package pricingengines;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.BermudanExercise;
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
import jquant.cashflows.Dividend;
import jquant.experimental.barrieroption.PerturbativeBarrierOptionEngine;
import jquant.experimental.barrieroption.VannaVolgaBarrierEngine;
import jquant.indexes.IndexManager;
import jquant.math.Matrix;
import jquant.math.interpolations.impl.Bicubic;
import jquant.math.randomnumbers.RngType;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.barrier.AnalyticBarrierEngine;
import jquant.pricingengines.barrier.BinomialBarrierEngine;
import jquant.pricingengines.barrier.FdBlackScholesBarrierEngine;
import jquant.pricingengines.barrier.FdHestonBarrierEngine;
import jquant.pricingengines.barrier.MakeMCBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BinomialVanillaEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.pricingengines.vanilla.QdFpAmericanEngine;
import jquant.quotes.DeltaVolQuote;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.BlackScholesProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVarianceCurve;
import jquant.termstructures.volatility.equityfx.BlackVarianceSurface;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Barrier option pricing from QuantLib
 * {@code test-suite/barrieroption.cpp}.
 *
 * <p>This class ports the complete official single-barrier coverage agreed
 * for this step: all 24 official cases are complete, using the analytic,
 * finite-differences, binomial (Boyle-Lau and Derman-Kani), Monte Carlo,
 * perturbative and Vanna-Volga engines.
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
     * Complete port of official {@code testHaugValues}.
     *
     * <p>All 72 European rows are priced with {@link AnalyticBarrierEngine};
     * the {@link FdBlackScholesBarrierEngine} branch covers European options
     * and American knock-outs; the 400-step Boyle-Lau and Derman-Kani
     * binomial branches use {@link BinomialBarrierEngine}; and the official
     * exception checks (American knock-ins, zero spot and triggered barrier)
     * are kept. For the FD branch, American knock-outs whose barrier lies in
     * the money (and which are not on the barrier) are skipped exactly as in
     * the official test. The Derman-Kani branch follows the official engine
     * construction, leaving the default Boyle-Lau adjustment enabled.
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

        barrierOption.setPricingEngine(
                new FdBlackScholesBarrierEngine(stochProcess, 200, 400));

        final boolean knockOut = value.barrierType == Barrier.Type.DownOut
                || value.barrierType == Barrier.Type.UpOut;
        if (!useZeroSpot && !useTriggeredBarrier
                && (value.exType == Exercise.Type.European || knockOut)) {
            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            // Haug's 400-step lattice values are biased low by up to 13%
            // for american knock-outs whose barrier lies in the money,
            // where lattices converge slowly; those cases are tested
            // against converged values in testFdAmericanKnockOut instead.
            final boolean comparable =
                    value.exType == Exercise.Type.European
                    || value.barrier == value.s
                    || payoff.value(value.barrier) <= value.rebate;
            if (comparable && error > 5.0e-3) {
                reportFailure("fd value", value.barrierType, value.barrier,
                        value.rebate, payoff, exercise, value.s, value.q,
                        value.r, today, value.v, expected, calculated, error,
                        5.0e-3);
            }
        } else {
            expectException(barrierOption::NPV);
        }

        // Binomial barrier engine, standard discretization with the
        // Boyle-Lau timestep adjustment (maxTimeSteps defaults to zero).
        barrierOption.setPricingEngine(new BinomialBarrierEngine(stochProcess,
                400));

        if (!useZeroSpot && !useTriggeredBarrier) {
            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            double tol = 1.1e-2;
            if (error > tol) {
                reportFailure("Binomial (Boyle-lau) value", value.barrierType,
                        value.barrier, value.rebate, payoff, exercise, value.s,
                        value.q, value.r, today, value.v, expected, calculated,
                        error, tol);
            }
        } else {
            expectException(barrierOption::NPV);
        }

        // Derman-Kani discretization. The official test passes only the
        // number of time steps (its comment about forcing maxTimeSteps to
        // timeSteps is stale: at exactly 400 steps the knock-out barrier
        // phase makes the official 4e-2 tolerance unachievable). The
        // default maxTimeSteps therefore leaves Boyle-Lau enabled, as in
        // the official source.
        barrierOption.setPricingEngine(new BinomialBarrierEngine(stochProcess,
                400, 0, BinomialBarrierEngine.Discretization.DERMAN_KANI));

        if (!useZeroSpot && !useTriggeredBarrier) {
            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            double tol = 4e-2;
            if (error > tol) {
                reportFailure("Binomial (Derman) value", value.barrierType,
                        value.barrier, value.rebate, payoff, exercise, value.s,
                        value.q, value.r, today, value.v, expected, calculated,
                        error, tol);
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
     * Complete port of official {@code testBabsiriValues}.
     *
     * <p>The 10 official analytic Call rows are priced with
     * {@link AnalyticBarrierEngine} to absolute tolerance {@code 1.0e-5};
     * the Monte Carlo branch uses
     * {@link MakeMCBarrierEngine} with low-discrepancy numbers, a Brownian
     * bridge, 131071 samples, seed 5, and relative tolerance {@code 2.0e-2}.
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

            double maxMcRelativeErrorAllowed = 2.0e-2;

            PricingEngine mcEngine = new MakeMCBarrierEngine(stochProcess,
                    RngType.LOW_DISCREPANCY)
                    .withStepsPerYear(1)
                    .withBrownianBridge()
                    .withSamples(131071)     // 2^17-1
                    .withMaxSamples(1048575) // 2^20-1
                    .withSeed(5)
                    .build();

            barrierCallOption.setPricingEngine(mcEngine);
            calculated = barrierCallOption.NPV();
            error = Math.abs(calculated - expected) / expected;
            if (error > maxMcRelativeErrorAllowed) {
                reportFailure("value", value.type, value.barrier, rebate,
                        callPayoff, exercise, underlyingPrice, q, r, today,
                        value.volatility, expected, calculated, error,
                        maxMcRelativeErrorAllowed);
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
     * Complete port of official {@code testBeagleholeValues}.
     *
     * <p>The official analytic DownOut Call row is priced with
     * {@link AnalyticBarrierEngine} to absolute tolerance {@code 1.0e-3};
     * the Monte Carlo branch uses {@link MakeMCBarrierEngine} with
     * low-discrepancy numbers, a Brownian bridge, 131071 samples, seed 10,
     * and relative tolerance {@code 0.01}.
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

            double maxMcRelativeErrorAllowed = 0.01;
            PricingEngine mcEngine = new MakeMCBarrierEngine(stochProcess,
                    RngType.LOW_DISCREPANCY)
                    .withStepsPerYear(1)
                    .withBrownianBridge()
                    .withSamples(131071)     // 2^17-1
                    .withMaxSamples(1048575) // 2^20-1
                    .withSeed(10)
                    .build();

            barrierCallOption.setPricingEngine(mcEngine);
            calculated = barrierCallOption.NPV();
            error = Math.abs(calculated - expected) / expected;
            if (error > maxMcRelativeErrorAllowed) {
                reportFailure("value", value.type, value.barrier, rebate,
                        callPayoff, exercise, underlyingPrice, q, r, today,
                        value.volatility, expected, calculated, error,
                        maxMcRelativeErrorAllowed);
            }
        }
    }

    /**
     * Complete port of official {@code testImpliedVolatility}.
     *
     * <p>Solves for each of the four barriers without dividends and reprices
     * with {@link AnalyticBarrierEngine}, then solves with the discrete
     * dividend schedule and reprices with {@link FdBlackScholesBarrierEngine}
     * to absolute tolerance {@code 1.0e-5}.
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

        double divAmount = 10;
        Date divDate = today.add(new Period(6, TimeUnit.MONTHS));
        List<Dividend> dividends = Dividend.dividendVector(
                Collections.singletonList(divDate),
                new double[]{divAmount});

        double strike = 105.0;
        double rebate = 5.0;

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);
        Exercise exercise = new EuropeanExercise(maturity);

        double[] barriers = {90.0, 110.0, 90.0, 110.0};
        Barrier.Type[] barrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.UpOut, Barrier.Type.DownIn, Barrier.Type.UpIn};
        double[] targetWithoutDividends = {1.0, 1.0, 5.0, 5.0};
        double[] targetWithDividends = {8.0, 12.0, 9.0, 8.0};
        double tolerance = 1e-5;

        for (int i = 0; i < barriers.length; ++i) {
            double barrier = barriers[i];
            Barrier.Type barrierType = barrierTypes[i];

            BarrierOption barrierOption = new BarrierOption(barrierType,
                    barrier, rebate, payoff, exercise);
            double impliedVol = barrierOption.impliedVolatility(
                    targetWithoutDividends[i], bsProcess, 1e-6);

            RelinkableHandle<BlackVolTermStructure> volTS =
                    new RelinkableHandle<>(
                            flatVol(today, impliedVol, dc), true);
            BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    s0, qTS, rTS, volTS);
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

            impliedVol = barrierOption.impliedVolatility(
                    targetWithDividends[i], bsProcess, dividends, 1e-6);

            volTS.linkTo(flatVol(today, impliedVol, dc), true);
            barrierOption.setPricingEngine(
                    new FdBlackScholesBarrierEngine(process, dividends));

            diff = Math.abs(barrierOption.NPV()
                    - targetWithDividends[i]);
            if (diff > tolerance) {
                fail("Failed to reproduce target option price:"
                        + "\n    strike:       " + strike
                        + "\n    barrier:      " + barrier
                        + "\n    maturity:     " + maturity
                        + "\n    target value: " + targetWithDividends[i]
                        + "\n    calculated:   " + barrierOption.NPV()
                        + "\n    difference    " + diff);
            }
        }
    }

    @Test
    public void testLocalVolAndHestonComparison() {
        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Target calendar = new Target();

        int[] t = {13, 41, 75, 165, 256, 345, 524, 703};
        double[] r = {0.0357, 0.0349, 0.0341, 0.0355,
                0.0359, 0.0368, 0.0386, 0.0401};

        List<Double> rates = new ArrayList<>();
        rates.add(0.0357);
        List<Date> dates = new ArrayList<>();
        dates.add(settlementDate);
        for (int i = 0; i < 8; ++i) {
            dates.add(settlementDate.add(t[i]));
            rates.add(r[i]);
        }
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(settlementDate, 0.0, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(4500.00), true);

        List<Double> strikes = Arrays.asList(100.0, 500.0, 2000.0, 3400.0,
                3600.0, 3800.0, 4000.0, 4200.0, 4400.0, 4500.0,
                4600.0, 4800.0, 5000.0, 5200.0, 5400.0, 5600.0, 7500.0,
                10000.0, 20000.0, 30000.0);

        double[] v = {
                1.015873, 1.015873, 1.015873, 0.89729, 0.796493, 0.730914, 0.631335, 0.568895,
                0.711309, 0.711309, 0.711309, 0.641309, 0.635593, 0.583653, 0.508045, 0.463182,
                0.516034, 0.500534, 0.500534, 0.500534, 0.448706, 0.416661, 0.375470, 0.353442,
                0.516034, 0.482263, 0.447713, 0.387703, 0.355064, 0.337438, 0.316966, 0.306859,
                0.497587, 0.464373, 0.430764, 0.374052, 0.344336, 0.328607, 0.310619, 0.301865,
                0.479511, 0.446815, 0.414194, 0.361010, 0.334204, 0.320301, 0.304664, 0.297180,
                0.461866, 0.429645, 0.398092, 0.348638, 0.324680, 0.312512, 0.299082, 0.292785,
                0.444801, 0.413014, 0.382634, 0.337026, 0.315788, 0.305239, 0.293855, 0.288660,
                0.428604, 0.397219, 0.368109, 0.326282, 0.307555, 0.298483, 0.288972, 0.284791,
                0.420971, 0.389782, 0.361317, 0.321274, 0.303697, 0.295302, 0.286655, 0.282948,
                0.413749, 0.382754, 0.354917, 0.316532, 0.300016, 0.292251, 0.284420, 0.281164,
                0.400889, 0.370272, 0.343525, 0.307904, 0.293204, 0.286549, 0.280189, 0.277767,
                0.390685, 0.360399, 0.334344, 0.300507, 0.287149, 0.281380, 0.276271, 0.274588,
                0.383477, 0.353434, 0.327580, 0.294408, 0.281867, 0.276746, 0.272655, 0.271617,
                0.379106, 0.349214, 0.323160, 0.289618, 0.277362, 0.272641, 0.269332, 0.268846,
                0.377073, 0.347258, 0.320776, 0.286077, 0.273617, 0.269057, 0.266293, 0.266265,
                0.399925, 0.369232, 0.338895, 0.289042, 0.265509, 0.255589, 0.249308, 0.249665,
                0.423432, 0.406891, 0.373720, 0.314667, 0.281009, 0.263281, 0.246451, 0.242166,
                0.453704, 0.453704, 0.453704, 0.381255, 0.334578, 0.305527, 0.268909, 0.251367,
                0.517748, 0.517748, 0.517748, 0.416577, 0.364770, 0.331595, 0.287423, 0.264285
        };

        Matrix blackVolMatrix = new Matrix(strikes.size(), dates.size() - 1);
        for (int i = 0; i < strikes.size(); ++i) {
            for (int j = 1; j < dates.size(); ++j) {
                blackVolMatrix.set(i, j - 1, v[i * (dates.size() - 1) + j - 1]);
            }
        }

        BlackVarianceSurface volTS = new BlackVarianceSurface(
                settlementDate, calendar,
                new ArrayList<>(dates.subList(1, dates.size())),
                strikes, blackVolMatrix, dayCounter);
        volTS.setInterpolation(new Bicubic());
        GeneralizedBlackScholesProcess localVolProcess =
                new BlackScholesMertonProcess(
                        s0, qTS, rTS, new Handle<>(volTS, true));

        double v0 = 0.195662;
        double kappa = 5.6628;
        double theta = 0.0745911;
        double sigma = 1.1619;
        double rho = -0.511493;

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, v0, kappa, theta, sigma, rho);
        HestonModel hestonModel = new HestonModel(hestonProcess);

        PricingEngine fdHestonEngine =
                new FdHestonBarrierEngine(hestonModel, 100, 400, 50);
        PricingEngine fdLocalVolEngine =
                new FdBlackScholesBarrierEngine(localVolProcess,
                        100, 400, 0, FdmSchemeDesc.Douglas(),
                        true, 0.35);

        double strike = s0.getValue().value();
        double barrier = 3000;
        double rebate = 100;
        Date exDate = settlementDate.add(new Period(20, TimeUnit.MONTHS));

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);
        Exercise exercise = new EuropeanExercise(exDate);

        BarrierOption barrierOption = new BarrierOption(
                Barrier.Type.DownOut, barrier, rebate, payoff, exercise);

        barrierOption.setPricingEngine(fdHestonEngine);
        double expectedHestonNPV = 111.5;
        double calculatedHestonNPV = barrierOption.NPV();

        barrierOption.setPricingEngine(fdLocalVolEngine);
        double expectedLocalVolNPV = 132.8;
        double calculatedLocalVolNPV = barrierOption.NPV();

        double tol = 0.01;

        if (Math.abs(expectedHestonNPV - calculatedHestonNPV)
                > tol * expectedHestonNPV) {
            fail("Failed to reproduce Heston barrier price for "
                    + "\n    strike:     " + payoff.strike()
                    + "\n    barrier:    " + barrier
                    + "\n    maturity:   " + exDate
                    + "\n    calculated: " + calculatedHestonNPV
                    + "\n    expected:   " + expectedHestonNPV);
        }
        if (Math.abs(expectedLocalVolNPV - calculatedLocalVolNPV)
                > tol * expectedLocalVolNPV) {
            fail("Failed to reproduce Heston barrier price for "
                    + "\n    strike:     " + payoff.strike()
                    + "\n    barrier:    " + barrier
                    + "\n    maturity:   " + exDate
                    + "\n    calculated: " + calculatedLocalVolNPV
                    + "\n    expected:   " + expectedLocalVolNPV);
        }
    }

    @Test
    public void testDividendBarrierOption() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(11, Month.FEBRUARY, 2018);
        Date maturity = today.add(new Period(1, TimeUnit.YEARS));
        Settings.instance.evaluationDate().equal(today);

        double spot = 100.0;
        double strike = 105.0;
        double rebate = 5.0;

        double[] barriers = {80.0, 120.0, 80.0, 120.0};
        Barrier.Type[] barrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.UpOut, Barrier.Type.DownIn, Barrier.Type.UpIn};

        double r = 0.05;
        double q = 0.0;
        double v = 0.02;

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(spot), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, q, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, r, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, v, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);

        double divAmount = 30;
        Date divDate = today.add(new Period(6, TimeUnit.MONTHS));
        List<Dividend> dividends = Dividend.dividendVector(
                Collections.singletonList(divDate),
                new double[]{divAmount});

        PricingEngine douglas = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0, FdmSchemeDesc.Douglas());
        PricingEngine crankNicolson = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0,
                FdmSchemeDesc.CrankNicolson());
        PricingEngine craigSnyed = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0, FdmSchemeDesc.CraigSneyd());
        PricingEngine hundsdorfer = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0,
                FdmSchemeDesc.Hundsdorfer());
        PricingEngine mol = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0,
                FdmSchemeDesc.MethodOfLines());
        PricingEngine trPDF2 = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 100, 100, 0, FdmSchemeDesc.TrBDF2());

        PricingEngine hestonEngine = new FdHestonBarrierEngine(
                new HestonModel(new HestonProcess(
                        rTS, qTS, s0, v * v, 1.0, v * v, 0.005, 0.0)),
                dividends, 50, 101, 3);

        PricingEngine[] engines = {
                douglas, crankNicolson,
                trPDF2, craigSnyed, hundsdorfer, mol, hestonEngine
        };

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);
        Exercise exercise = new EuropeanExercise(maturity);

        YieldTermStructure rTSValue = rTS.getValue();
        double[] expected = {
                rTSValue.discount(divDate) * rebate,
                payoff.value((spot - divAmount * rTSValue.discount(divDate))
                        / rTSValue.discount(maturity))
                        * rTSValue.discount(maturity),
                29.154,
                4.765
        };

        double relTol = 2e-4;
        for (int i = 0; i < barriers.length; ++i) {
            for (int j = 0; j < engines.length; ++j) {
                double barrier = barriers[i];
                Barrier.Type barrierType = barrierTypes[i];

                BarrierOption barrierOption = new BarrierOption(
                        barrierType, barrier, rebate, payoff, exercise);
                barrierOption.setPricingEngine(engines[j]);

                double calculated = barrierOption.NPV();
                double diff = Math.abs(calculated - expected[i]);
                if (diff > relTol * expected[i]) {
                    fail("Failed to reproduce barrier price with discrete "
                            + "dividends:"
                            + "\n    engine:     " + j
                            + "\n    strike:     " + strike
                            + "\n    barrier:    " + barrier
                            + "\n    maturity:   " + maturity
                            + "\n    calculated: " + calculated
                            + "\n    expected:   " + expected[i]
                            + "\n    difference  " + diff
                            + "\n    tolerance   " + relTol * expected[i]);
                }
            }
        }
    }

    @Test
    public void testDividendBarrierOptionWithDividendsPastMaturity() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(11, Month.FEBRUARY, 2018);
        Date maturity = today.add(new Period(1, TimeUnit.YEARS));
        Settings.instance.evaluationDate().equal(today);

        double spot = 100.0;
        double strike = 105.0;
        double rebate = 5.0;

        double[] barriers = {90.0, 110.0};
        Barrier.Type[] barrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.UpOut};

        double r = 0.05;
        double q = 0.0;
        double v = 0.02;

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(spot), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, q, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, r, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, v, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);

        double divAmount = 30;
        Date divDate = today.add(new Period(18, TimeUnit.MONTHS));
        List<Dividend> dividends = Dividend.dividendVector(
                Collections.singletonList(divDate),
                new double[]{divAmount});

        PricingEngine[] engines = {
                new FdBlackScholesBarrierEngine(
                        bsProcess, 100, 100, 0, FdmSchemeDesc.Douglas()),
                new FdHestonBarrierEngine(
                        new HestonModel(new HestonProcess(
                                rTS, qTS, s0, v * v, 1.0, v * v, 0.005, 0.0)),
                        50, 101, 3)
        };

        PricingEngine[] enginesWithDividends = {
                new FdBlackScholesBarrierEngine(
                        bsProcess, dividends, 100, 100, 0,
                        FdmSchemeDesc.Douglas()),
                new FdHestonBarrierEngine(
                        new HestonModel(new HestonProcess(
                                rTS, qTS, s0, v * v, 1.0, v * v, 0.005, 0.0)),
                        dividends, 50, 101, 3)
        };

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);
        Exercise exercise = new EuropeanExercise(maturity);

        for (int i = 0; i < barriers.length; ++i) {
            for (int j = 0; j < engines.length; ++j) {
                double barrier = barriers[i];
                Barrier.Type barrierType = barrierTypes[i];

                BarrierOption barrierOption = new BarrierOption(
                        barrierType, barrier, rebate, payoff, exercise);

                barrierOption.setPricingEngine(engines[j]);
                double withoutDividends = barrierOption.NPV();

                barrierOption.setPricingEngine(enginesWithDividends[j]);
                double withDividends = barrierOption.NPV();

                double diff = Math.abs(withDividends - withoutDividends);
                double tolerance = 1e-12;
                if (diff > tolerance) {
                    fail("Dividends past maturity affected option price:"
                            + "\n    engine:           " + j
                            + "\n    strike:           " + strike
                            + "\n    barrier:          " + barrier
                            + "\n    maturity:         " + maturity
                            + "\n    without dividend: " + withoutDividends
                            + "\n    with dividend:    " + withDividends
                            + "\n    difference        " + diff);
                }
            }
        }
    }

    /**
     * Complete port of official {@code testFdAmericanKnockOut}.
     *
     * <p>Six American knock-out rows are checked to relative tolerance
     * {@code 1e-3}. The four rows whose official expected values are
     * {@code Null} are compared against the Derman-Kani binomial reference
     * (1000 steps, Boyle-Lau adjustment enabled); the last two use the
     * stored converged values {@code 8.0305} and {@code 9.7605}.
     */
    @Test
    public void testFdAmericanKnockOut() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(1, Month.JANUARY, 2025);
        Date maturity = today.add(365);
        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.02, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.25, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);

        PricingEngine fdEngine =
                new FdBlackScholesBarrierEngine(bsProcess, 200, 400);
        PricingEngine binomialEngine = new BinomialBarrierEngine(bsProcess,
                1000, 0, BinomialBarrierEngine.Discretization.DERMAN_KANI);

        Exercise americanExercise = new AmericanExercise(today, maturity);

        Barrier.Type[] barrierTypes = {
                Barrier.Type.DownOut, Barrier.Type.UpOut,
                Barrier.Type.DownOut, Barrier.Type.UpOut,
                Barrier.Type.DownOut, Barrier.Type.UpOut};
        Option.Type[] optionTypes = {
                Option.Type.Call, Option.Type.Put,
                Option.Type.Call, Option.Type.Put,
                Option.Type.Put, Option.Type.Call};
        double[] barriers = {85.0, 120.0, 85.0, 120.0, 85.0, 120.0};
        double[] rebates = {0.0, 0.0, 5.0, 5.0, 0.0, 0.0};
        // Null values are taken from the binomial reference, as official.
        Double[] expectedValues = {null, null, null, null, 8.0305, 9.7605};

        double relTol = 1e-3;
        for (int i = 0; i < barrierTypes.length; ++i) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(optionTypes[i], 100.0);
            BarrierOption option = new BarrierOption(barrierTypes[i],
                    barriers[i], rebates[i], payoff, americanExercise);

            double expected;
            if (expectedValues[i] == null) {
                option.setPricingEngine(binomialEngine);
                expected = option.NPV();
            } else {
                expected = expectedValues[i];
            }

            option.setPricingEngine(fdEngine);
            double calculated = option.NPV();

            if (Math.abs(calculated - expected) > relTol * expected) {
                fail("Failed to reproduce American knock-out barrier price:"
                        + "\n    barrier type: " + Barrier.toString(barrierTypes[i])
                        + "\n    option type:  " + optionTypes[i]
                        + "\n    barrier:      " + barriers[i]
                        + "\n    rebate:       " + rebates[i]
                        + "\n    calculated:   " + calculated
                        + "\n    expected:     " + expected
                        + "\n    difference    "
                        + Math.abs(calculated - expected)
                        + "\n    tolerance     " + relTol * expected);
            }
        }
    }

    /**
     * Complete port of official {@code testBinomialExerciseAtMaturity}.
     *
     * <p>All stock nodes lie between the two barriers, so neither can knock
     * out and the barrier lattice must reproduce the vanilla binomial price
     * for both European and American exercise. The Boyle-Lau adjustment is
     * disabled so that the tree matches the vanilla reference exactly.
     */
    @Test
    public void testBinomialExerciseAtMaturity() {
        Date today = new Date(21, Month.SEPTEMBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();
        Date maturity = today.add(91);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.0, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.15, dc), true);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                spot, qTS, rTS, volTS);

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 85.0);
        Exercise europeanExercise = new EuropeanExercise(maturity);
        Exercise americanExercise = new AmericanExercise(today, maturity);

        int timeSteps = 51;
        // Disable Boyle-Lau adjustment to keep the same tree as the vanilla
        // reference.
        BinomialBarrierEngine engine =
                new BinomialBarrierEngine(process, timeSteps, timeSteps);

        EuropeanOption vanilla = new EuropeanOption(payoff, europeanExercise);
        vanilla.setPricingEngine(new BinomialVanillaEngine(process, timeSteps,
                BinomialVanillaEngine.TreeType.COX_ROSS_RUBINSTEIN));
        double expected = vanilla.NPV();
        double tolerance = 1.0e-10;

        Barrier.Type[] types = {Barrier.Type.DownOut, Barrier.Type.UpOut};
        for (Barrier.Type type : types) {
            double barrier = type == Barrier.Type.DownOut ? 1.0 : 1000.0;
            BarrierOption european = new BarrierOption(type, barrier, 0.0,
                    payoff, europeanExercise);
            BarrierOption american = new BarrierOption(type, barrier, 0.0,
                    payoff, americanExercise);
            european.setPricingEngine(engine);
            american.setPricingEngine(engine);

            double europeanValue = european.NPV();
            double americanValue = american.NPV();

            if (Math.abs(europeanValue - expected) > tolerance) {
                fail(type + ": European barrier value " + europeanValue
                        + " differs from vanilla value " + expected);
            }
            if (americanValue < expected - tolerance) {
                fail(type + ": American barrier value " + americanValue
                        + " is below European vanilla value " + expected);
            }
            if (Math.abs(americanValue - expected) > tolerance) {
                fail(type + ": non-dividend-paying American barrier value "
                        + americanValue + " differs from vanilla value "
                        + expected);
            }
        }
    }

    /**
     * Official helper {@code perturbativeBarrierOptionValue}.
     */
    private static double perturbativeBarrierOptionValue(int order,
                                                        int npoint,
                                                        int npoint2) {
        double s = 100.0;
        double rebate = 0.0;
        double r = 0.03;
        double q = 0.02;

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote underlying = new SimpleQuote(s);
        YieldTermStructure qTS = flatRate(today, q, dc);
        YieldTermStructure rTS = flatRate(today, r, dc);

        List<Date> dates = Arrays.asList(today.add(90), today.add(180));
        List<Double> vols = Arrays.asList(0.105, 0.11);

        BlackVolTermStructure volTS =
                new BlackVarianceCurve(today, dates, vols, dc, true);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(underlying, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        double strike = 101.0;
        double barrier = 101.0;
        Date exDate = today.add(180);

        Exercise exercise = new EuropeanExercise(exDate);
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, strike);

        BarrierOption option = new BarrierOption(Barrier.Type.UpOut, barrier,
                rebate, payoff, exercise);

        boolean zeroGamma = false;
        option.setPricingEngine(new PerturbativeBarrierOptionEngine(
                stochProcess, order, zeroGamma, npoint, npoint2));

        return option.NPV();
    }

    /** Official {@code testPerturbativeOrder0}. */
    @Test
    public void testPerturbativeOrder0() {
        double value = perturbativeBarrierOptionValue(0, 1000, 100);
        double error = Math.abs(value - 0.897365);
        if (error > 1.0e-6) {
            fail("perturbative barrier engine, order 0:"
                    + "\n    calculated: " + value
                    + "\n    expected:   0.897365"
                    + "\n    error:      " + error
                    + "\n    tolerance:  1.0e-6");
        }
    }

    /** Official {@code testPerturbativeOrder1}. */
    @Test
    public void testPerturbativeOrder1() {
        double value = perturbativeBarrierOptionValue(1, 1000, 100);
        double error = Math.abs(value - 0.894374);
        if (error > 1.0e-6) {
            fail("perturbative barrier engine, order 1:"
                    + "\n    calculated: " + value
                    + "\n    expected:   0.894374"
                    + "\n    error:      " + error
                    + "\n    tolerance:  1.0e-6");
        }
    }

    /** Official {@code testPerturbativeOrder2Fast}. */
    @Test
    public void testPerturbativeOrder2Fast() {
        double value = perturbativeBarrierOptionValue(2, 100, 10);
        double error = Math.abs(value - 0.8943754);
        if (error > 1.0e-6) {
            fail("perturbative barrier engine, fast order 2:"
                    + "\n    calculated: " + value
                    + "\n    expected:   0.8943754"
                    + "\n    error:      " + error
                    + "\n    tolerance:  1.0e-6");
        }
    }

    /** Official {@code testPerturbativeOrder2FullResolution}. */
    @Test
    public void testPerturbativeOrder2FullResolution() {
        double value = perturbativeBarrierOptionValue(2, 1000, 100);
        double error = Math.abs(value - 0.8943754);
        if (error > 1.0e-6) {
            fail("perturbative barrier engine, full-resolution order 2:"
                    + "\n    calculated: " + value
                    + "\n    expected:   0.8943754"
                    + "\n    error:      " + error
                    + "\n    tolerance:  1.0e-6");
        }
    }

    /** Official {@code BarrierFxOptionData}. */
    private static class BarrierFxOptionData {
        final Barrier.Type barrierType;
        final double barrier;
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

        BarrierFxOptionData(Barrier.Type barrierType, double barrier,
                            double rebate, Option.Type type, double strike,
                            double s, double q, double r, double t,
                            double vol25Put, double volAtm, double vol25Call,
                            double v, double result, double tol) {
            this.barrierType = barrierType;
            this.barrier = barrier;
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

    private static final BarrierFxOptionData[] VANNA_VOLGA_VALUES = {

            //barrierType,barrier,rebate,type,strike,s,q,r,t,vol25Put,volAtm,vol25Call,vol, result, tol
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.148127, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.075943, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0274771, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00573, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00012, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00697606, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.020078, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0489395, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.0969877, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.5, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.157, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.0322202, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.0241491, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0164275, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00489, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.000560713, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.000546804, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.000130649, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.000300828, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.5, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00135, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.17746, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.0994142, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.0439, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01574, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00501, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00612, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00426, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.00257, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00122, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00045, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00022, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00284, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.02032, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.058235, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.1, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.109432, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.00017, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00083, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00289, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.00067784, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.17423, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.09584, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.04133, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.01452, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.00456, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00732, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.01778, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.02875, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.0390535, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.1, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.0489236, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.13321, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.11638, 0.00753, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.22687, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.10088, 0.02062, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.31179, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08925, 0.04907, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.38843, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08463, 0.09711, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.46047, 1.30265, 0.0003541, 0.0033871, 1, 0.10087, 0.08925, 0.08463, 0.08412, 0.15752, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.20493, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.105577, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0358872, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00634958, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.0108218, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0313339, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0751237, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.153407, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpOut, 1.6, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.253767, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.05402, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0410069, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0279562, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0173055, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00764, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.000962737, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00102637, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.000419834, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00159277, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.UpIn, 1.6, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00473629, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.255098, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.145701, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.06384, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.02366, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00764, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00592, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00421, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.00256, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0012, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.0004, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00280549, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.0279945, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0896352, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.175182, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00000, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.00000, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.00000, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0002, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownOut, 1.3, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00096, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.00384783, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.000883232, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.00000, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00000, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.25302, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.14238, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.06128, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.02245, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Call, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.00725, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.01178, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.0295548, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.047549, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.0653642, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.0833221, 1.0e-4),

            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.06145, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.12511, 0.01178, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.19545, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.1089, 0.03236, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.32238, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09444, 0.07554, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.44298, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09197, 0.15479, 1.0e-4),
            new BarrierFxOptionData(Barrier.Type.DownIn, 1.3, 0, Option.Type.Put, 1.56345, 1.30265, 0.0009418, 0.0039788, 2, 0.10891, 0.09525, 0.09197, 0.09261, 0.25754, 1.0e-4)
    };

    /**
     * Complete port of official {@code testVannaVolgaSimpleBarrierValues}.
     *
     * <p>The 120 official FX barrier rows are priced with
     * {@link VannaVolgaBarrierEngine} to absolute tolerance {@code 1.0e-4}.
     */
    @Test
    public void testVannaVolgaSimpleBarrierValues() {
        DayCounter dc = new Actual365Fixed();
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

        for (BarrierFxOptionData value : VANNA_VOLGA_VALUES) {

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
                    new DeltaVolQuote(new Handle<Quote>(volAtm, true),
                            DeltaVolQuote.DeltaType.Fwd, value.t,
                            DeltaVolQuote.AtmType.AtmDeltaNeutral), true);

            Handle<DeltaVolQuote> vol25PutQuote = new Handle<>(
                    new DeltaVolQuote(-0.25, new Handle<Quote>(vol25Put, true),
                            value.t, DeltaVolQuote.DeltaType.Fwd), true);

            Handle<DeltaVolQuote> vol25CallQuote = new Handle<>(
                    new DeltaVolQuote(0.25, new Handle<Quote>(vol25Call, true),
                            value.t, DeltaVolQuote.DeltaType.Fwd), true);

            BarrierOption barrierOption = new BarrierOption(value.barrierType,
                    value.barrier, value.rebate, payoff, exercise);

            double bsVanillaPrice = BlackFormula.blackFormula(value.type,
                    value.strike,
                    spot.value() * qTS.discount(value.t) / rTS.discount(value.t),
                    value.v * Math.sqrt(value.t),
                    rTS.discount(value.t), 0.0);

            PricingEngine vannaVolgaEngine = new VannaVolgaBarrierEngine(
                    volAtmQuote, vol25PutQuote, vol25CallQuote,
                    new Handle<Quote>(spot, true),
                    new Handle<>(rTS, true),
                    new Handle<>(qTS, true),
                    true, bsVanillaPrice);

            barrierOption.setPricingEngine(vannaVolgaEngine);

            double calculated = barrierOption.NPV();
            double expected = value.result;
            double error = Math.abs(calculated - expected);
            if (error > value.tol) {
                reportFxFailure("value", value, payoff, exercise, spot.value(),
                        today, expected, calculated, error);
            }
        }
    }

    @Test
    public void testFdAmericanKnockOutGaoHuangSubrahmanyam() {
        double[][] values = {
                //    s,    v,    t,  value,   delta
                {40.0, 0.20, 0.25, 5.0357, -0.9253},
                {45.0, 0.20, 0.50, 1.9375, -0.4893},
                {49.5, 0.20, 0.50, 0.1613, -0.3270},
                {45.0, 0.30, 0.75, 2.7668, -0.5959},
                {45.0, 0.40, 1.00, 3.2145, -0.6672},
                {49.5, 0.40, 1.00, 0.3117, -0.6253}
        };

        DayCounter dc = new Actual360();
        Date today = new Date(1, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.0, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.0488, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);
        PricingEngine engine =
                new FdBlackScholesBarrierEngine(bsProcess, 200, 400);
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 45.0);

        double valueTol = 2e-3;
        double deltaTol = 1e-3;
        for (double[] value : values) {
            spot.setValue(value[0]);
            vol.setValue(value[1]);

            BarrierOption option = new BarrierOption(Barrier.Type.UpOut,
                    50.0, 0.0, payoff,
                    new AmericanExercise(today,
                            today.add(timeToDays(value[2]))));
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double delta = option.delta();
            if (Math.abs(calculated - value[3]) > valueTol
                    || Math.abs(delta - value[4]) > deltaTol) {
                fail("Failed to reproduce American up-and-out put:"
                        + "\n    spot:       " + value[0]
                        + "\n    volatility: " + value[1]
                        + "\n    maturity:   " + value[2]
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + value[3]
                        + "\n    tolerance:  " + valueTol
                        + "\n    delta:      " + delta
                        + "\n    expected:   " + value[4]
                        + "\n    tolerance:  " + deltaTol);
            }
        }
    }

    @Test
    public void testFdEarlyExerciseKnockOutBounds() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(1, Month.JANUARY, 2025);
        Date maturity = today.add(365);
        Settings.instance.evaluationDate().equal(today);

        double s0 = 100.0;
        SimpleQuote spot = new SimpleQuote(s0);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.02, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.25, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);

        PricingEngine barrierEngine =
                new FdBlackScholesBarrierEngine(bsProcess, 200, 400);
        PricingEngine vanillaEngine =
                new FdBlackScholesVanillaEngine(bsProcess, 200, 400);
        PricingEngine qdFpEngine = new QdFpAmericanEngine(bsProcess);

        Exercise europeanExercise = new EuropeanExercise(maturity);
        Exercise americanExercise = new AmericanExercise(today, maturity);
        List<Date> exerciseDates = new ArrayList<>();
        for (int m = 1; m <= 12; ++m)
            exerciseDates.add(today.add(new Period(m, TimeUnit.MONTHS)));
        Exercise bermudanExercise = new BermudanExercise(exerciseDates);

        Barrier.Type[] barrierTypes = {
                Barrier.Type.DownOut, Barrier.Type.DownOut,
                Barrier.Type.UpOut, Barrier.Type.UpOut,
                Barrier.Type.DownOut, Barrier.Type.UpOut};
        Option.Type[] optionTypes = {
                Option.Type.Call, Option.Type.Put,
                Option.Type.Call, Option.Type.Put,
                Option.Type.Put, Option.Type.Call};
        double[] barriers = {85.0, 85.0, 120.0, 120.0, 20.0, 500.0};
        double[] rebates = {0.0, 0.0, 0.0, 5.0, 0.0, 0.0};
        boolean[] farAway = {false, false, false, false, true, true};

        double tol = 1e-6;
        for (int c = 0; c < barrierTypes.length; ++c) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(optionTypes[c], 100.0);

            double european = barrierNpv(payoff, barrierTypes[c],
                    barriers[c], rebates[c], europeanExercise, barrierEngine);
            double bermudan = barrierNpv(payoff, barrierTypes[c],
                    barriers[c], rebates[c], bermudanExercise, barrierEngine);
            double american = barrierNpv(payoff, barrierTypes[c],
                    barriers[c], rebates[c], americanExercise, barrierEngine);

            EuropeanOption vanilla =
                    new EuropeanOption(payoff, americanExercise);
            vanilla.setPricingEngine(vanillaEngine);
            double americanVanilla = vanilla.NPV();

            // the knock-out pays either what the vanilla option could
            // pay or the rebate, which is worth at most its face value
            // with non-negative rates
            if (european > bermudan + tol || bermudan > american + tol
                    || american > americanVanilla + rebates[c] + tol) {
                fail("Failed to bound early-exercise knock-out barrier price:"
                        + "\n    barrier type:     "
                        + Barrier.toString(barrierTypes[c])
                        + "\n    option type:      " + optionTypes[c]
                        + "\n    barrier:          " + barriers[c]
                        + "\n    rebate:           " + rebates[c]
                        + "\n    european:         " + european
                        + "\n    bermudan:         " + bermudan
                        + "\n    american:         " + american
                        + "\n    american vanilla: " + americanVanilla);
            }

            if (farAway[c]) {
                // with the barrier never reached, neither is the rebate;
                // the QdFp engine gives an independent, highly accurate
                // reference
                vanilla.setPricingEngine(qdFpEngine);
                double qdFpVanilla = vanilla.NPV();
                if (Math.abs(american - americanVanilla)
                        > 1e-3 * americanVanilla
                        || Math.abs(american - qdFpVanilla)
                        > 5e-4 * qdFpVanilla) {
                    fail("Failed to reproduce American vanilla price with "
                            + "a far barrier:"
                            + "\n    barrier type:          "
                            + Barrier.toString(barrierTypes[c])
                            + "\n    option type:           " + optionTypes[c]
                            + "\n    barrier:               " + barriers[c]
                            + "\n    american:              " + american
                            + "\n    american vanilla (FD): " + americanVanilla
                            + "\n    american vanilla (QD+): " + qdFpVanilla);
                }

                EuropeanOption bermudanVanilla =
                        new EuropeanOption(payoff, bermudanExercise);
                bermudanVanilla.setPricingEngine(vanillaEngine);
                double expected = bermudanVanilla.NPV();
                if (Math.abs(bermudan - expected) > 1e-3 * expected) {
                    fail("Failed to reproduce Bermudan vanilla price with "
                            + "a far barrier:"
                            + "\n    barrier type:      "
                            + Barrier.toString(barrierTypes[c])
                            + "\n    option type:       " + optionTypes[c]
                            + "\n    barrier:           " + barriers[c]
                            + "\n    bermudan:          " + bermudan
                            + "\n    bermudan vanilla:  " + expected);
                }
                continue;
            }

            // the american price never falls below the exercise value
            BarrierOption option = new BarrierOption(barrierTypes[c],
                    barriers[c], rebates[c], payoff, americanExercise);
            option.setPricingEngine(barrierEngine);
            boolean down = barrierTypes[c] == Barrier.Type.DownOut;
            for (double s = down ? barriers[c] + 0.5 : 50.0;
                 s < (down ? 160.0 : barriers[c]); s += 5.0) {
                spot.setValue(s);
                double calculated = option.NPV();
                if (calculated < payoff.value(s)) {
                    fail("American knock-out price below exercise value:"
                            + "\n    barrier type: "
                            + Barrier.toString(barrierTypes[c])
                            + "\n    option type:  " + optionTypes[c]
                            + "\n    barrier:      " + barriers[c]
                            + "\n    rebate:       " + rebates[c]
                            + "\n    spot:         " + s
                            + "\n    american:     " + calculated
                            + "\n    exercise:     " + payoff.value(s));
                }
            }
            spot.setValue(s0);
        }

        // in-out parity does not hold with early exercise
        BarrierOption knockIn = new BarrierOption(Barrier.Type.DownIn,
                85.0, 0.0,
                new PlainVanillaPayoff(Option.Type.Call, 100.0),
                americanExercise);
        knockIn.setPricingEngine(barrierEngine);
        expectException(knockIn::NPV);

        // spot on the barrier: when the intrinsic value exceeds the rebate
        // the value jumps there, so only the rebate is returned
        double rebate = 3.0;
        BarrierOption jumpOption = new BarrierOption(Barrier.Type.DownOut,
                100.0, rebate,
                new PlainVanillaPayoff(Option.Type.Call, 90.0),
                americanExercise);
        jumpOption.setPricingEngine(barrierEngine);
        checkClose("jump option value", jumpOption.NPV(), rebate, 1e-10);
        expectException(jumpOption::delta);

        // the same holds for a Bermudan option exercisable today...
        List<Date> datesFromToday = new ArrayList<>(exerciseDates);
        datesFromToday.add(0, today);
        BarrierOption bermudanJump = new BarrierOption(Barrier.Type.DownOut,
                100.0, rebate,
                new PlainVanillaPayoff(Option.Type.Call, 90.0),
                new BermudanExercise(datesFromToday));
        bermudanJump.setPricingEngine(barrierEngine);
        checkClose("bermudan jump value", bermudanJump.NPV(), rebate, 1e-10);
        expectException(bermudanJump::delta);

        // ...but not for one that isn't exercisable today
        BarrierOption bermudanCall = new BarrierOption(Barrier.Type.DownOut,
                100.0, rebate,
                new PlainVanillaPayoff(Option.Type.Call, 90.0),
                bermudanExercise);
        bermudanCall.setPricingEngine(barrierEngine);
        checkClose("bermudan call value", bermudanCall.NPV(), rebate, 1e-10);
        bermudanCall.delta();

        // otherwise the value is continuous at the barrier and the greeks
        // are provided
        BarrierOption americanCall = new BarrierOption(Barrier.Type.DownOut,
                100.0, rebate,
                new PlainVanillaPayoff(Option.Type.Call, 100.0),
                americanExercise);
        americanCall.setPricingEngine(barrierEngine);
        checkClose("american call value", americanCall.NPV(), rebate, 1e-10);
        americanCall.delta();
        americanCall.gamma();
        americanCall.theta();
    }

    private static double barrierNpv(PlainVanillaPayoff payoff,
                                     Barrier.Type barrierType,
                                     double barrier, double rebate,
                                     Exercise exercise,
                                     PricingEngine engine) {
        BarrierOption option = new BarrierOption(barrierType, barrier,
                rebate, payoff, exercise);
        option.setPricingEngine(engine);
        return option.NPV();
    }

    @Test
    public void testFdAmericanDownOutCallWithoutDividends() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(1, Month.JANUARY, 2025);
        Date maturity = today.add(365);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100.0);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.0, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.25, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);
        PricingEngine engine =
                new FdBlackScholesBarrierEngine(bsProcess, 200, 400);

        Exercise europeanExercise = new EuropeanExercise(maturity);
        Exercise americanExercise = new AmericanExercise(today, maturity);

        double tol = 1e-6;
        for (double strike : new double[]{100.0, 110.0}) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, strike);
            for (double barrier : new double[]{70.0, 85.0, 95.0, 100.0}) {
                BarrierOption american = new BarrierOption(
                        Barrier.Type.DownOut, barrier, 0.0, payoff,
                        americanExercise);
                BarrierOption european = new BarrierOption(
                        Barrier.Type.DownOut, barrier, 0.0, payoff,
                        europeanExercise);
                american.setPricingEngine(engine);
                european.setPricingEngine(engine);

                for (double s : new double[]{barrier + 1.0, 100.0,
                        130.0, 180.0}) {
                    spot.setValue(s);
                    double diff = american.NPV() - european.NPV();
                    if (Math.abs(diff) > tol) {
                        fail("Failed to reproduce european price with "
                                + "american down-and-out call without "
                                + "dividends:"
                                + "\n    strike:     " + strike
                                + "\n    barrier:    " + barrier
                                + "\n    spot:       " + s
                                + "\n    american:   " + american.NPV()
                                + "\n    european:   " + european.NPV()
                                + "\n    difference: " + diff
                                + "\n    tolerance:  " + tol);
                    }
                }
            }
        }
    }

    @Test
    public void testFdAmericanKnockOutWithDividends() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(1, Month.JANUARY, 2025);
        Date maturity = today.add(365);
        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.0, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.25, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);

        List<Dividend> dividends = Dividend.dividendVector(
                Arrays.asList(today.add(new Period(3, TimeUnit.MONTHS)),
                        today.add(new Period(9, TimeUnit.MONTHS))),
                new double[]{2.0, 2.0});
        PricingEngine barrierEngine = new FdBlackScholesBarrierEngine(
                bsProcess, dividends, 200, 400);
        PricingEngine vanillaEngine = new FdBlackScholesVanillaEngine(
                bsProcess, dividends, 200, 400);

        Exercise europeanExercise = new EuropeanExercise(maturity);
        Exercise americanExercise = new AmericanExercise(today, maturity);

        // with the barrier too far away to matter, the price
        // equals the one of the american vanilla option
        Barrier.Type[] farBarrierTypes = {
                Barrier.Type.DownOut, Barrier.Type.UpOut};
        Option.Type[] farOptionTypes = {Option.Type.Put, Option.Type.Call};
        double[] farBarriers = {20.0, 500.0};

        for (int c = 0; c < farBarrierTypes.length; ++c) {
            for (double strike : new double[]{90.0, 110.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(farOptionTypes[c], strike);

                BarrierOption option = new BarrierOption(farBarrierTypes[c],
                        farBarriers[c], 0.0, payoff, americanExercise);
                option.setPricingEngine(barrierEngine);
                EuropeanOption vanilla =
                        new EuropeanOption(payoff, americanExercise);
                vanilla.setPricingEngine(vanillaEngine);

                double calculated = option.NPV();
                double expected = vanilla.NPV();
                if (Math.abs(calculated - expected) > 1e-3 * expected) {
                    fail("Failed to reproduce American vanilla price with "
                            + "dividends and a far barrier:"
                            + "\n    option type: " + farOptionTypes[c]
                            + "\n    strike:      " + strike
                            + "\n    barrier:     " + farBarriers[c]
                            + "\n    calculated:  " + calculated
                            + "\n    expected:    " + expected);
                }
            }
        }

        // near the barrier, early exercise adds value
        Barrier.Type[] nearBarrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.DownOut, Barrier.Type.UpOut,
                Barrier.Type.UpOut};
        Option.Type[] nearOptionTypes = {Option.Type.Call,
                Option.Type.Put, Option.Type.Call, Option.Type.Put};
        double[] nearBarriers = {85.0, 85.0, 120.0, 120.0};
        double[] nearRebates = {0.0, 0.0, 0.0, 5.0};

        for (int c = 0; c < nearBarrierTypes.length; ++c) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(nearOptionTypes[c], 100.0);

            BarrierOption american = new BarrierOption(nearBarrierTypes[c],
                    nearBarriers[c], nearRebates[c], payoff,
                    americanExercise);
            BarrierOption european = new BarrierOption(nearBarrierTypes[c],
                    nearBarriers[c], nearRebates[c], payoff,
                    europeanExercise);
            american.setPricingEngine(barrierEngine);
            european.setPricingEngine(barrierEngine);

            if (american.NPV() < european.NPV()) {
                fail("American knock-out price with dividends below "
                        + "european price:"
                        + "\n    barrier type: "
                        + Barrier.toString(nearBarrierTypes[c])
                        + "\n    option type:  " + nearOptionTypes[c]
                        + "\n    barrier:      " + nearBarriers[c]
                        + "\n    rebate:       " + nearRebates[c]
                        + "\n    american:     " + american.NPV()
                        + "\n    european:     " + european.NPV());
            }
        }
    }

    @Test
    public void testFdAmericanKnockOutWithDeferredExercise() {
        DayCounter dc = new Actual365Fixed();

        Date today = new Date(1, Month.JANUARY, 2025);
        Date maturity = today.add(365);
        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.0, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, 0.25, dc), true);

        BlackScholesMertonProcess bsProcess = new BlackScholesMertonProcess(
                s0, qTS, rTS, volTS);

        PricingEngine barrierEngine =
                new FdBlackScholesBarrierEngine(bsProcess, 200, 400);
        PricingEngine vanillaEngine =
                new FdBlackScholesVanillaEngine(bsProcess, 200, 400);

        Exercise europeanExercise = new EuropeanExercise(maturity);

        // with the barrier too far away to matter, the price equals
        // the one of the american vanilla option with the same exercise
        Exercise deferredExercise = new AmericanExercise(
                today.add(new Period(6, TimeUnit.MONTHS)), maturity);
        for (double strike : new double[]{100.0, 110.0}) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Put, strike);

            BarrierOption option = new BarrierOption(Barrier.Type.DownOut,
                    20.0, 0.0, payoff, deferredExercise);
            option.setPricingEngine(barrierEngine);
            EuropeanOption vanilla =
                    new EuropeanOption(payoff, deferredExercise);
            vanilla.setPricingEngine(vanillaEngine);

            double calculated = option.NPV();
            double expected = vanilla.NPV();
            if (Math.abs(calculated - expected) > 1e-3 * expected) {
                fail("Failed to reproduce deferred American vanilla price "
                        + "with a far barrier:"
                        + "\n    strike:     " + strike
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected);
            }
        }

        // where the barrier lies in the money, exercising just before the
        // barrier is hit is only possible within the exercise window; when
        // it opens a week before maturity, it adds only a small fraction
        // of the early-exercise premium of the american option
        Exercise lateExercise = new AmericanExercise(
                maturity.substract(7), maturity);
        Exercise americanExercise = new AmericanExercise(today, maturity);
        Barrier.Type[] barrierTypes = {Barrier.Type.DownOut,
                Barrier.Type.UpOut};
        Option.Type[] optionTypes = {Option.Type.Put, Option.Type.Call};
        double[] barriers = {85.0, 120.0};

        for (int c = 0; c < barrierTypes.length; ++c) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(optionTypes[c], 100.0);

            BarrierOption late = new BarrierOption(barrierTypes[c],
                    barriers[c], 0.0, payoff, lateExercise);
            BarrierOption european = new BarrierOption(barrierTypes[c],
                    barriers[c], 0.0, payoff, europeanExercise);
            BarrierOption american = new BarrierOption(barrierTypes[c],
                    barriers[c], 0.0, payoff, americanExercise);
            late.setPricingEngine(barrierEngine);
            european.setPricingEngine(barrierEngine);
            american.setPricingEngine(barrierEngine);

            double lateValue = late.NPV();
            double europeanValue = european.NPV();
            double americanValue = american.NPV();
            if (lateValue < europeanValue
                    || lateValue - europeanValue
                    > 0.05 * (americanValue - europeanValue)) {
                fail("Failed to price American knock-out with late "
                        + "exercise window:"
                        + "\n    barrier type: " + Barrier.toString(barrierTypes[c])
                        + "\n    option type:  " + optionTypes[c]
                        + "\n    barrier:      " + barriers[c]
                        + "\n    late:         " + lateValue
                        + "\n    european:     " + europeanValue
                        + "\n    american:     " + americanValue);
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

    /**
     * Boost {@code BOOST_CHECK_CLOSE}: the third argument is a percentage
     * tolerance on {@code |calculated - expected| / max(|..|)}.
     */
    private static void checkClose(String name, double calculated,
                                   double expected, double percent) {
        if (Double.isNaN(calculated) || Double.isNaN(expected)) {
            fail(name + " is NaN: calculated " + calculated
                    + ", expected " + expected);
        }
        double diff = Math.abs(calculated - expected);
        double scale = Math.max(Math.abs(calculated), Math.abs(expected));
        boolean ok = scale == 0.0 || diff / scale * 100.0 <= percent;
        if (!ok) {
            fail(name + " mismatch:"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    difference: " + diff
                    + "\n    tolerance:  " + percent + "%");
        }
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

    /** Official {@code REPORT_FX_FAILURE} message fields. */
    private static void reportFxFailure(String greekName,
                                        BarrierFxOptionData value,
                                        PlainVanillaPayoff payoff,
                                        Exercise exercise, double s, Date today,
                                        double expected, double calculated,
                                        double error) {
        fail("\n" + barrierTypeToString(value.barrierType) + " "
                + exerciseTypeToString(exercise) + " "
                + payoff.optionType() + " FX option with plain-vanilla payoff:\n"
                + "    underlying value: " + s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    barrier:          " + value.barrier + "\n"
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
                + "    tolerance:        " + value.tol);
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
