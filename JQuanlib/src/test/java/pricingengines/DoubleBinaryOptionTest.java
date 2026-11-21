package pricingengines;

import jquant.AmericanExercise;
import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.EuropeanExercise;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.experimental.barrieroption.BinomialDoubleBarrierEngine;
import jquant.indexes.IndexManager;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.barrier.AnalyticDoubleBarrierBinaryEngine;
import jquant.pricingengines.barrier.FdHestonDoubleBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cash-or-nothing double barrier option pricing from QuantLib
 * {@code test-suite/doublebinaryoption.cpp}.
 *
 * <p>Complete port of the two official cases: Haug's textbook values
 * (analytic and binomial engines) and the PDE Heston comparison.
 */
public class DoubleBinaryOptionTest {

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

    /** Official {@code DoubleBinaryOptionData}. */
    private static class DoubleBinaryOptionData {
        final DoubleBarrier.Type barrierType;
        final double barrier_lo;
        final double barrier_hi;
        final double cash;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        DoubleBinaryOptionData(DoubleBarrier.Type barrierType,
                               double barrier_lo, double barrier_hi,
                               double cash, double s, double q, double r,
                               double t, double v, double result,
                               double tol) {
            this.barrierType = barrierType;
            this.barrier_lo = barrier_lo;
            this.barrier_hi = barrier_hi;
            this.cash = cash;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    @Test
    public void testHaugValues() {
        DoubleBinaryOptionData[] values = {
                /* The data below are from
                  "Option pricing formulas 2nd Ed.", E.G. Haug, McGraw-Hill
                  2007 pag. 181
                  Note: book uses cost of carry b, instead of dividend rate q
                */
                // barrierType, bar_lo, bar_hi, cash, spot, q, r, t, vol, value, tol
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 9.8716, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 8.9307, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 6.3272, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 1.9094, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 9.7961, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 7.2300, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 3.7100, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 0.4271, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 8.9054, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 3.6752, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 0.7960, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 0.0059, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 3.6323, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 0.0911, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 0.0002, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 0.0000, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 0.2402, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 1.4076, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 3.8160, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0075, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 0.9910, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 2.8098, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 4.6612, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.2656, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 2.7954, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 4.4024, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 4.9266, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 2.6285, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 4.7523, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 4.9096, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 4.9675, 1e-4),

                // following values calculated with haug's VBA code
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0042, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 0.9450, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 3.5486, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 7.9663, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0797, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 2.6458, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 6.1658, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 9.4486, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.9704, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 6.2006, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 9.0798, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 9.8699, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 6.2434, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 9.7847, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 9.8756, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 9.8758, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0041, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 0.7080, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 2.1581, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 4.2061, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.0723, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 1.6663, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 3.3930, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 4.8679, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 0.7080, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 3.4424, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 4.7496, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 5.0475, 1e-4),

                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10, 3.6524, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20, 5.1256, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30, 5.0763, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50, 5.0275, 1e-4),

                // degenerate cases
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 80.00, 0.02, 0.05, 0.25, 0.10, 0.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockOut, 95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 0.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 80.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KnockIn, 95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 80.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KIKO, 95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 0.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 80.00, 0.02, 0.05, 0.25, 0.10, 0.0000, 1e-4),
                new DoubleBinaryOptionData(DoubleBarrier.Type.KOKI, 95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4)
        };

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.04);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.01);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.25);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (DoubleBinaryOptionData value : values) {
            CashOrNothingPayoff payoff = new CashOrNothingPayoff(
                    Option.Type.Call, 0.0, value.cash);

            Date exDate = today.add(timeToDays(value.t));
            Exercise exercise;
            if (value.barrierType == DoubleBarrier.Type.KIKO
                    || value.barrierType == DoubleBarrier.Type.KOKI) {
                exercise = new AmericanExercise(today, exDate);
            } else {
                exercise = new EuropeanExercise(exDate);
            }

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            // checking with analytic engine
            DoubleBarrierOption opt = new DoubleBarrierOption(
                    value.barrierType, value.barrier_lo, value.barrier_hi,
                    0.0, payoff, exercise);
            opt.setPricingEngine(
                    new AnalyticDoubleBarrierBinaryEngine(stochProcess));

            double calculated = opt.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            if (error > value.tol) {
                reportFailure("value", value, exercise, today, expected,
                        calculated, error, value.tol);
            }

            int steps = 500;
            // checking with binomial engine (default CoxRossRubinstein /
            // DiscretizedDoubleBarrierOption)
            PricingEngine binomialEngine =
                    new BinomialDoubleBarrierEngine(stochProcess, steps);
            opt.setPricingEngine(binomialEngine);
            calculated = opt.NPV();
            expected = value.result;
            error = Math.abs(calculated - expected);
            double tol = 0.22;
            if (error > tol) {
                reportFailure("Binomial value", value, exercise, today,
                        expected, calculated, error, tol);
            }
        }
    }

    @Test
    public void testPdeDoubleBarrierWithAnalytical() {
        DayCounter dc = new Actual360();
        Date todaysDate = new Date(30, Month.JANUARY, 2023);
        Date maturityDate = todaysDate.add(new Period(1, TimeUnit.YEARS));
        Settings.instance.evaluationDate().equal(todaysDate);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);
        double r = 0.075;
        double q = 0.03;
        double vol = 0.4;

        double kappa = 1.0;
        double theta = vol * vol;
        double rho = 0.0;
        double sigma = 1e-4;
        double v0 = theta;

        YieldTermStructure rTS = flatRate(todaysDate, r, dc);
        YieldTermStructure qTS = flatRate(todaysDate, q, dc);

        HestonModel hestonModel = new HestonModel(
                new HestonProcess(
                        new Handle<>(rTS, true),
                        new Handle<>(qTS, true),
                        spot, v0, kappa, theta, sigma, rho));

        BlackScholesMertonProcess bsProcess =
                new BlackScholesMertonProcess(
                        new Handle<>(spot.getValue(), true),
                        new Handle<>(qTS, true),
                        new Handle<>(rTS, true),
                        new Handle<>(flatVol(todaysDate, vol, dc), true));

        PricingEngine analyticEngine =
                new AnalyticDoubleBarrierBinaryEngine(bsProcess);

        PricingEngine fdEngine = new FdHestonDoubleBarrierEngine(
                hestonModel, 201, 101, 3, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);

        Exercise europeanExercise = new EuropeanExercise(maturityDate);

        double tol = 5e-3;
        for (int i = 5; i < 18; i += 2) {
            double dist = 10.0 + 5.0 * i;

            double barrier_lo = Math.max(spot.getValue().value() - dist, 1e-2);
            double barrier_hi = spot.getValue().value() + dist;
            DoubleBarrierOption doubleBarrier = new DoubleBarrierOption(
                    DoubleBarrier.Type.KnockOut, barrier_lo, barrier_hi, 0.0,
                    new CashOrNothingPayoff(Option.Type.Call, 0.0, 1.0),
                    europeanExercise);

            doubleBarrier.setPricingEngine(analyticEngine);
            double bsNPV = doubleBarrier.NPV();

            doubleBarrier.setPricingEngine(fdEngine);
            double slvNPV = doubleBarrier.NPV();

            double diff = slvNPV - bsNPV;
            if (Math.abs(diff) > tol) {
                fail("Failed to reproduce price difference for a "
                        + "Double-No-Touch option between Black-Scholes and "
                        + "Heston Stochastic Local Volatility model"
                        + "\n Barrier Low        : " + barrier_lo
                        + "\n Barrier High       : " + barrier_hi
                        + "\n Black-Scholes Price: " + bsNPV
                        + "\n Heston SLV Price   : " + slvNPV
                        + "\n diff               : " + diff
                        + "\n tolerance          : " + tol);
            }
        }
    }

    private static void reportFailure(String name,
                                      DoubleBinaryOptionData value,
                                      Exercise exercise, Date today,
                                      double expected, double calculated,
                                      double error, double tolerance) {
        fail(value.barrierType + " barrier type:"
                + "\n    barrier_lo:       " + value.barrier_lo
                + "\n    barrier_hi:       " + value.barrier_hi
                + "\n    spot value:       " + value.s
                + "\n    dividend yield:   " + value.q
                + "\n    risk-free rate:   " + value.r
                + "\n    reference date:   " + today
                + "\n    maturity:         " + exercise.lastDate()
                + "\n    volatility:       " + value.v
                + "\n    expected " + name + ": " + expected
                + "\n    calculated " + name + ": " + calculated
                + "\n    error:            " + error
                + "\n    tolerance:        " + tolerance);
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
