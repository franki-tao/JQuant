package pricingengines;

import jquant.Barrier;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PartialBarrier;
import jquant.PartialTimeBarrierOption;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.barrier.AnalyticPartialTimeBarrierOptionEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVarianceCurve;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ForwardCurve;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Partial-time barrier option pricing from QuantLib
 * {@code test-suite/partialtimebarrieroption.cpp}.
 *
 * <p>Complete port of the five official cases: analytic-engine call and put
 * tables, finiteness with d2 near zero, in-out parity, and put-call symmetry.
 */
public class PartialTimeBarrierOptionTest {

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

    /** Official {@code TestCase}. */
    private static class TestCase {
        final double underlying;
        final double strike;
        final int days;
        final double result;

        TestCase(double underlying, double strike, int days, double result) {
            this.underlying = underlying;
            this.strike = strike;
            this.days = days;
            this.result = result;
        }
    }

    @Test
    public void testAnalyticEngine() {
        Date today = Settings.instance.evaluationDate().Date();

        Option.Type type = Option.Type.Call;
        DayCounter dc = new Actual360();
        Date maturity = today.add(360);
        Exercise exercise = new EuropeanExercise(maturity);
        double barrier = 100.0;
        double rebate = 0.0;

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.1);
        SimpleQuote vol = new SimpleQuote(0.25);

        Handle<Quote> underlying = new Handle<>(spot, true);
        YieldTermStructure dividendTS = flatRate(today, qRate, dc);
        YieldTermStructure riskFreeTS = flatRate(today, rRate, dc);
        BlackVolTermStructure blackVolTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                underlying,
                new Handle<>(dividendTS, true),
                new Handle<>(riskFreeTS, true),
                new Handle<>(blackVolTS, true));
        AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);

        TestCase[] cases = {
                new TestCase(95.0, 90.0, 1, 0.0393),
                new TestCase(95.0, 110.0, 1, 0.0000),
                new TestCase(105.0, 90.0, 1, 9.8751),
                new TestCase(105.0, 110.0, 1, 6.2303),

                new TestCase(95.0, 90.0, 90, 6.2747),
                new TestCase(95.0, 110.0, 90, 3.7352),
                new TestCase(105.0, 90.0, 90, 15.6324),
                new TestCase(105.0, 110.0, 90, 9.6812),

                new TestCase(95.0, 90.0, 180, 10.3345),
                new TestCase(95.0, 110.0, 180, 5.8712),
                new TestCase(105.0, 90.0, 180, 19.2896),
                new TestCase(105.0, 110.0, 180, 11.6055),

                new TestCase(95.0, 90.0, 270, 13.4342),
                new TestCase(95.0, 110.0, 270, 7.1270),
                new TestCase(105.0, 90.0, 270, 22.0753),
                new TestCase(105.0, 110.0, 270, 12.7342),

                new TestCase(95.0, 90.0, 359, 16.8576),
                new TestCase(95.0, 110.0, 359, 7.5763),
                new TestCase(105.0, 90.0, 359, 25.1488),
                new TestCase(105.0, 110.0, 359, 13.1376)
        };

        for (TestCase i : cases) {
            Date coverEventDate = today.add(i.days);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, i.strike);
            PartialTimeBarrierOption option = new PartialTimeBarrierOption(
                    Barrier.Type.DownOut, PartialBarrier.Range.EndB1,
                    barrier, rebate, coverEventDate, payoff, exercise);
            option.setPricingEngine(engine);

            spot.setValue(i.underlying);
            double calculated = option.NPV();
            double expected = i.result;
            double error = Math.abs(calculated - expected);
            double tolerance = 1e-4;
            if (error > tolerance) {
                reportFailure("value", Barrier.Type.DownOut, barrier, rebate,
                        payoff, exercise, i.underlying, 0.0, 0.1, today, 0.25,
                        expected, calculated, error, tolerance);
            }
        }
    }

    @Test
    public void testAnalyticEngineNearZeroD2() {
        Date today = new Date(1, Month.JANUARY, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        final int[] pillars = {91, 182, 365, 730, 1095};
        final double[] rates = {0.01, 0.03, 0.05, 0.02, 0.04};
        final double[] instantVols = {0.30, 0.22, 0.20, 0.25, 0.20};

        List<Date> dates = new ArrayList<>();
        dates.add(today);
        List<Date> volDates = new ArrayList<>();
        List<Double> forwards = new ArrayList<>();
        forwards.add(rates[0]);
        List<Double> yields = new ArrayList<>();
        yields.add(rates[0] - 0.5 * instantVols[0] * instantVols[0]);
        List<Double> blackVols = new ArrayList<>();

        double variance = 0.0;
        double previous = 0.0;
        for (int i = 0; i < pillars.length; ++i) {
            Date date = today.add(pillars[i]);
            double t = dc.yearFraction(today, date);
            variance += instantVols[i] * instantVols[i] * (t - previous);
            previous = t;
            dates.add(date);
            volDates.add(date);
            forwards.add(rates[i]);
            // Carry equals half the variance rate, so d2 cancels to near zero.
            yields.add(rates[i] - 0.5 * instantVols[i] * instantVols[i]);
            blackVols.add(Math.sqrt(variance / t));
        }

        YieldTermStructure dividendCurve =
                new ForwardCurve(dates, yields, dc);
        YieldTermStructure riskFreeCurve =
                new ForwardCurve(dates, forwards, dc);
        BlackVolTermStructure volCurve = new BlackVarianceCurve(
                today, volDates, blackVols, dc, false);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(dividendCurve, true),
                new Handle<>(riskFreeCurve, true),
                new Handle<>(volCurve, true));

        AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 100.0);
        Exercise exercise = new EuropeanExercise(today.add(548));

        PartialTimeBarrierOption startOut = new PartialTimeBarrierOption(
                Barrier.Type.DownOut, PartialBarrier.Range.Start, 85.0, 0.0,
                today.add(274), payoff, exercise);
        PartialTimeBarrierOption startIn = new PartialTimeBarrierOption(
                Barrier.Type.DownIn, PartialBarrier.Range.Start, 85.0, 0.0,
                today.add(274), payoff, exercise);
        PartialTimeBarrierOption endOut = new PartialTimeBarrierOption(
                Barrier.Type.DownOut, PartialBarrier.Range.EndB1, 85.0, 0.0,
                today.add(274), payoff, exercise);

        startOut.setPricingEngine(engine);
        startIn.setPricingEngine(engine);
        endOut.setPricingEngine(engine);

        double outPrice = startOut.NPV();
        double inPrice = startIn.NPV();
        double endPrice = endOut.NPV();
        assertTrue(Double.isFinite(outPrice),
                "start down-out price is not finite: " + outPrice);
        assertTrue(Double.isFinite(inPrice),
                "start down-in price is not finite: " + inPrice);
        assertTrue(Double.isFinite(endPrice),
                "end down-out price is not finite: " + endPrice);
    }

    @Test
    public void testAnalyticEnginePutOption() {
        Date today = Settings.instance.evaluationDate().Date();

        Option.Type type = Option.Type.Put;
        DayCounter dc = new Actual360();
        Date maturity = today.add(360);
        Exercise exercise = new EuropeanExercise(maturity);
        double barrier = 100.0;
        double rebate = 0.0;

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.1);
        SimpleQuote vol = new SimpleQuote(0.25);

        Handle<Quote> underlying = new Handle<>(spot, true);
        YieldTermStructure dividendTS = flatRate(today, qRate, dc);
        YieldTermStructure riskFreeTS = flatRate(today, rRate, dc);
        BlackVolTermStructure blackVolTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                underlying,
                new Handle<>(dividendTS, true),
                new Handle<>(riskFreeTS, true),
                new Handle<>(blackVolTS, true));
        AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);

        TestCase[] cases = {
                new TestCase(95.0, 90.0, 1, 1.5551),
                new TestCase(95.0, 95.0, 1, 2.0589),
                new TestCase(90.0, 95.0, 1, 4.4512),
                new TestCase(99.0, 90.0, 1, 0.3404),

                new TestCase(95.0, 90.0, 90, 2.4181),
                new TestCase(95.0, 95.0, 90, 3.2257),
                new TestCase(90.0, 95.0, 90, 5.0624),
                new TestCase(99.0, 90.0, 90, 1.5992),

                new TestCase(95.0, 90.0, 180, 3.0021),
                new TestCase(95.0, 95.0, 180, 4.0617),
                new TestCase(90.0, 95.0, 180, 5.7960),
                new TestCase(99.0, 90.0, 180, 2.1903),

                new TestCase(95.0, 90.0, 270, 3.4194),
                new TestCase(95.0, 95.0, 270, 4.7362),
                new TestCase(90.0, 95.0, 270, 6.4370),
                new TestCase(99.0, 90.0, 270, 2.6025),

                new TestCase(95.0, 90.0, 359, 3.5965),
                new TestCase(95.0, 95.0, 359, 5.1865),
                new TestCase(90.0, 95.0, 359, 6.8782),
                new TestCase(99.0, 90.0, 359, 2.7759)
        };

        for (TestCase i : cases) {
            Date coverEventDate = today.add(i.days);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, i.strike);
            PartialTimeBarrierOption option = new PartialTimeBarrierOption(
                    Barrier.Type.UpOut, PartialBarrier.Range.EndB1,
                    barrier, rebate, coverEventDate, payoff, exercise);
            option.setPricingEngine(engine);

            spot.setValue(i.underlying);
            double calculated = option.NPV();
            double expected = i.result;
            double error = Math.abs(calculated - expected);
            double tolerance = 1e-4;
            if (error > tolerance) {
                reportFailure("value", Barrier.Type.UpOut, barrier, rebate,
                        payoff, exercise, i.underlying, 0.0, 0.1, today, 0.25,
                        expected, calculated, error, tolerance);
            }
        }
    }

    @Test
    public void testInOutParity() {
        Date today = new Date(1, Month.JANUARY, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        Exercise exercise = new EuropeanExercise(today.add(182));
        Date coverEventDate = today.add(91);
        double spotPrice = 100.0;
        SimpleQuote spot = new SimpleQuote(spotPrice);
        Handle<Quote> underlying = new Handle<>(spot, true);
        YieldTermStructure dividendTS = flatRate(today, 0.04, dc);
        YieldTermStructure riskFreeTS = flatRate(today, 0.08, dc);
        BlackVolTermStructure blackVolTS = flatVol(today, 0.25, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                underlying,
                new Handle<>(dividendTS, true),
                new Handle<>(riskFreeTS, true),
                new Handle<>(blackVolTS, true));
        AnalyticPartialTimeBarrierOptionEngine barrierEngine =
                new AnalyticPartialTimeBarrierOptionEngine(process);
        AnalyticEuropeanEngine vanillaEngine =
                new AnalyticEuropeanEngine(process);

        Barrier.Type[] knockIn = {Barrier.Type.DownIn, Barrier.Type.UpIn};
        Barrier.Type[] knockOut = {Barrier.Type.DownOut, Barrier.Type.UpOut};
        double[] barriers = {85.0, 115.0};

        for (Option.Type type : new Option.Type[]{
                Option.Type.Put, Option.Type.Call}) {
            for (double strike : new double[]{80.0, 100.0, 130.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                EuropeanOption vanilla = new EuropeanOption(payoff, exercise);
                vanilla.setPricingEngine(vanillaEngine);
                double vanillaValue = vanilla.NPV();

                for (int b = 0; b < barriers.length; ++b) {
                    PartialTimeBarrierOption in = new PartialTimeBarrierOption(
                            knockIn[b], PartialBarrier.Range.Start,
                            barriers[b], 0.0, coverEventDate, payoff, exercise);
                    PartialTimeBarrierOption out = new PartialTimeBarrierOption(
                            knockOut[b], PartialBarrier.Range.Start,
                            barriers[b], 0.0, coverEventDate, payoff, exercise);
                    in.setPricingEngine(barrierEngine);
                    out.setPricingEngine(barrierEngine);

                    double expected = vanillaValue - out.NPV();
                    double calculated = in.NPV();
                    double error = Math.abs(calculated - expected);
                    double tolerance = 1e-10;
                    if (error > tolerance) {
                        reportFailure("value", knockIn[b], barriers[b], 0.0,
                                payoff, exercise, spotPrice, 0.04, 0.08,
                                today, 0.25, expected, calculated, error,
                                tolerance);
                    }
                }
            }
        }
    }

    /** Official {@code PutCallSymmetryTestCase}. */
    private static class PutCallSymmetryTestCase {
        final double callStrike;
        final double callBarrier;
        final Barrier.Type callType;
        final double putStrike;
        final double putBarrier;
        final int days;
        final Barrier.Type putType;

        PutCallSymmetryTestCase(double callStrike, double callBarrier,
                                Barrier.Type callType, double putStrike,
                                double putBarrier, int days,
                                Barrier.Type putType) {
            this.callStrike = callStrike;
            this.callBarrier = callBarrier;
            this.callType = callType;
            this.putStrike = putStrike;
            this.putBarrier = putBarrier;
            this.days = days;
            this.putType = putType;
        }
    }

    @Test
    public void testPutCallSymmetry() {
        Date today = Settings.instance.evaluationDate().Date();

        PutCallSymmetryTestCase[] cases = {
                new PutCallSymmetryTestCase(105.2631, 95.2380,
                        Barrier.Type.DownOut, 95.0, 105.0, 1, Barrier.Type.UpOut),
                new PutCallSymmetryTestCase(105.2631, 95.2380,
                        Barrier.Type.DownOut, 95.0, 105.0, 90, Barrier.Type.UpOut),
                new PutCallSymmetryTestCase(105.2631, 95.2380,
                        Barrier.Type.DownOut, 95.0, 105.0, 180, Barrier.Type.UpOut),
                new PutCallSymmetryTestCase(105.2631, 95.2380,
                        Barrier.Type.DownOut, 95.0, 105.0, 270, Barrier.Type.UpOut),
                new PutCallSymmetryTestCase(105.2631, 95.2380,
                        Barrier.Type.DownOut, 95.0, 105.0, 359, Barrier.Type.UpOut),

                new PutCallSymmetryTestCase(110.0, 120.0,
                        Barrier.Type.UpOut, 90.9090, 83.3333, 1, Barrier.Type.DownOut),
                new PutCallSymmetryTestCase(110.0, 120.0,
                        Barrier.Type.UpOut, 90.9090, 83.3333, 90, Barrier.Type.DownOut),
                new PutCallSymmetryTestCase(110.0, 120.0,
                        Barrier.Type.UpOut, 90.9090, 83.3333, 180, Barrier.Type.DownOut),
                new PutCallSymmetryTestCase(110.0, 120.0,
                        Barrier.Type.UpOut, 90.9090, 83.3333, 270, Barrier.Type.DownOut),
                new PutCallSymmetryTestCase(110.0, 120.0,
                        Barrier.Type.UpOut, 90.9090, 83.3333, 359, Barrier.Type.DownOut)
        };

        DayCounter dc = new Actual360();
        Date maturity = today.add(360);
        Exercise exercise = new EuropeanExercise(maturity);
        double r = 0.01;
        double rebate = 0.0;
        double spotPrice = 100.0;

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(r);
        SimpleQuote vol = new SimpleQuote(0.25);

        Handle<Quote> underlying = new Handle<>(spot, true);
        YieldTermStructure dividendTSCall = flatRate(today, qRate, dc);
        YieldTermStructure riskFreeTSCall = flatRate(today, rRate, dc);
        YieldTermStructure dividendTSPut = flatRate(today, rRate, dc);
        YieldTermStructure riskFreeTSPut = flatRate(today, qRate, dc);
        BlackVolTermStructure blackVolTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess callProcess = new BlackScholesMertonProcess(
                underlying,
                new Handle<>(dividendTSCall, true),
                new Handle<>(riskFreeTSCall, true),
                new Handle<>(blackVolTS, true));
        BlackScholesMertonProcess putProcess = new BlackScholesMertonProcess(
                underlying,
                new Handle<>(dividendTSPut, true),
                new Handle<>(riskFreeTSPut, true),
                new Handle<>(blackVolTS, true));
        AnalyticPartialTimeBarrierOptionEngine callEngine =
                new AnalyticPartialTimeBarrierOptionEngine(callProcess);
        AnalyticPartialTimeBarrierOptionEngine putEngine =
                new AnalyticPartialTimeBarrierOptionEngine(putProcess);

        for (PutCallSymmetryTestCase i : cases) {
            Date coverEventDate = today.add(i.days);
            PlainVanillaPayoff putPayoff =
                    new PlainVanillaPayoff(Option.Type.Put, i.putStrike);
            PlainVanillaPayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, i.callStrike);
            PartialTimeBarrierOption putOption = new PartialTimeBarrierOption(
                    i.putType, PartialBarrier.Range.EndB1, i.putBarrier,
                    rebate, coverEventDate, putPayoff, exercise);
            putOption.setPricingEngine(putEngine);
            PartialTimeBarrierOption callOption = new PartialTimeBarrierOption(
                    i.callType, PartialBarrier.Range.EndB1, i.callBarrier,
                    rebate, coverEventDate, callPayoff, exercise);
            callOption.setPricingEngine(callEngine);

            spot.setValue(spotPrice);
            double putValue = putOption.NPV();
            double callValue = callOption.NPV();
            double callAmount = i.putStrike / spotPrice;
            double error = Math.abs(putValue - callAmount * callValue);
            double tolerance = 1e-4;
            if (error > tolerance) {
                fail("Failed to reproduce the put-call symmetry for the"
                        + " partial-time barrier options"
                        + "\n    error:      " + error);
            }
        }
    }

    /** Official {@code REPORT_FAILURE} message fields. */
    private static void reportFailure(String greekName,
                                      Barrier.Type barrierType,
                                      double barrier, double rebate,
                                      PlainVanillaPayoff payoff,
                                      Exercise exercise, double s, double q,
                                      double r, Date today, double v,
                                      double expected, double calculated,
                                      double error, double tolerance) {
        fail("\n" + Barrier.toString(barrierType) + " "
                + exerciseTypeToString(exercise) + " "
                + payoff.optionType() + " option with vanilla payoff:\n"
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
