package pricingengines;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.SoftBarrierOption;
import jquant.indexes.IndexManager;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.barrier.AnalyticBarrierEngine;
import jquant.pricingengines.barrier.AnalyticSoftBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Soft barrier option pricing from QuantLib
 * {@code test-suite/softbarrieroption.cpp}.
 *
 * <p>Complete port of the two official cases: Haug's textbook values and
 * comparison against averaged single-barrier options.
 */
public class SoftBarrierOptionTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        try {
            savedSettings_.restore();
        } finally {
            IndexManager.Instance.clearHistories();
        }
    }

    /** Official {@code SoftBarrierOptionData}. */
    private static class SoftBarrierOptionData {
        final Barrier.Type barrierType;
        final Option.Type type;
        final double s;
        final double strike;
        final double U;
        final double L;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        SoftBarrierOptionData(Barrier.Type barrierType, Option.Type type,
                              double s, double strike, double U, double L,
                              double q, double r, double t, double v,
                              double result, double tol) {
            this.barrierType = barrierType;
            this.type = type;
            this.s = s;
            this.strike = strike;
            this.U = U;
            this.L = L;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    @Test
    public void testSoftBarrierHaug() {
        SoftBarrierOptionData[] values = {
                /* The data below are from "The complete guide to option
                   pricing formulas 2nd Ed", E.G. Haug p.166
                   Note: In the book, b represents the cost of carry (r-q)
                */

                // barrierType, optionType, S, X, U, L, q, r, T, v, result, tol
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.1, 3.8075, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.1, 4.0175, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.1, 4.0529, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.1, 4.0648, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.1, 4.0708, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.1, 4.0744, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.1, 4.0768, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.1, 4.0785, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.1, 4.0798, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.1, 4.0808, 1e-4),

                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.2, 4.5263, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.2, 5.5615, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.2, 6.0394, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.2, 6.2594, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.2, 6.3740, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.2, 6.4429, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.2, 6.4889, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.2, 6.5217, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.2, 6.5463, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.2, 6.5654, 1e-4),

                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.3, 4.7297, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.3, 6.2595, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.3, 7.2496, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.3, 7.8567, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.3, 8.2253, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.3, 8.4578, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.3, 8.6142, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.3, 8.7260, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.3, 8.8099, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.3, 8.8751, 1e-4),

                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.1, 5.4187, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.1, 6.0758, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.1, 6.2641, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.1, 6.3336, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.1, 6.3685, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.1, 6.3894, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.1, 6.4034, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.1, 6.4133, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.1, 6.4208, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.1, 6.4266, 1e-4),

                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.2, 5.3614, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.2, 6.9776, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.2, 7.9662, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.2, 8.5432, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.2, 8.8822, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.2, 9.0931, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.2, 9.2343, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.2, 9.3353, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.2, 9.4110, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.2, 9.4698, 1e-4),

                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.3, 5.2300, 1e-4),
                // { Barrier::DownOut, Option::Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.3, 7.2046, 1e-4 },
                // this test case has an error of c0.0003 -> not sure why, but most likely due to
                // tight barriers and high volatility leading to small numerical differences
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.3, 8.7092, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.3, 9.8118, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.3, 10.5964, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.3, 11.1476, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.3, 11.5384, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.3, 11.8228, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.3, 12.0369, 1e-4),
                new SoftBarrierOptionData(Barrier.Type.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.3, 12.2036, 1e-4)
        };

        DayCounter dc = new Actual360();
        Date today = new Date(8, Month.AUGUST, 2025);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (SoftBarrierOptionData value : values) {
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            Date exDate = today.add(timeToDays(value.t));
            Exercise exercise = new EuropeanExercise(exDate);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(value.type, value.strike);

            BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            SoftBarrierOption option = new SoftBarrierOption(
                    value.barrierType, value.L, value.U, payoff, exercise);
            option.setPricingEngine(new AnalyticSoftBarrierEngine(process));

            double calculated = option.NPV();
            double error = Math.abs(calculated - value.result);
            double tolerance = value.tol;
            if (error > tolerance) {
                fail("failed to reproduce expected soft barrier price"
                        + "\n    barrier type: " + value.barrierType
                        + "\n    option type:  " + value.type
                        + "\n    spot:         " + value.s
                        + "\n    strike:       " + value.strike
                        + "\n    upper:        " + value.U
                        + "\n    lower:        " + value.L
                        + "\n    dividend:     " + value.q
                        + "\n    risk-free:    " + value.r
                        + "\n    maturity:     " + exDate
                        + "\n    volatility:   " + value.v
                        + "\n    expected:     " + value.result
                        + "\n    calculated:   " + calculated
                        + "\n    tolerance:    " + tolerance
                        + "\n    error:        " + error);
            }
        }
    }

    @Test
    public void testSoftBarrierDirection() {
        DayCounter dc = new Actual360();
        Date today = new Date(8, Month.AUGUST, 2025);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.04, dc);
        YieldTermStructure rTS = flatRate(today, 0.08, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.25, dc);

        BlackScholesMertonProcess process =
                new BlackScholesMertonProcess(
                        new Handle<>(spot, true),
                        new Handle<>(qTS, true),
                        new Handle<>(rTS, true),
                        new Handle<>(volTS, true));

        Exercise exercise = new EuropeanExercise(today.add(360));

        Barrier.Type[] barrierTypes = {Barrier.Type.DownIn,
                Barrier.Type.DownOut, Barrier.Type.UpIn, Barrier.Type.UpOut};
        double[] lowers = {80.0, 80.0, 110.0, 110.0};
        double[] uppers = {90.0, 90.0, 120.0, 120.0};

        double tolerance = 1e-6;

        for (int c = 0; c < barrierTypes.length; ++c) {
            Barrier.Type barrierType = barrierTypes[c];
            boolean isDown = barrierType == Barrier.Type.DownIn
                    || barrierType == Barrier.Type.DownOut;
            for (Option.Type type : new Option.Type[]{
                    Option.Type.Call, Option.Type.Put}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, 100.0);

                SoftBarrierOption option = new SoftBarrierOption(
                        barrierType, lowers[c], uppers[c], payoff, exercise);
                option.setPricingEngine(
                        new AnalyticSoftBarrierEngine(process));

                if ((type == Option.Type.Call) == isDown) {
                    double calculated = option.NPV();
                    double expected = averaged(barrierType, payoff,
                            lowers[c], uppers[c], process, exercise);
                    double error = Math.abs(calculated - expected);
                    if (Double.isNaN(error) || error > tolerance) {
                        fail("failed to reproduce the averaged value of a "
                                + barrierType + " " + type + " option"
                                + "\n    calculated: " + calculated
                                + "\n    expected:   " + expected
                                + "\n    error:      " + error
                                + "\n    tolerance:  " + tolerance);
                    }
                } else {
                    // not covered by the formula
                    IllegalArgumentException exception = assertThrows(
                            IllegalArgumentException.class,
                            option::NPV);
                    assertTrue(exception.getMessage()
                                    .contains("soft-barrier formula not available"),
                            "unexpected error message: "
                                    + exception.getMessage());
                }
            }
        }
    }

    /**
     * A soft barrier option is the average of the single-barrier options
     * with barriers in [L, U] (Simpson's rule).
     */
    private static double averaged(Barrier.Type barrierType,
                                   StrikedTypePayoff payoff,
                                   double L, double U,
                                   BlackScholesMertonProcess process,
                                   Exercise exercise) {
        int n = 128;
        double h = (U - L) / n;
        double sum = 0.0;
        for (int i = 0; i <= n; ++i) {
            BarrierOption option = new BarrierOption(
                    barrierType, L + i * h, 0.0, payoff, exercise);
            option.setPricingEngine(new AnalyticBarrierEngine(process));
            double weight = (i == 0 || i == n) ? 1.0
                    : (i % 2 == 1 ? 4.0 : 2.0);
            sum += weight * option.NPV();
        }
        return sum * h / 3.0 / (U - L);
    }

    private static int timeToDays(double t) {
        return (int) Math.round(t * 360.0);
    }

    private static YieldTermStructure flatRate(Date today, double rate,
                                               DayCounter dc) {
        return flatRate(today, new SimpleQuote(rate), dc);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static BlackVolTermStructure flatVol(Date today, double vol,
                                                 DayCounter dc) {
        return flatVol(today, new SimpleQuote(vol), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }
}
