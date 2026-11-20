package pricingengines;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.barrier.AnalyticBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.BlackScholesProcess;
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
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Business252;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Barrier option pricing from QuantLib
 * {@code test-suite/barrieroption.cpp}.
 *
 * <p>This class ports the standard single-barrier analytic coverage agreed
 * for this step: five complete official cases and four partial ones. The
 * partial cases are documented individually; the remaining official barrier
 * cases are not started.
 */
public class BarrierOptionTest {

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

    /** Official {@code BarrierOptionData} used by the Babsiri/Beaglehole cases. */
    private static class BarrierOptionData {
        final Barrier.Type type;
        final double volatility;
        final double strike;
        final double barrier;
        final double callValue;
        final double putValue;

        BarrierOptionData(Barrier.Type type, double volatility, double strike,
                          double barrier, double callValue, double putValue) {
            this.type = type;
            this.volatility = volatility;
            this.strike = strike;
            this.barrier = barrier;
            this.callValue = callValue;
            this.putValue = putValue;
        }
    }

    /**
     * Official {@code NewBarrierOptionData}: barrier type, barrier, rebate,
     * payoff and exercise type, strike, spot, dividend, rate, time,
     * volatility, expected value and tolerance.
     */
    private static class NewBarrierOptionData {
        final Barrier.Type barrierType;
        final double barrier;
        final double rebate;
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

        NewBarrierOptionData(Barrier.Type barrierType, double barrier,
                             double rebate, Option.Type type,
                             Exercise.Type exType, double strike, double s,
                             double q, double r, double t, double v,
                             double result, double tol) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.rebate = rebate;
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

    private static class PutCallSymmetryCase {
        final double callStrike;
        final double callBarrier;
        final Barrier.Type callType;
        final double putStrike;
        final double putBarrier;
        final Barrier.Type putType;

        PutCallSymmetryCase(double callStrike, double callBarrier,
                            Barrier.Type callType, double putStrike,
                            double putBarrier, Barrier.Type putType) {
            this.callStrike = callStrike;
            this.callBarrier = callBarrier;
            this.callType = callType;
            this.putStrike = putStrike;
            this.putBarrier = putBarrier;
            this.putType = putType;
        }
    }

    @Test
    public void testParity() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360();

        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure rTS = flatRate(today, 0.01, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.20, dc);
        RelinkableHandle<BlackVolTermStructure> volHandle =
                new RelinkableHandle<>(volTS, true);

        BlackScholesProcess stochProcess = new BlackScholesProcess(
                new Handle<>(spot, true), new Handle<>(rTS, true), volHandle);

        Date exerciseDate = today.add(new Period(6, TimeUnit.MONTHS));

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 100.0);
        Exercise exercise = new EuropeanExercise(exerciseDate);

        BarrierOption knockIn = new BarrierOption(Barrier.Type.DownIn,
                90.0, 0.0, payoff, exercise);
        BarrierOption knockOut = new BarrierOption(Barrier.Type.DownOut,
                90.0, 0.0, payoff, exercise);
        EuropeanOption european = new EuropeanOption(payoff, exercise);

        PricingEngine barrierEngine = new AnalyticBarrierEngine(stochProcess);
        PricingEngine europeanEngine = new AnalyticEuropeanEngine(stochProcess);

        knockIn.setPricingEngine(barrierEngine);
        knockOut.setPricingEngine(barrierEngine);
        european.setPricingEngine(europeanEngine);

        double replicated = knockIn.NPV() + knockOut.NPV();
        double expected = european.NPV();
        double error = Math.abs(replicated - expected);
        if (error > 1e-7) {
            fail("Failed to replicate European option"
                    + "\n    knock-in:   " + knockIn.NPV()
                    + "\n    knock-out:  " + knockOut.NPV()
                    + "\n    replicated: " + replicated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        // Try again with different day counters.
        volHandle.linkTo(flatVol(today, new SimpleQuote(0.20),
                new Business252(new Target())), true);

        replicated = knockIn.NPV() + knockOut.NPV();
        expected = european.NPV();
        error = Math.abs(replicated - expected);
        if (error > 1e-7) {
            fail("Failed to replicate European option"
                    + "\n    knock-in:   " + knockIn.NPV()
                    + "\n    knock-out:  " + knockOut.NPV()
                    + "\n    replicated: " + replicated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testPutCallSymmetry() {
        PutCallSymmetryCase[] cases = {
                new PutCallSymmetryCase(90, 95, Barrier.Type.DownOut,
                        111.11111, 105.26315, Barrier.Type.UpOut),
                new PutCallSymmetryCase(95, 95, Barrier.Type.DownOut,
                        105.26315, 105.26315, Barrier.Type.UpOut),
                new PutCallSymmetryCase(100, 95, Barrier.Type.DownOut,
                        100.0, 105.26315, Barrier.Type.UpOut),
                new PutCallSymmetryCase(105, 95, Barrier.Type.DownOut,
                        95.23809, 105.26315, Barrier.Type.UpOut),
                new PutCallSymmetryCase(110, 95, Barrier.Type.DownOut,
                        90.90909, 105.26315, Barrier.Type.UpOut),

                new PutCallSymmetryCase(90.0, 120.0, Barrier.Type.UpOut,
                        111.11111, 83.33333, Barrier.Type.DownOut),
                new PutCallSymmetryCase(95.0, 120.0, Barrier.Type.UpOut,
                        105.26315, 83.33333, Barrier.Type.DownOut),
                new PutCallSymmetryCase(100.0, 120.0, Barrier.Type.UpOut,
                        100.0, 83.33333, Barrier.Type.DownOut),
                new PutCallSymmetryCase(105.0, 120.0, Barrier.Type.UpOut,
                        95.23809, 83.33333, Barrier.Type.DownOut),
                new PutCallSymmetryCase(110.0, 120.0, Barrier.Type.UpOut,
                        90.90909, 83.33333, Barrier.Type.DownOut)
        };

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();
        Date maturity = today.add(360);
        Exercise exercise = new EuropeanExercise(maturity);
        double r = 0.01;
        double rebate = 0.0;
        double spotPrice = 100;

        SimpleQuote spot = new SimpleQuote();
        SimpleQuote qRateCall = new SimpleQuote(0.0);
        SimpleQuote rRateCall = new SimpleQuote(r);
        SimpleQuote rRatePut = new SimpleQuote(0.0);
        SimpleQuote qRatePut = new SimpleQuote(r);
        SimpleQuote vol = new SimpleQuote(0.25);

        Handle<Quote> underlying = new Handle<>(spot, true);
        Handle<YieldTermStructure> dividendTSCall = new Handle<>(
                flatRate(today, qRateCall, dc), true);
        Handle<YieldTermStructure> dividendTSPut = new Handle<>(
                flatRate(today, qRatePut, dc), true);
        Handle<YieldTermStructure> riskFreeTSCall = new Handle<>(
                flatRate(today, rRateCall, dc), true);
        Handle<YieldTermStructure> riskFreeTSPut = new Handle<>(
                flatRate(today, rRatePut, dc), true);
        Handle<BlackVolTermStructure> blackVolTS = new Handle<>(
                flatVol(today, vol, dc), true);

        BlackScholesMertonProcess processCall = new BlackScholesMertonProcess(
                underlying, dividendTSCall, riskFreeTSCall, blackVolTS);
        BlackScholesMertonProcess processPut = new BlackScholesMertonProcess(
                underlying, dividendTSPut, riskFreeTSPut, blackVolTS);

        PricingEngine callEngine = new AnalyticBarrierEngine(processCall);
        PricingEngine putEngine = new AnalyticBarrierEngine(processPut);

        for (PutCallSymmetryCase i : cases) {
            PlainVanillaPayoff putPayoff =
                    new PlainVanillaPayoff(Option.Type.Put, i.putStrike);
            PlainVanillaPayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, i.callStrike);
            BarrierOption putOption = new BarrierOption(i.putType,
                    i.putBarrier, rebate, putPayoff, exercise);
            putOption.setPricingEngine(putEngine);
            BarrierOption callOption = new BarrierOption(i.callType,
                    i.callBarrier, rebate, callPayoff, exercise);
            callOption.setPricingEngine(callEngine);

            spot.setValue(spotPrice);
            double putValue = putOption.NPV();
            double callValue = callOption.NPV();
            double callAmount = i.putStrike / spotPrice;
            double error = Math.abs(putValue - callAmount * callValue);
            double tolerance = 1e-4;
            if (error > tolerance) {
                fail("Failed to reproduce the put-call symmetry for the "
                        + "partial-time barrier options "
                        + "\n    error:      " + error);
            }
        }
    }

    private static final NewBarrierOptionData[] HAUG_VALUES = {
            // The data below are from "Option pricing formulas",
            // E.G. Haug, McGraw-Hill 1998 pag. 72
            // barrierType, barrier, rebate, type, exercise, strike, spot,
            // q, r, t, v, result, tol
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 9.0246, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 6.7924, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.8759, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.6789, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.3580, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.3453, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 7.7627, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.0109, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.0576, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 13.8333, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 7.8494, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.9795, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 14.1112, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 8.4482, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.5910, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 8.8334, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 7.0285, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 5.4137, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.6341, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.4389, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.4315, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 9.0093, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 5.1370, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.8517, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 14.8816, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 9.2045, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 5.3043, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 15.2098, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 9.7278, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 5.8350, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.2798, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.2947, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.6252, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.7760, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 5.4932, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 7.5187, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.9586, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 6.5677, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 11.9752, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.2845, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 5.9085, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 11.6465, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 1.4653, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.3721, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 7.0846, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.4170, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.4258, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.6246, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 4.2293, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 5.8032, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 7.5649, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.8769, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 7.7989, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 13.3078, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 3.3328, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 7.2636, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 12.9713, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 90, 100.0,
                    0.04, 0.08, 0.50, 0.30, 2.0658, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 100, 100.0,
                    0.04, 0.08, 0.50, 0.30, 4.4226, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Put, Exercise.Type.European, 110, 100.0,
                    0.04, 0.08, 0.50, 0.30, 8.3686, 1.0e-4),

            // Options with american exercise: values computed with 400 steps
            // of Haug's VBA code (handles only out options). They are inputs
            // to the official exception checks only; their prices are not
            // asserted against the analytic engine.
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 0.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 10.4655, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 0.0,
                    Option.Type.Call, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.5159, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 0.0,
                    Option.Type.Call, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.5971, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 0.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 11.8076, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 0.0,
                    Option.Type.Call, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.3993, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.3457, 1.0e-4),

            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0,
                    Option.Type.Put, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.2795, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 0.0,
                    Option.Type.Put, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.3512, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 0.0,
                    Option.Type.Put, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 11.5773, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0,
                    Option.Type.Put, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.0000, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 0.0,
                    Option.Type.Put, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 1.4763, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 0.0,
                    Option.Type.Put, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 3.3001, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 0.0,
                    Option.Type.Put, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 10.0000, 1.0e-4),

            // Some american in-options - results (roughly) verified with
            // other numerical methods.
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 7.7615, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 100, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.0118, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 2.0544, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 13.8308, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 90, 100.0,
                    0.04, 0.08, 0.50, 0.25, 14.1150, 1.0e-4),
            new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0,
                    Option.Type.Call, Exercise.Type.American, 110, 100.0,
                    0.04, 0.08, 0.50, 0.25, 4.5900, 1.0e-4)
    };

    /**
     * Partial port of official {@code testHaugValues}.
     *
     * <p>Ported branches: all 72 European rows priced with
     * {@link AnalyticBarrierEngine}, plus the official analytic exception
     * checks (American exercise, zero spot and triggered barrier).
     *
     * <p>Deferred official branches, not ported here: the
     * {@code FdBlackScholesBarrierEngine} checks, the binomial
     * Boyle-Lau checks and the binomial Derman-Kani checks. The 24 American
     * Haug prices are not asserted against the analytic engine.
     */
    @Test
    public void testHaugValues() {
        for (NewBarrierOptionData value : HAUG_VALUES) {
            runHaugTest(value, false, false);
            runHaugTest(value, true, false);
            runHaugTest(value, false, true);
        }
    }

    private static void runHaugTest(NewBarrierOptionData value,
                                    boolean useZeroSpot,
                                    boolean useTriggeredBarrier) {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();
        Date exDate = today.add(timeToDays(value.t));

        SimpleQuote spot = new SimpleQuote(useZeroSpot ? 0.0 : value.s);
        SimpleQuote qRate = new SimpleQuote(value.q);
        SimpleQuote rRate = new SimpleQuote(value.r);
        SimpleQuote vol = new SimpleQuote(value.v);

        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(value.type, value.strike);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        Exercise exercise;
        if (value.exType == Exercise.Type.European) {
            exercise = new EuropeanExercise(exDate);
        } else {
            exercise = new AmericanExercise(exDate);
        }

        double barrier = useTriggeredBarrier
                ? (value.barrierType == Barrier.Type.DownIn
                || value.barrierType == Barrier.Type.DownOut
                ? value.s * 1.01 : value.s * 0.99)
                : value.barrier;

        BarrierOption barrierOption = new BarrierOption(value.barrierType,
                barrier, value.rebate, payoff, exercise);
        barrierOption.setPricingEngine(new AnalyticBarrierEngine(stochProcess));

        if (!useZeroSpot && !useTriggeredBarrier
                && value.exType == Exercise.Type.European) {
            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            if (error > value.tol) {
                reportFailure("value", value.barrierType, value.barrier,
                        value.rebate, payoff, exercise, value.s, value.q,
                        value.r, today, value.v, expected, calculated, error,
                        value.tol);
            }
        } else {
            expectException(barrierOption::NPV);
        }
    }

    private static final BarrierOptionData[] BABSIRI_VALUES = {
            // Data from "Simulating Path-Dependent Options: A New Approach",
            // M. El Babsiri and G. Noel, Journal of Derivatives, Winter 1998.
            new BarrierOptionData(Barrier.Type.DownIn, 0.10, 100, 90,
                    0.07187, 0.0),
            new BarrierOptionData(Barrier.Type.DownIn, 0.15, 100, 90,
                    0.60638, 0.0),
            new BarrierOptionData(Barrier.Type.DownIn, 0.20, 100, 90,
                    1.64005, 0.0),
            new BarrierOptionData(Barrier.Type.DownIn, 0.25, 100, 90,
                    2.98495, 0.0),
            new BarrierOptionData(Barrier.Type.DownIn, 0.30, 100, 90,
                    4.50952, 0.0),
            new BarrierOptionData(Barrier.Type.UpIn, 0.10, 100, 110,
                    4.79148, 0.0),
            new BarrierOptionData(Barrier.Type.UpIn, 0.15, 100, 110,
                    7.08268, 0.0),
            new BarrierOptionData(Barrier.Type.UpIn, 0.20, 100, 110,
                    9.11008, 0.0),
            new BarrierOptionData(Barrier.Type.UpIn, 0.25, 100, 110,
                    11.06148, 0.0),
            new BarrierOptionData(Barrier.Type.UpIn, 0.30, 100, 110,
                    12.98351, 0.0)
    };

    /**
     * Partial port of official {@code testBabsiriValues}.
     *
     * <p>Ported branch: the 10 official analytic Call rows against
     * {@link AnalyticBarrierEngine} with absolute tolerance {@code 1.0e-5}.
     *
     * <p>Deferred official branch, not ported here: the Monte Carlo checks
     * using {@code MakeMCBarrierEngine}.
     */
    @Test
    public void testBabsiriValues() {
        double underlyingPrice = 100.0;
        double rebate = 0.0;
        double r = 0.05;
        double q = 0.02;

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote underlying = new SimpleQuote(underlyingPrice);
        SimpleQuote qH_SME = new SimpleQuote(q);
        YieldTermStructure qTS = flatRate(today, qH_SME, dc);
        SimpleQuote rH_SME = new SimpleQuote(r);
        YieldTermStructure rTS = flatRate(today, rH_SME, dc);
        SimpleQuote volatility = new SimpleQuote(0.10);
        BlackVolTermStructure volTS = flatVol(today, volatility, dc);

        Date exDate = today.add(360);
        Exercise exercise = new EuropeanExercise(exDate);

        for (BarrierOptionData value : BABSIRI_VALUES) {
            volatility.setValue(value.volatility);

            PlainVanillaPayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, value.strike);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(underlying, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            PricingEngine engine = new AnalyticBarrierEngine(stochProcess);

            BarrierOption barrierCallOption = new BarrierOption(value.type,
                    value.barrier, rebate, callPayoff, exercise);
            barrierCallOption.setPricingEngine(engine);
            double calculated = barrierCallOption.NPV();
            double expected = value.callValue;
            double error = Math.abs(calculated - expected);
            double maxErrorAllowed = 1.0e-5;
            if (error > maxErrorAllowed) {
                reportFailure("value", value.type, value.barrier, rebate,
                        callPayoff, exercise, underlyingPrice, q, r, today,
                        value.volatility, expected, calculated, error,
                        maxErrorAllowed);
            }
        }
    }

    private static final BarrierOptionData[] BEAGLEHOLE_VALUES = {
            // Data from "Going to Extreme: Correcting Simulation Bias in
            // Exotic Option Valuation", D.R. Beaglehole, P.H. Dybvig and
            // G. Zhou, Financial Analysts Journal, Jan/Feb 1997.
            new BarrierOptionData(Barrier.Type.DownOut, 0.50, 50, 45,
                    5.477, 0.0)
    };

    /**
     * Partial port of official {@code testBeagleholeValues}.
     *
     * <p>Ported branch: the official analytic DownOut Call row with
     * absolute tolerance {@code 1.0e-3}.
     *
     * <p>Deferred official branch, not ported here: the Monte Carlo check
     * using {@code MakeMCBarrierEngine}.
     */
    @Test
    public void testBeagleholeValues() {
        double underlyingPrice = 50.0;
        double rebate = 0.0;
        double r = Math.log(1.1);
        double q = 0.00;

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote underlying = new SimpleQuote(underlyingPrice);
        SimpleQuote qH_SME = new SimpleQuote(q);
        YieldTermStructure qTS = flatRate(today, qH_SME, dc);
        SimpleQuote rH_SME = new SimpleQuote(r);
        YieldTermStructure rTS = flatRate(today, rH_SME, dc);
        SimpleQuote volatility = new SimpleQuote(0.10);
        BlackVolTermStructure volTS = flatVol(today, volatility, dc);

        Date exDate = today.add(360);
        Exercise exercise = new EuropeanExercise(exDate);

        for (BarrierOptionData value : BEAGLEHOLE_VALUES) {
            volatility.setValue(value.volatility);

            PlainVanillaPayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, value.strike);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(underlying, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            PricingEngine engine = new AnalyticBarrierEngine(stochProcess);

            BarrierOption barrierCallOption = new BarrierOption(value.type,
                    value.barrier, rebate, callPayoff, exercise);
            barrierCallOption.setPricingEngine(engine);
            double calculated = barrierCallOption.NPV();
            double expected = value.callValue;
            double maxErrorAllowed = 1.0e-3;
            double error = Math.abs(calculated - expected);
            if (error > maxErrorAllowed) {
                reportFailure("value", value.type, value.barrier, rebate,
                        callPayoff, exercise, underlyingPrice, q, r, today,
                        value.volatility, expected, calculated, error,
                        maxErrorAllowed);
            }
        }
    }

    /**
     * Partial port of official {@code testImpliedVolatility}.
     *
     * <p>Ported branch: the no-dividend half, solving for each of the four
     * barriers and repricing with {@link AnalyticBarrierEngine} to absolute
     * tolerance {@code 1.0e-5}.
     *
     * <p>Deferred official branch, not ported here: the dividend targets and
     * the {@code FdBlackScholesBarrierEngine} repricing.
     */
    @Test
    public void testImpliedVolatility() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(11, Month.FEBRUARY, 2018);
        Date maturity = today.add(new Period(1, TimeUnit.YEARS));
        Settings.instance.evaluationDate().equal(today);

        double spot = 100.0;
        double r = 0.05;
        double q = 0.0;

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(spot), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, q, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, r, dc), true);
        Handle<BlackVolTermStructure> dummyVolTS =
                new Handle<>(flatVol(today, 0.0, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, dummyVolTS);

        double strike = 105.0;
        double rebate = 5.0;

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);
        Exercise exercise = new EuropeanExercise(maturity);

        double[] barriers = {90.0, 110.0, 90.0, 110.0};
        Barrier.Type[] barrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.UpOut, Barrier.Type.DownIn, Barrier.Type.UpIn};
        double[] targetWithoutDividends = {1.0, 1.0, 5.0, 5.0};
        double tolerance = 1e-5;

        for (int i = 0; i < barriers.length; ++i) {
            double barrier = barriers[i];
            Barrier.Type barrierType = barrierTypes[i];

            BarrierOption barrierOption = new BarrierOption(barrierType,
                    barrier, rebate, payoff, exercise);
            double impliedVol = barrierOption.impliedVolatility(
                    targetWithoutDividends[i], bsProcess, 1e-6);

            BlackVolTermStructure volTS = flatVol(today,
                    new SimpleQuote(impliedVol), dc);
            BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    s0, qTS, rTS, new Handle<>(volTS, true));
            barrierOption.setPricingEngine(
                    new AnalyticBarrierEngine(process));

            double diff = Math.abs(barrierOption.NPV()
                    - targetWithoutDividends[i]);
            if (diff > tolerance) {
                fail("Failed to reproduce target option price:"
                        + "\n    strike:       " + strike
                        + "\n    barrier:      " + barrier
                        + "\n    maturity:     " + maturity
                        + "\n    target value: " + targetWithoutDividends[i]
                        + "\n    calculated:   " + barrierOption.NPV()
                        + "\n    difference    " + diff);
            }
        }
    }

    @Test
    public void testLowVolatility() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(11, Month.FEBRUARY, 2018);
        Settings.instance.evaluationDate().equal(today);

        Date maturity = today.add(new Period(1, TimeUnit.YEARS));

        double spot = 100.0;
        double vol = 1e-7;

        SimpleQuote Q = new SimpleQuote();
        SimpleQuote R = new SimpleQuote();

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(spot), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, Q, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, R, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);
        PricingEngine engine = new AnalyticBarrierEngine(process);

        //    strike          type     barrier     barrier type     rebate       r      q    expected
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 3.0);  // fwd = 102, put, ITM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                109.0, Option.Type.Put, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 7.0);  // fwd = 102, put, ITM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Put, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 0.0);  // fwd = 102, put, OTM
        checkLowVolatility(Q, R, engine, maturity,
                99.0, Option.Type.Put, 101.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 4.0);  // fwd = 102, knocked out, k < H
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 101.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 4.0);  // fwd = 102, knocked out, k > H
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Call, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 0.0);  // fwd = 102, call, OTM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                109.0, Option.Type.Call, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 0.0);  // fwd = 102, call, OTM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Call, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 2.0);  // fwd = 102, call, ITM
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Call, 101.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 4.0);  // fwd = 102, knocked out, k < H
        checkLowVolatility(Q, R, engine, maturity,
                99.0, Option.Type.Call, 101.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 4.0);  // fwd = 102, knocked out, k > H

        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 107.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 4.0);  // fwd = 103, not triggered, k < H
        checkLowVolatility(Q, R, engine, maturity,
                109.0, Option.Type.Put, 107.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 4.0);  // fwd = 103, not triggered, k > H
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 2.0);  // fwd = 103, put, ITM
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Put, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 0.0);  // fwd = 103, put, OTM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                102.0, Option.Type.Put, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 0.0);  // fwd = 103, put, OTM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Call, 107.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 4.0);  // fwd = 103, not triggered, k < H
        checkLowVolatility(Q, R, engine, maturity,
                109.0, Option.Type.Call, 107.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 4.0);  // fwd = 103, not triggered, k > H
        checkLowVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Call, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 0.0);  // fwd = 103, call, OTM
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Call, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 3.0);  // fwd = 103, call, ITM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                102.0, Option.Type.Call, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 1.0);  // fwd = 103, call, ITM, k > H

        checkLowVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Put, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 0.0);  // fwd = 98, put, OTM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Put, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 0.0);  // fwd = 98, put, OTM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Put, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 2.0);  // fwd = 98, put, ITM
        checkLowVolatility(Q, R, engine, maturity,
                97.0, Option.Type.Put, 99.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 4.0);  // fwd = 98, knocked out, k < H
        checkLowVolatility(Q, R, engine, maturity,
                101.0, Option.Type.Put, 99.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 4.0);  // fwd = 98, knocked out, k > H
        checkLowVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Call, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 7.0);  // fwd = 98, call, ITM
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Call, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 3.0);  // fwd = 98, call, ITM
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Call, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 0.0);  // fwd = 98, call, OTM
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Call, 99.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 4.0);  // fwd = 98, knocked out, k < H
        checkLowVolatility(Q, R, engine, maturity,
                101.0, Option.Type.Call, 99.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 4.0);  // fwd = 98, knocked out, k > H

        checkLowVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Put, 93.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 4.0);  // fwd = 97, not triggered, k < H
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Put, 93.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 4.0);  // fwd = 97, not triggered, k > H
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Put, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 3.0);  // fwd = 97, put, ITM
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Put, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 0.0);  // fwd = 97, put, OTM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Put, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 0.0);  // fwd = 97, put, OTM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Call, 93.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 4.0);  // fwd = 97, not triggered, k < H
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Call, 93.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 4.0);  // fwd = 97, not triggered, k > H
        checkLowVolatility(Q, R, engine, maturity,
                98.0, Option.Type.Call, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 0.0);  // fwd = 97, call, OTM, k < H
        checkLowVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Call, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 0.0);  // fwd = 97, call, OTM, k > H
        checkLowVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Call, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 2.0);  // fwd = 97, call, ITM
    }

    private static void checkLowVolatility(SimpleQuote Q, SimpleQuote R,
                                           PricingEngine engine, Date maturity,
                                           double strike, Option.Type optionType,
                                           double barrier,
                                           Barrier.Type barrierType,
                                           double rebate, double r, double q,
                                           double expected) {
        R.setValue(r);
        Q.setValue(q);
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(optionType, strike);
        Exercise exercise = new EuropeanExercise(maturity);
        BarrierOption option = new BarrierOption(barrierType, barrier,
                rebate, payoff, exercise);
        option.setPricingEngine(engine);
        double value = option.NPV();
        double diff = Math.abs(value - expected);
        if (Double.isNaN(value) || diff > 0.5) {
            fail("Failed to match expected price:"
                    + "\n    strike:         " + strike
                    + "\n    option type:    " + optionType
                    + "\n    barrier:        " + barrier
                    + "\n    barrier type:   "
                    + Barrier.toString(barrierType)
                    + "\n    rebate:         " + rebate
                    + "\n    risk-free rate: " + r
                    + "\n    dividend yield: " + q
                    + "\n    expected:       " + expected
                    + "\n    calculated:     " + value);
        }
    }

    @Test
    public void testLowVolatilityLargeCarry() {
        // At low volatility and large carry, (H/S)^(2 mu) overflows while
        // N(y) underflows, but their product is finite and not negligible.
        // Expected values are the same closed-form formulas evaluated with
        // 60-digit arithmetic.
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(15, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        Date maturity = today.add(747);

        double spot = 40.809474692328266;
        double strike = 100.0;
        double barrier = 26.788905850262115;
        double r = 0.04943364523655579;
        double q = 0.27314598534530515;

        SimpleQuote vol = new SimpleQuote();
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(spot), true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, r, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        PricingEngine engine = new AnalyticBarrierEngine(process);

        Barrier.Type[] types = {
                Barrier.Type.DownOut, Barrier.Type.DownOut,
                Barrier.Type.DownOut, Barrier.Type.DownOut,
                Barrier.Type.DownOut,
                Barrier.Type.DownIn, Barrier.Type.DownIn,
                Barrier.Type.DownIn, Barrier.Type.DownIn,
                Barrier.Type.DownIn,
                Barrier.Type.DownOut, Barrier.Type.DownOut,
                Barrier.Type.DownOut, Barrier.Type.DownOut,
                Barrier.Type.DownOut,
                Barrier.Type.DownIn, Barrier.Type.DownIn,
                Barrier.Type.DownIn, Barrier.Type.DownIn,
                Barrier.Type.DownIn
        };
        double[] rebates = {
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0,
                2.0, 2.0, 2.0, 2.0, 2.0,
                2.0, 2.0, 2.0, 2.0, 2.0
        };
        double[] vols = {
                0.005, 0.010, 0.012985838614515886, 0.016, 0.020,
                0.005, 0.010, 0.012985838614515886, 0.016, 0.020,
                0.005, 0.010, 0.012985838614515886, 0.016, 0.020,
                0.005, 0.010, 0.012985838614515886, 0.016, 0.020
        };
        double[] expected = {
                7.6135719504503223e-6, 0.30397185187568111,
                1.4378677391320085, 3.2549740062727869,
                5.9549948957634315,
                67.044217762067952, 66.740253523764222,
                65.606357636507894, 63.789251369367116,
                61.089230479876471,
                1.8223841335261903, 2.1180735128774331,
                3.220988434074213, 4.9884026997480601,
                7.6145160525959926,
                67.044217970160664, 66.74857193361007,
                65.645743358757572, 63.878505578488262,
                61.252770544422279
        };

        // The prices are differences of terms of the order of the spot, so
        // the error is relative to the price but at least absolute for small
        // prices.
        double tolerance = 1.0e-10;
        for (int i = 0; i < types.length; ++i) {
            vol.setValue(vols[i]);
            BarrierOption option = new BarrierOption(types[i], barrier,
                    rebates[i],
                    new PlainVanillaPayoff(Option.Type.Put, strike),
                    new EuropeanExercise(maturity));
            option.setPricingEngine(engine);
            double value = option.NPV();
            double error = Math.abs(value - expected[i])
                    / Math.max(expected[i], 1.0);
            if (Double.isNaN(value) || error > tolerance) {
                fail("Failed to reproduce expected barrier option value:"
                        + "\n    barrier type:   " + Barrier.toString(types[i])
                        + "\n    rebate:         " + rebates[i]
                        + "\n    volatility:     " + vols[i]
                        + "\n    expected:       " + expected[i]
                        + "\n    calculated:     " + value
                        + "\n    error:          " + error
                        + "\n    tolerance:      " + tolerance);
            }
        }
    }

    @Test
    public void testZeroVolatility() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(11, Month.FEBRUARY, 2018);
        Settings.instance.evaluationDate().equal(today);

        Date maturity = today.add(new Period(1, TimeUnit.YEARS));

        double spot = 100.0;

        SimpleQuote Q = new SimpleQuote();
        SimpleQuote R = new SimpleQuote();

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(spot), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, Q, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, R, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.0, dc), true);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);
        PricingEngine engine = new AnalyticBarrierEngine(process);

        // The underlying follows its forward; a knock-out rebate is paid when
        // the forward reaches the barrier, a knock-in rebate at maturity.
        //    strike          type     barrier     barrier type     rebate       r      q    expected
        checkZeroVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 107.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 2.891797647676558);  // not hit
        checkZeroVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Put, 101.0, Barrier.Type.UpOut,
                4.0, 0.03, 0.01, 3.940741347366294);  // hit at 0.4975
        checkZeroVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Call, 101.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 2.955446645149196);  // hit
        checkZeroVolatility(Q, R, engine, maturity,
                105.0, Option.Type.Call, 107.0, Barrier.Type.UpIn,
                4.0, 0.03, 0.0, 3.881782134194033);  // not hit
        checkZeroVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Call, 93.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 6.950018483676519);  // not hit
        checkZeroVolatility(Q, R, engine, maturity,
                101.0, Option.Type.Put, 99.0, Barrier.Type.DownOut,
                4.0, 0.01, 0.03, 3.979949748426480);  // hit at 0.5025
        checkZeroVolatility(Q, R, engine, maturity,
                100.0, Option.Type.Put, 99.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 2.926039459684483);  // hit
        checkZeroVolatility(Q, R, engine, maturity,
                91.0, Option.Type.Call, 93.0, Barrier.Type.DownIn,
                4.0, 0.01, 0.04, 3.960199334996672);  // not hit
        checkZeroVolatility(Q, R, engine, maturity,
                95.0, Option.Type.Call, 100.0, Barrier.Type.DownOut,
                4.0, 0.03, 0.0, 4.0);  // spot on the barrier
    }

    private static void checkZeroVolatility(SimpleQuote Q, SimpleQuote R,
                                            PricingEngine engine,
                                            Date maturity, double strike,
                                            Option.Type optionType,
                                            double barrier,
                                            Barrier.Type barrierType,
                                            double rebate, double r, double q,
                                            double expected) {
        R.setValue(r);
        Q.setValue(q);
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(optionType, strike);
        Exercise exercise = new EuropeanExercise(maturity);
        BarrierOption option = new BarrierOption(barrierType, barrier,
                rebate, payoff, exercise);
        option.setPricingEngine(engine);
        double value = option.NPV();
        double diff = Math.abs(value - expected);
        if (!(diff <= 1.0e-10)) {
            fail("Failed to match expected price:"
                    + "\n    strike:         " + strike
                    + "\n    option type:    " + optionType
                    + "\n    barrier:        " + barrier
                    + "\n    barrier type:   "
                    + Barrier.toString(barrierType)
                    + "\n    rebate:         " + rebate
                    + "\n    risk-free rate: " + r
                    + "\n    dividend yield: " + q
                    + "\n    expected:       " + expected
                    + "\n    calculated:     " + value);
        }
    }

    private static void expectException(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        fail("exception expected");
    }

    private static void reportFailure(String greekName,
                                      Barrier.Type barrierType,
                                      double barrier, double rebate,
                                      PlainVanillaPayoff payoff,
                                      Exercise exercise, double s, double q,
                                      double r, Date today, double v,
                                      double expected, double calculated,
                                      double error, double tolerance) {
        fail("\n" + barrierTypeToString(barrierType) + " "
                + exerciseTypeToString(exercise) + " "
                + payoff.optionType() + " option with plain-vanilla payoff:\n"
                + "    underlying value: " + s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    barrier:          " + barrier + "\n"
                + "    rebate:           " + rebate + "\n"
                + "    dividend yield:   " + q + "\n"
                + "    risk-free rate:   " + r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + exercise.lastDate() + "\n"
                + "    volatility:       " + v + "\n\n"
                + "    expected   " + greekName + ": " + expected + "\n"
                + "    calculated " + greekName + ": " + calculated + "\n"
                + "    error:            " + error + "\n"
                + "    tolerance:        " + tolerance);
    }

    /** Official {@code barrierTypeToString} from test-suite utilities. */
    private static String barrierTypeToString(Barrier.Type type) {
        switch (type) {
            case DownIn:
                return "Down-and-in";
            case UpIn:
                return "Up-and-in";
            case DownOut:
                return "Down-and-out";
            case UpOut:
                return "Up-and-out";
            default:
                return "unknown exercise type";
        }
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
