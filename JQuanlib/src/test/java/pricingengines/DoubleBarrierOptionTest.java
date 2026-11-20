package pricingengines;

import jquant.AmericanExercise;
import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.experimental.barrieroption.BinomialDoubleBarrierEngine;
import jquant.experimental.barrieroption.MakeMCDoubleBarrierEngine;
import jquant.experimental.barrieroption.SuoWangDoubleBarrierEngine;
import jquant.experimental.barrieroption.VannaVolgaDoubleBarrierEngine;
import jquant.indexes.IndexManager;
import jquant.math.randomnumbers.RngType;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.barrier.AnalyticDoubleBarrierBinaryEngine;
import jquant.pricingengines.barrier.AnalyticDoubleBarrierEngine;
import jquant.pricingengines.barrier.FdHestonDoubleBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BinomialVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.DeltaVolQuote;
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
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Double-barrier option pricing from QuantLib
 * {@code test-suite/doublebarrieroption.cpp}.
 *
 * <p>Complete port of the six official cases: exercise at maturity, Haug's
 * European values (Ikeda/Kunitomo, SuoWang, plain and Derman-Kani binomials,
 * FdHeston), strikes outside the corridor, low volatility, Vanna-Volga FX
 * values, and Monte Carlo against the analytic engine.
 */
public class DoubleBarrierOptionTest {

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

    /** Official {@code NewBarrierOptionData}. */
    private static class NewBarrierOptionData {
        final DoubleBarrier.Type barrierType;
        final double barrierlo;
        final double barrierhi;
        final Option.Type type;
        final Exercise.Type exType;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        NewBarrierOptionData(DoubleBarrier.Type barrierType, double barrierlo,
                             double barrierhi, Option.Type type,
                             Exercise.Type exType, double strike, double s,
                             double q, double r, double t, double v,
                             double result, double tol) {
            this.barrierType = barrierType;
            this.barrierlo = barrierlo;
            this.barrierhi = barrierhi;
            this.type = type;
            this.exType = exType;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    /** Official {@code DoubleBarrierFxOptionData}. */
    private static class DoubleBarrierFxOptionData {
        final DoubleBarrier.Type barrierType;
        final double barrier1;
        final double barrier2;
        final double rebate;
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double vol25Put;
        final double volAtm;
        final double vol25Call;
        final double v;
        final double result;
        final double tol;

        DoubleBarrierFxOptionData(DoubleBarrier.Type barrierType,
                                  double barrier1, double barrier2,
                                  double rebate, Option.Type type,
                                  double strike, double s, double q, double r,
                                  double t, double vol25Put, double volAtm,
                                  double vol25Call, double v, double result,
                                  double tol) {
            this.barrierType = barrierType;
            this.barrier1 = barrier1;
            this.barrier2 = barrier2;
            this.rebate = rebate;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.vol25Put = vol25Put;
            this.volAtm = volAtm;
            this.vol25Call = vol25Call;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    @Test
    public void testBinomialExerciseAtMaturity() {
        Date today = new Date(21, Month.SEPTEMBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();
        Date maturity = today.add(91);
        SimpleQuote spot = new SimpleQuote(100.0);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, 0.0, dc), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatVol(today, 0.15, dc), true));
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 85.0);
        EuropeanExercise europeanExercise = new EuropeanExercise(maturity);
        AmericanExercise americanExercise =
                new AmericanExercise(today, maturity);
        int timeSteps = 51;
        BinomialDoubleBarrierEngine engine =
                new BinomialDoubleBarrierEngine(process, timeSteps);
        EuropeanOption vanilla = new EuropeanOption(payoff, europeanExercise);
        vanilla.setPricingEngine(new BinomialVanillaEngine(process, timeSteps,
                BinomialVanillaEngine.TreeType.COX_ROSS_RUBINSTEIN));
        double expected = vanilla.NPV();
        double tolerance = 1.0e-10;

        // All stock nodes lie between 58 and 171, so neither barrier can
        // knock out.
        DoubleBarrierOption european = new DoubleBarrierOption(
                DoubleBarrier.Type.KnockOut, 1.0, 1000.0, 0.0,
                payoff, europeanExercise);
        DoubleBarrierOption american = new DoubleBarrierOption(
                DoubleBarrier.Type.KnockOut, 1.0, 1000.0, 0.0,
                payoff, americanExercise);
        european.setPricingEngine(engine);
        american.setPricingEngine(engine);
        double europeanValue = european.NPV();
        double americanValue = american.NPV();
        if (Math.abs(europeanValue - expected) > tolerance) {
            fail("European double-barrier value " + europeanValue
                    + " differs from vanilla value " + expected);
        }
        if (Math.abs(americanValue - expected) > tolerance) {
            fail("Non-dividend-paying American double-barrier value "
                    + americanValue + " differs from vanilla value " + expected);
        }
    }

    private static final NewBarrierOptionData[] HAUG_VALUES = {
            // The data below are from "The complete guide to option pricing
            // formulas 2nd Ed", E.G. Haug, McGraw-Hill, p.156 and following.
            // The book uses b instead of q (q = r - b).
            // barrierType, barr.lo, barr.hi, type, exercise, strike, spot,
            // q, r, t, v, result, tol
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 4.3515, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 6.1644, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 7.0373, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 6.9853, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 7.9336, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 6.5088, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 4.3505, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 5.8500, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 5.7726, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 6.8082, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 6.3383, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 4.3841, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 4.3139, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 4.8293, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 3.7765, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 5.9697, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 4.0004, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 2.2563, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 3.7516, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 2.6387, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 1.4903, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 3.5805, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 1.5098, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 0.5635, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 1.2055, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 0.3098, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 0.0477, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 0.5537, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 0.0441, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 0.0011, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 1.8825, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 3.7855, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 5.7191, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 2.1374, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 4.7033, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 50.0, 150.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 7.1683, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 1.8825, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 3.7845, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 5.6060, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 2.1374, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 4.6236, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 60.0, 140.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 6.1062, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 1.8825, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 3.7014, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 4.6472, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 2.1325, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 3.8944, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 70.0, 130.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 3.5868, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 1.8600, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 2.6866, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 2.0719, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 1.8883, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 1.7851, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 80.0, 120.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 0.8244, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 0.9473, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 0.3449, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 0.0578, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 0.4555, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 0.0491, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockOut, 90.0, 110.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 0.0013, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 0.0000, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 0.0900, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 1.1537, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 0.0292, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 1.6487, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 50.0, 150.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 5.7321, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 0.0010, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 0.4045, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 2.4184, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 0.2062, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 3.2439, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 60.0, 140.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 7.8569, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 0.0376, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 1.4252, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 4.4145, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 1.0447, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 5.5818, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 70.0, 130.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 9.9846, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 0.5999, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 3.6158, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 6.7007, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 3.4340, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 8.0724, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 80.0, 120.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 11.6774, 1.0e-4),

            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.15, 3.1460, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.25, 5.9447, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.25, 0.35, 8.1432, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.15, 6.4608, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.25, 9.5382, 1.0e-4),
            new NewBarrierOptionData(DoubleBarrier.Type.KnockIn, 90.0, 110.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.0, 0.1, 0.50, 0.35, 12.2398, 1.0e-4)
    };

    @Test
    public void testEuropeanHaugValues() {
        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (NewBarrierOptionData value : HAUG_VALUES) {
            Date exDate = today.add(timeToDays(value.t));
            Exercise exercise = new EuropeanExercise(exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(value.type, value.strike);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            DoubleBarrierOption opt = new DoubleBarrierOption(
                    value.barrierType, value.barrierlo, value.barrierhi,
                    0.0, payoff, exercise);

            // Ikeda/Kunitomo engine
            opt.setPricingEngine(new AnalyticDoubleBarrierEngine(stochProcess));
            double calculated = opt.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            if (error > value.tol) {
                reportFailure("Ikeda/Kunitomo value", value, payoff, exercise,
                        today, expected, calculated, error, value.tol);
            }

            // Wulin Suo/Yong Wang engine
            opt.setPricingEngine(new SuoWangDoubleBarrierEngine(stochProcess));
            calculated = opt.NPV();
            expected = value.result;
            error = Math.abs(calculated - expected);
            if (error > value.tol) {
                reportFailure("Wulin/Yong value", value, payoff, exercise,
                        today, expected, calculated, error, value.tol);
            }

            opt.setPricingEngine(new BinomialDoubleBarrierEngine(
                    stochProcess, 300));
            calculated = opt.NPV();
            expected = value.result;
            error = Math.abs(calculated - expected);
            double tol = 0.28;
            if (error > tol) {
                reportFailure("Binomial value", value, payoff, exercise,
                        today, expected, calculated, error, tol);
            }

            opt.setPricingEngine(new BinomialDoubleBarrierEngine(
                    stochProcess, 300,
                    BinomialDoubleBarrierEngine.Discretization.DERMAN_KANI));
            calculated = opt.NPV();
            expected = value.result;
            error = Math.abs(calculated - expected);
            tol = 0.033; // error one order of magnitude lower than plain binomial
            if (error > tol) {
                reportFailure("Binomial (Derman) value", value, payoff, exercise,
                        today, expected, calculated, error, tol);
            }

            if (value.barrierType == DoubleBarrier.Type.KnockOut) {
                PricingEngine hestonEngine = new FdHestonDoubleBarrierEngine(
                        new HestonModel(new HestonProcess(
                                new Handle<>(rTS, true),
                                new Handle<>(qTS, true),
                                new Handle<>(spot, true),
                                value.v * value.v, 1.0,
                                value.v * value.v, 0.001, 0.0)),
                        251, 76, 3);

                opt.setPricingEngine(hestonEngine);
                calculated = opt.NPV();
                expected = value.result;
                error = Math.abs(calculated - expected);

                tol = 0.025;
                if (error > tol) {
                    reportFailure("Heston value", value, payoff, exercise,
                            today, expected, calculated, error, tol);
                }
            }
        }
    }

    @Test
    public void testStrikesOutsideCorridor() {
        Date today = new Date(1, Month.JANUARY, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(flatRate(today, 0.04, dc), true),
                new Handle<>(flatRate(today, 0.08, dc), true),
                new Handle<>(flatVol(today, 0.25, dc), true));
        PricingEngine engine = new AnalyticDoubleBarrierEngine(process);
        final double lower = 80.0;
        final double upper = 120.0;
        final double tolerance = 1.0e-10;

        Option.Type[] types = {
                Option.Type.Call, Option.Type.Call,
                Option.Type.Put, Option.Type.Put,
                Option.Type.Call, Option.Type.Put,
                Option.Type.Call, Option.Type.Put
        };
        double[] strikes = {70.0, 72.0, 132.0, 130.0, 132.0, 70.0, 120.0, 80.0};
        int[] days = {365, 182, 182, 365, 365, 365, 365, 365};

        for (int i = 0; i < types.length; ++i) {
            Option.Type cType = types[i];
            double cStrike = strikes[i];
            int cDays = days[i];
            Exercise exercise = new EuropeanExercise(today.add(cDays));
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(cType, cStrike);
            double expected = 0.0;
            if ((cType == Option.Type.Call && cStrike < lower)
                    || (cType == Option.Type.Put && cStrike > upper)) {
                double boundary = cType == Option.Type.Call ? lower : upper;
                DoubleBarrierOption atBoundary = new DoubleBarrierOption(
                        DoubleBarrier.Type.KnockOut, lower, upper, 0.0,
                        new PlainVanillaPayoff(cType, boundary), exercise);
                atBoundary.setPricingEngine(engine);
                DoubleBarrierOption noTouch = new DoubleBarrierOption(
                        DoubleBarrier.Type.KnockOut, lower, upper, 0.0,
                        new CashOrNothingPayoff(Option.Type.Call, 0.0, 1.0),
                        exercise);
                noTouch.setPricingEngine(
                        new AnalyticDoubleBarrierBinaryEngine(process));
                // On survival, C(K) = C(L) + (L-K) and P(K) = P(U) + (K-U).
                expected = atBoundary.NPV()
                        + Math.abs(cStrike - boundary) * noTouch.NPV();
            }

            DoubleBarrierOption knockOut = new DoubleBarrierOption(
                    DoubleBarrier.Type.KnockOut, lower, upper, 0.0,
                    payoff, exercise);
            knockOut.setPricingEngine(engine);
            double calculated = knockOut.NPV();
            if (!(Math.abs(calculated - expected) < tolerance)) {
                fail(cType + " strike " + cStrike + ", " + cDays
                        + " days: expected " + expected + ", calculated "
                        + calculated);
            }

            EuropeanOption vanilla = new EuropeanOption(payoff, exercise);
            vanilla.setPricingEngine(new AnalyticEuropeanEngine(process));
            DoubleBarrierOption knockIn = new DoubleBarrierOption(
                    DoubleBarrier.Type.KnockIn, lower, upper, 0.0,
                    payoff, exercise);
            knockIn.setPricingEngine(engine);
            if (!(Math.abs(knockIn.NPV() - (vanilla.NPV() - expected))
                    < tolerance)) {
                fail("Knock-in parity failed for " + cType + " strike "
                        + cStrike);
            }
        }
    }

    @Test
    public void testLowVolatility() {
        Date today = new Date(1, Month.JANUARY, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();
        Exercise exercise = new EuropeanExercise(today.add(365));
        for (Option.Type type : new Option.Type[]{
                Option.Type.Call, Option.Type.Put}) {
            double rate = type == Option.Type.Call ? 0.05 : -0.05;
            BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    new Handle<>(new SimpleQuote(100.0), true),
                    new Handle<>(flatRate(today, 0.0, dc), true),
                    new Handle<>(flatRate(today, rate, dc), true),
                    new Handle<>(flatVol(today, 0.01, dc), true));
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, 100.0);
            DoubleBarrierOption option = new DoubleBarrierOption(
                    DoubleBarrier.Type.KnockOut, 80.0, 120.0, 0.0,
                    payoff, exercise);
            option.setPricingEngine(
                    new AnalyticDoubleBarrierEngine(process, 1));
            EuropeanOption vanilla = new EuropeanOption(payoff, exercise);
            vanilla.setPricingEngine(new AnalyticEuropeanEngine(process));
            // Barrier-hit probabilities are negligible at this volatility,
            // so the vanilla prices (4.87705760207 and 5.12710969242) are
            // independent references.
            if (!(Math.abs(option.NPV() - vanilla.NPV()) < 1.0e-10)) {
                fail(type + ": expected " + vanilla.NPV()
                        + ", calculated " + option.NPV());
            }
        }
    }

    private static final DoubleBarrierFxOptionData[] VANNA_VOLGA_VALUES = {
            // barrierType, barr.1, barr.2, rebate, type, strike, spot,
            // q, r, t, vol25Put, volAtm, vol25Call, vol, result, tol
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Call, 1.13321, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.11638, 0.14413, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Call, 1.22687, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.10088, 0.07456, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Call, 1.31179, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08925, 0.02710, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Call, 1.38843, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08463, 0.00569, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Call, 1.46047, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08412, 0.00013, 1.0e-4),

            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Put, 1.13321, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.11638, 0.00017, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Put, 1.22687, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.10088, 0.00353, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Put, 1.31179, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08925, 0.02221, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Put, 1.38843, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08463, 0.06049, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.1, 1.5, 0.0, Option.Type.Put, 1.46047, 1.30265,
                    0.0003541, 0.0033871, 1.0, 0.10087, 0.08925, 0.08463,
                    0.08412, 0.11103, 1.0e-4),

            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Call, 1.06145, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.12511, 0.19981, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Call, 1.19545, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.10890, 0.10389, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Call, 1.32238, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09444, 0.03555, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Call, 1.44298, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09197, 0.00634, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Call, 1.56345, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09261, 0.00000, 1.0e-4),

            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Put, 1.06145, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.12511, 0.00000, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Put, 1.19545, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.10890, 0.00436, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Put, 1.32238, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09444, 0.03173, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Put, 1.44298, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09197, 0.09346, 1.0e-4),
            new DoubleBarrierFxOptionData(DoubleBarrier.Type.KnockOut,
                    1.0, 1.6, 0.0, Option.Type.Put, 1.56345, 1.30265,
                    0.0009418, 0.0039788, 2.0, 0.10891, 0.09525, 0.09197,
                    0.09261, 0.17704, 1.0e-4)
    };

    @Test
    public void testVannaVolgaDoubleBarrierValues() {
        DayCounter dc = new Actual360();
        Date today = new Date(5, Month.MARCH, 2013);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol25Put = new SimpleQuote(0.0);
        SimpleQuote volAtm = new SimpleQuote(0.0);
        SimpleQuote vol25Call = new SimpleQuote(0.0);

        for (DoubleBarrierFxOptionData value : VANNA_VOLGA_VALUES) {
            for (int j = 0; j <= 1; j++) {
                DoubleBarrier.Type barrierType = j == 0
                        ? DoubleBarrier.Type.KnockIn
                        : DoubleBarrier.Type.KnockOut;

                spot.setValue(value.s);
                qRate.setValue(value.q);
                rRate.setValue(value.r);
                vol25Put.setValue(value.vol25Put);
                volAtm.setValue(value.volAtm);
                vol25Call.setValue(value.vol25Call);

                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(value.type, value.strike);

                Date exDate = today.add(timeToDays(value.t, 365));
                Exercise exercise = new EuropeanExercise(exDate);

                Handle<DeltaVolQuote> volAtmQuote = new Handle<>(
                        new DeltaVolQuote(new Handle<>(volAtm, true),
                                DeltaVolQuote.DeltaType.Fwd, value.t,
                                DeltaVolQuote.AtmType.AtmDeltaNeutral),
                        true);

                // always delta neutral atm
                Handle<DeltaVolQuote> vol25PutQuote = new Handle<>(
                        new DeltaVolQuote(-0.25, new Handle<>(vol25Put, true),
                                value.t, DeltaVolQuote.DeltaType.Fwd),
                        true);

                Handle<DeltaVolQuote> vol25CallQuote = new Handle<>(
                        new DeltaVolQuote(0.25, new Handle<>(vol25Call, true),
                                value.t, DeltaVolQuote.DeltaType.Fwd),
                        true);

                DoubleBarrierOption doubleBarrierOption = new DoubleBarrierOption(
                        barrierType, value.barrier1, value.barrier2,
                        value.rebate, payoff, exercise);

                double bsVanillaPrice = BlackFormula.blackFormula(value.type,
                        value.strike,
                        spot.value() * qTS.discount(value.t)
                                / rTS.discount(value.t),
                        value.v * Math.sqrt(value.t),
                        rTS.discount(value.t), 0.0);
                PricingEngine vannaVolgaEngine =
                        new VannaVolgaDoubleBarrierEngine(
                                volAtmQuote,
                                vol25PutQuote,
                                vol25CallQuote,
                                new Handle<>(spot, true),
                                new Handle<>(rTS, true),
                                new Handle<>(qTS, true),
                                true,
                                bsVanillaPrice,
                                VannaVolgaDoubleBarrierEngine.Kernel.SUO_WANG);
                doubleBarrierOption.setPricingEngine(vannaVolgaEngine);

                // Expected result for KO is given in array; for KI it is
                // evaluated as vanilla - KO.
                double expected;
                if (barrierType == DoubleBarrier.Type.KnockOut) {
                    expected = value.result;
                } else {
                    expected = bsVanillaPrice - value.result;
                }

                double calculated = doubleBarrierOption.NPV();
                double error = Math.abs(calculated - expected);
                if (error > value.tol) {
                    reportFxFailure("value", value, barrierType, payoff,
                            exercise, today, expected, calculated, error,
                            value.tol);
                }

                vannaVolgaEngine = new VannaVolgaDoubleBarrierEngine(
                        volAtmQuote,
                        vol25PutQuote,
                        vol25CallQuote,
                        new Handle<>(spot, true),
                        new Handle<>(rTS, true),
                        new Handle<>(qTS, true),
                        true,
                        bsVanillaPrice,
                        VannaVolgaDoubleBarrierEngine.Kernel.ANALYTIC);
                doubleBarrierOption.setPricingEngine(vannaVolgaEngine);

                calculated = doubleBarrierOption.NPV();
                error = Math.abs(calculated - expected);
                // different engines have somewhat different results
                double maxtol = 5.0e-3;
                if (error > maxtol) {
                    reportFxFailure("value", value, barrierType, payoff,
                            exercise, today, expected, calculated, error,
                            maxtol);
                }
            }
        }
    }

    @Test
    public void testMonteCarloDoubleBarrierWithAnalytical() {
        double tolerance = 0.01;

        // set up dates
        Target calendar = new Target();
        Date todaysDate = new Date(15, Month.MAY, 1998);
        Date settlementDate = new Date(17, Month.MAY, 1998);
        Settings.instance.evaluationDate().equal(todaysDate);

        // our options
        Option.Type type = Option.Type.Put;
        double underlying = 36;
        double strike = 40;
        double dividendYield = 0.00;
        double riskFreeRate = 0.06;
        double volatility = 0.20;
        Date maturity = new Date(17, Month.MAY, 1999);
        DayCounter dayCounter = new Actual365Fixed();

        Exercise europeanExercise = new EuropeanExercise(maturity);

        Handle<Quote> underlyingH = new Handle<>(
                new SimpleQuote(underlying), true);

        // bootstrap the yield/dividend/vol curves
        Handle<YieldTermStructure> flatTermStructure = new Handle<>(
                new FlatForward(settlementDate, riskFreeRate, dayCounter),
                true);
        Handle<YieldTermStructure> flatDividendTS = new Handle<>(
                new FlatForward(settlementDate, dividendYield, dayCounter),
                true);
        Handle<BlackVolTermStructure> flatVolTS = new Handle<>(
                new BlackConstantVol(settlementDate, calendar, volatility,
                        dayCounter), true);
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        BlackScholesMertonProcess bsmProcess = new BlackScholesMertonProcess(
                underlyingH, flatDividendTS, flatTermStructure, flatVolTS);

        double barrierLow = underlying * 0.9;
        double barrierHigh = underlying * 1.1;

        DoubleBarrierOption knockInDoubleBarrierOption = new DoubleBarrierOption(
                DoubleBarrier.Type.KnockIn, barrierLow, barrierHigh, 0.0,
                payoff, europeanExercise);

        PricingEngine analyticdoublebarrierengine =
                new AnalyticDoubleBarrierEngine(bsmProcess);
        knockInDoubleBarrierOption.setPricingEngine(
                analyticdoublebarrierengine);
        double analytical = knockInDoubleBarrierOption.NPV();

        PricingEngine mcdoublebarrierengine = new MakeMCDoubleBarrierEngine(
                bsmProcess, RngType.PSEUDO_RANDOM)
                .withSteps(5000)
                .withAntitheticVariate()
                .withAbsoluteTolerance(0.5)
                .withSeed(1)
                .build();
        knockInDoubleBarrierOption.setPricingEngine(mcdoublebarrierengine);
        double monteCarlo = knockInDoubleBarrierOption.NPV();

        double percentageDiff = Math.abs(analytical - monteCarlo) / analytical;
        if (percentageDiff > tolerance) {
            fail("Double Barrier Option Threshold exceeded: "
                    + percentageDiff
                    + "\nAnalytical:  " + analytical
                    + "\nMonte Carlo: " + monteCarlo);
        }

        DoubleBarrierOption knockOutDoubleBarrierOption = new DoubleBarrierOption(
                DoubleBarrier.Type.KnockOut, barrierLow, barrierHigh, 0.0,
                payoff, europeanExercise);

        knockOutDoubleBarrierOption.setPricingEngine(
                analyticdoublebarrierengine);
        analytical = knockOutDoubleBarrierOption.NPV();

        tolerance = 0.01;

        mcdoublebarrierengine = new MakeMCDoubleBarrierEngine(
                bsmProcess, RngType.PSEUDO_RANDOM)
                .withSteps(5000)
                .withAntitheticVariate()
                .withAbsoluteTolerance(tolerance)
                .withSeed(10)
                .build();
        knockOutDoubleBarrierOption.setPricingEngine(mcdoublebarrierengine);
        monteCarlo = knockOutDoubleBarrierOption.NPV();

        double diff = Math.abs(analytical - monteCarlo);
        if (diff > tolerance) {
            fail("Double Barrier Option Threshold exceeded: " + diff
                    + "\nAnalytical:  " + analytical
                    + "\nMonte Carlo: " + monteCarlo);
        }
    }

    private static void reportFailure(String greekName,
                                      NewBarrierOptionData value,
                                      PlainVanillaPayoff payoff,
                                      Exercise exercise, Date today,
                                      double expected, double calculated,
                                      double error, double tolerance) {
        fail("\n" + DoubleBarrier.toString(value.barrierType) + " "
                + exerciseTypeToString(exercise) + " "
                + payoff.optionType() + " option with plain-vanilla payoff:\n"
                + "    underlying value: " + value.s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    barrier low:      " + value.barrierlo + "\n"
                + "    barrier high:     " + value.barrierhi + "\n"
                + "    dividend yield:   " + value.q + "\n"
                + "    risk-free rate:   " + value.r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + exercise.lastDate() + "\n"
                + "    volatility:       " + value.v + "\n\n"
                + "    expected   " + greekName + ": " + expected + "\n"
                + "    calculated " + greekName + ": " + calculated + "\n"
                + "    error:            " + error + "\n"
                + "    tolerance:        " + tolerance);
    }

    /** Official {@code REPORT_FAILURE_VANNAVOLGA} message fields. */
    private static void reportFxFailure(String greekName,
                                        DoubleBarrierFxOptionData value,
                                        DoubleBarrier.Type barrierType,
                                        PlainVanillaPayoff payoff,
                                        Exercise exercise, Date today,
                                        double expected, double calculated,
                                        double error, double tolerance) {
        fail("\n" + "Double Barrier Option "
                + DoubleBarrier.toString(barrierType) + " "
                + exerciseTypeToString(exercise) + " "
                + payoff.optionType() + " option with plain-vanilla payoff:\n"
                + "    underlying value: " + value.s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    barrier1:         " + value.barrier1 + "\n"
                + "    barrier2:         " + value.barrier2 + "\n"
                + "    rebate:           " + value.rebate + "\n"
                + "    dividend yield:   " + value.q + "\n"
                + "    risk-free rate:   " + value.r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + exercise.lastDate() + "\n"
                + "    25PutVol:         " + value.vol25Put + "\n"
                + "    atmVol:           " + value.volAtm + "\n"
                + "    25CallVol:        " + value.vol25Call + "\n"
                + "    volatility:       " + value.v + "\n\n"
                + "    expected   " + greekName + ": " + expected + "\n"
                + "    calculated " + greekName + ": " + calculated + "\n"
                + "    error:            " + error + "\n"
                + "    tolerance:        " + tolerance);
    }

    private static String exerciseTypeToString(Exercise exercise) {
        switch (exercise.type()) {
            case European:
                return "European";
            case American:
                return "American";
            case Bermudan:
                return "Bermudan";
            default:
                return exercise.type().toString();
        }
    }

    private static int timeToDays(double t) {
        return (int) Math.round(t * 360.0);
    }

    private static int timeToDays(double t, int daysPerYear) {
        return (int) Math.round(t * daysPerYear);
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
