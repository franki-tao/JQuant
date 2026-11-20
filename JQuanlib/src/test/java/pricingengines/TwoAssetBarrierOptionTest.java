package pricingengines;

import jquant.Barrier;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.TwoAssetBarrierOption;
import jquant.indexes.IndexManager;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.barrier.AnalyticTwoAssetBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
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

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Two-asset barrier option pricing from QuantLib
 * {@code test-suite/twoassetbarrieroption.cpp}.
 *
 * <p>Complete port of the two official cases: Haug's literature values and
 * in-out parity with dividends.
 */
public class TwoAssetBarrierOptionTest {

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

    /** Official {@code OptionData}. */
    private static class OptionData {
        final Barrier.Type barrierType;
        final Option.Type type;
        final double barrier;
        final double strike;
        final double s1;
        final double q1;
        final double v1;
        final double s2;
        final double q2;
        final double v2;
        final double correlation;
        final double r;
        final double result;

        OptionData(Barrier.Type barrierType, Option.Type type,
                   double barrier, double strike, double s1, double q1,
                   double v1, double s2, double q2, double v2,
                   double correlation, double r, double result) {
            this.barrierType = barrierType;
            this.type = type;
            this.barrier = barrier;
            this.strike = strike;
            this.s1 = s1;
            this.q1 = q1;
            this.v1 = v1;
            this.s2 = s2;
            this.q2 = q2;
            this.v2 = v2;
            this.correlation = correlation;
            this.r = r;
            this.result = result;
        }
    }

    @Test
    public void testHaugValues() {
        OptionData[] values = {
                // The data below are from "Option pricing formulas",
                // E.G. Haug, McGraw-Hill 1998.
                new OptionData(Barrier.Type.DownOut, Option.Type.Call,
                        95, 90, 100.0, 0.0, 0.2, 100.0, 0.0, 0.2,
                        0.5, 0.08, 6.6592),
                new OptionData(Barrier.Type.UpOut, Option.Type.Call,
                        105, 90, 100.0, 0.0, 0.2, 100.0, 0.0, 0.2,
                        -0.5, 0.08, 4.6670),
                new OptionData(Barrier.Type.DownOut, Option.Type.Put,
                        95, 90, 100.0, 0.0, 0.2, 100.0, 0.0, 0.2,
                        -0.5, 0.08, 0.6184),
                new OptionData(Barrier.Type.UpOut, Option.Type.Put,
                        105, 100, 100.0, 0.0, 0.2, 100.0, 0.0, 0.2,
                        0.0, 0.08, 0.8246)
        };

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();
        Date maturity = today.add(180);
        Exercise exercise = new EuropeanExercise(maturity);

        SimpleQuote r = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, r, dc);

        SimpleQuote s1 = new SimpleQuote(0.0);
        SimpleQuote q1 = new SimpleQuote(0.0);
        YieldTermStructure qTS1 = flatRate(today, q1, dc);
        SimpleQuote vol1 = new SimpleQuote(0.0);
        BlackVolTermStructure volTS1 = flatVol(today, vol1, dc);

        BlackScholesMertonProcess process1 = new BlackScholesMertonProcess(
                new Handle<>(s1, true),
                new Handle<>(qTS1, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS1, true));

        SimpleQuote s2 = new SimpleQuote(0.0);
        SimpleQuote q2 = new SimpleQuote(0.0);
        YieldTermStructure qTS2 = flatRate(today, q2, dc);
        SimpleQuote vol2 = new SimpleQuote(0.0);
        BlackVolTermStructure volTS2 = flatVol(today, vol2, dc);

        BlackScholesMertonProcess process2 = new BlackScholesMertonProcess(
                new Handle<>(s2, true),
                new Handle<>(qTS2, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS2, true));

        SimpleQuote rho = new SimpleQuote(0.0);

        AnalyticTwoAssetBarrierEngine engine =
                new AnalyticTwoAssetBarrierEngine(process1, process2,
                        new Handle<>(rho, true));

        for (OptionData value : values) {
            s1.setValue(value.s1);
            q1.setValue(value.q1);
            vol1.setValue(value.v1);

            s2.setValue(value.s2);
            q2.setValue(value.q2);
            vol2.setValue(value.v2);

            rho.setValue(value.correlation);

            r.setValue(value.r);

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(value.type, value.strike);

            TwoAssetBarrierOption barrierOption = new TwoAssetBarrierOption(
                    value.barrierType, value.barrier, payoff, exercise);
            barrierOption.setPricingEngine(engine);

            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            double tolerance = 4.0e-3;
            if (error > tolerance) {
                fail("failed to reproduce expected price"
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    tolerance:  " + tolerance
                        + "\n    error:      " + error);
            }
        }
    }

    @Test
    public void testInOutParityWithDividends() {
        DayCounter dc = new Actual360();
        Date today = new Date(15, Month.JANUARY, 2026);
        Settings.instance.evaluationDate().equal(today);
        Exercise exercise = new EuropeanExercise(today.add(365));

        SimpleQuote s1 = new SimpleQuote(100.0);
        SimpleQuote q1 = new SimpleQuote(0.0);
        SimpleQuote s2 = new SimpleQuote(100.0);
        SimpleQuote rho = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, 0.05, dc);

        BlackScholesMertonProcess process1 = new BlackScholesMertonProcess(
                new Handle<>(s1, true),
                new Handle<>(flatRate(today, q1, dc), true),
                new Handle<>(rTS, true),
                new Handle<>(flatVol(today, 0.2, dc), true));
        BlackScholesMertonProcess process2 = new BlackScholesMertonProcess(
                new Handle<>(s2, true),
                new Handle<>(flatRate(today, 0.02, dc), true),
                new Handle<>(rTS, true),
                new Handle<>(flatVol(today, 0.3, dc), true));

        AnalyticTwoAssetBarrierEngine engine =
                new AnalyticTwoAssetBarrierEngine(process1, process2,
                        new Handle<>(rho, true));
        AnalyticEuropeanEngine europeanEngine =
                new AnalyticEuropeanEngine(process1);

        Barrier.Type[] in = {Barrier.Type.DownIn, Barrier.Type.UpIn};
        Barrier.Type[] out = {Barrier.Type.DownOut, Barrier.Type.UpOut};
        double[] barriers = {90.0, 110.0};

        for (int b = 0; b < barriers.length; ++b) {
            for (Option.Type type : new Option.Type[]{
                    Option.Type.Call, Option.Type.Put}) {
                for (double strike : new double[]{90.0, 110.0}) {
                    for (double q : new double[]{0.0, 0.04}) {
                        for (double correlation : new double[]{-0.5, 0.5}) {
                            q1.setValue(q);
                            rho.setValue(correlation);
                            PlainVanillaPayoff payoff =
                                    new PlainVanillaPayoff(type, strike);

                            TwoAssetBarrierOption knockIn =
                                    new TwoAssetBarrierOption(
                                            in[b], barriers[b], payoff,
                                            exercise);
                            knockIn.setPricingEngine(engine);
                            TwoAssetBarrierOption knockOut =
                                    new TwoAssetBarrierOption(
                                            out[b], barriers[b], payoff,
                                            exercise);
                            knockOut.setPricingEngine(engine);
                            EuropeanOption vanilla =
                                    new EuropeanOption(payoff, exercise);
                            vanilla.setPricingEngine(europeanEngine);

                            double calculated = knockIn.NPV() + knockOut.NPV();
                            double expected = vanilla.NPV();
                            double tolerance = 1.0e-10;
                            if (Math.abs(calculated - expected) > tolerance) {
                                fail("in-out parity violated"
                                        + "\n    barrier type: "
                                        + in[b] + "/" + out[b]
                                        + "\n    option type:  " + type
                                        + "\n    strike:       " + strike
                                        + "\n    dividend:     " + q
                                        + "\n    correlation:  " + correlation
                                        + "\n    knock-in:     " + knockIn.NPV()
                                        + "\n    knock-out:    " + knockOut.NPV()
                                        + "\n    vanilla:      " + expected);
                            }
                        }
                    }
                }
            }
        }
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
