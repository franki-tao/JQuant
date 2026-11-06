package pricingengines;

import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.quotes.SimpleQuote;
import jquant.Compounding;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Analytic European prices from QuantLib {@code test-suite/europeanoption.cpp}
 * ({@code testValues} and {@code testGreekValues}), Haug 1998. The option is
 * priced through {@code SimpleQuote}, {@code FlatForward}, {@code BlackConstantVol},
 * {@code BlackScholesMertonProcess} and {@code AnalyticEuropeanEngine}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/europeanoption.cpp
 */
public class EuropeanOptionTest {
    private static final double TOLERANCE = 1.0e-4;

    @Test
    public void testValues() {
        // type sign, strike, spot, q, r, t, vol, value
        double[][] values = {
                {1, 65.00, 60.00, 0.00, 0.08, 0.25, 0.30, 2.1334},
                {-1, 95.00, 100.00, 0.05, 0.10, 0.50, 0.20, 2.4648},
                {-1, 19.00, 19.00, 0.10, 0.10, 0.75, 0.28, 1.7011},
                {1, 19.00, 19.00, 0.10, 0.10, 0.75, 0.28, 1.7011},
                {1, 1.60, 1.56, 0.08, 0.06, 0.50, 0.12, 0.0291},
                {-1, 70.00, 75.00, 0.05, 0.10, 0.50, 0.35, 4.0870},
                {1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 0.0205},
                {1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8734},
                {1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 9.9413},
                {1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 0.3150},
                {1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1217},
                {1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 10.3556},
                {1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 0.9474},
                {1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3693},
                {1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 11.1381},
                {1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 0.8069},
                {1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0232},
                {1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 10.5769},
                {1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 2.7026},
                {1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.6997},
                {1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 12.7857},
                {1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 4.9329},
                {1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.3679},
                {1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 15.3086},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 9.9210},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8734},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 0.0408},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 10.2155},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1217},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 0.4551},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 10.8479},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3693},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 1.2376},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 10.3192},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0232},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 1.0646},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 12.2149},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.6997},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 3.2734},
                {-1, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 14.4452},
                {-1, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.3679},
                {-1, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 5.7963},
                {1, 40.00, 42.00, 0.08, 0.04, 0.75, 0.35, 5.0975}
        };
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));
        AnalyticEuropeanEngine engine = new AnalyticEuropeanEngine(process);
        for (double[] row : values) {
            Option.Type type = row[0] > 0 ? Option.Type.Call : Option.Type.Put;
            Date exerciseDate = today.add(timeToDays(row[5]));
            spot.setValue(row[2]);
            qRate.setValue(row[3]);
            rRate.setValue(row[4]);
            vol.setValue(row[6]);
            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(type, row[1]), new EuropeanExercise(exerciseDate));
            option.setPricingEngine(engine);
            assertEquals(row[7], option.NPV(), TOLERANCE, type + " K=" + row[1] + " S=" + row[2]);
        }
    }

    @Test
    public void testPutCallParityFromCurves() {
        // Haug rows with S=K=100, q=r=0.10, t=0.10, vol=0.15: call and put are both 1.8734.
        // Parity uses the flat curves' discount and forward, not a calculator price.
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        double strike = 100.0;
        double spotValue = 100.0;
        SimpleQuote spot = new SimpleQuote(spotValue);
        SimpleQuote qRate = new SimpleQuote(0.10);
        SimpleQuote rRate = new SimpleQuote(0.10);
        SimpleQuote vol = new SimpleQuote(0.15);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        Date exerciseDate = today.add(timeToDays(0.10));
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(flatVol(today, vol, dc), true));
        AnalyticEuropeanEngine engine = new AnalyticEuropeanEngine(process);
        EuropeanOption call = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, strike), new EuropeanExercise(exerciseDate));
        EuropeanOption put = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Put, strike), new EuropeanExercise(exerciseDate));
        call.setPricingEngine(engine);
        put.setPricingEngine(engine);
        double df = rTS.discount(exerciseDate, false);
        double dividendDiscount = qTS.discount(exerciseDate, false);
        double forward = spotValue * dividendDiscount / df;
        assertEquals(1.8734, call.NPV(), TOLERANCE);
        assertEquals(1.8734, put.NPV(), TOLERANCE);
        assertEquals(df * (forward - strike), call.NPV() - put.NPV(), TOLERANCE);
    }

    @Test
    public void testGreekValues() {
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        AnalyticEuropeanEngine engine = new AnalyticEuropeanEngine(process);
        // delta
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Call, 100, 105, 0.10, 0.10, 0.50, 0.36,
                "delta", 0.5946);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 100, 105, 0.10, 0.10, 0.50, 0.36,
                "delta", -0.3566);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 100, 105, 0.10, 0.10, 0.50, 0.36,
                "elasticity", -4.8775);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Call, 60, 55, 0.00, 0.10, 0.75, 0.30,
                "gamma", 0.0278);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 60, 55, 0.00, 0.10, 0.75, 0.30,
                "gamma", 0.0278);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Call, 60, 55, 0.00, 0.10, 0.75, 0.30,
                "vega", 18.9358);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 60, 55, 0.00, 0.10, 0.75, 0.30,
                "vega", 18.9358);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 405, 430, 0.05, 0.07, 1.0 / 12.0, 0.20,
                "theta", -31.1924);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 405, 430, 0.05, 0.07, 1.0 / 12.0, 0.20,
                "thetaPerDay", -0.0855);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Call, 75, 72, 0.00, 0.09, 1.0, 0.19,
                "rho", 38.7325);
        assertGreek(engine, spot, qRate, rRate, vol, Option.Type.Put, 490, 500, 0.05, 0.08, 0.25, 0.15,
                "dividendRho", 42.2254);
    }

    @Test
    public void testRhoUsesCurveReferenceDate() {
        // Haug rho stays 38.7325 when the evaluation date moves but the flat
        // curves keep their original reference date. Discount is not re-anchored.
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(72.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.09);
        SimpleQuote vol = new SimpleQuote(0.19);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 75.0),
                new EuropeanExercise(today.add(timeToDays(1.0))));
        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        assertEquals(38.7325, option.rho(), TOLERANCE);
        Settings.instance.evaluationDate().equal(today.add(-30));
        assertEquals(38.7325, option.rho(), TOLERANCE);
        Settings.instance.evaluationDate().equal(today);
    }

    @Test
    public void testLiveQuoteUpdatesRepriceGreeks() {
        // One vanilla point from QuantLib testGreeks: spot 100, strike 100,
        // q=0.05, r=0.05, residual time 1, vol 0.11. Curves keep a fixed
        // reference date. The same option is repriced after each live quote
        // bump; finite differences are the official central bumps.
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        double spot0 = 100.0;
        double q0 = 0.05;
        double r0 = 0.05;
        double vol0 = 0.11;
        SimpleQuote spot = new SimpleQuote(spot0);
        SimpleQuote qRate = new SimpleQuote(q0);
        SimpleQuote rRate = new SimpleQuote(r0);
        SimpleQuote vol = new SimpleQuote(vol0);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 100.0),
                new EuropeanExercise(today.add(timeToDays(1.0))));
        option.setPricingEngine(new AnalyticEuropeanEngine(process));

        double value = option.NPV();
        double delta = option.delta();
        double gamma = option.gamma();
        double theta = option.theta();
        double rho = option.rho();
        double dividendRho = option.dividendRho();
        double vega = option.vega();
        double elasticity = option.elasticity();
        assertEquals(delta / value * spot0, elasticity, 1.0e-10);

        double du = spot0 * 1.0e-4;
        spot.setValue(spot0 + du);
        double valueUp = option.NPV();
        double deltaUp = option.delta();
        spot.setValue(spot0 - du);
        double valueDown = option.NPV();
        double deltaDown = option.delta();
        spot.setValue(spot0);
        assertEquals((valueUp - valueDown) / (2.0 * du), delta, 1.0e-5 * spot0);
        assertEquals((deltaUp - deltaDown) / (2.0 * du), gamma, 1.0e-5 * spot0);

        double dr = r0 * 1.0e-4;
        rRate.setValue(r0 + dr);
        valueUp = option.NPV();
        rRate.setValue(r0 - dr);
        valueDown = option.NPV();
        rRate.setValue(r0);
        assertEquals((valueUp - valueDown) / (2.0 * dr), rho, 1.0e-5 * spot0);

        double dq = q0 * 1.0e-4;
        qRate.setValue(q0 + dq);
        valueUp = option.NPV();
        qRate.setValue(q0 - dq);
        valueDown = option.NPV();
        qRate.setValue(q0);
        assertEquals((valueUp - valueDown) / (2.0 * dq), dividendRho, 1.0e-5 * spot0);

        double dv = vol0 * 1.0e-4;
        vol.setValue(vol0 + dv);
        valueUp = option.NPV();
        vol.setValue(vol0 - dv);
        valueDown = option.NPV();
        vol.setValue(vol0);
        assertEquals((valueUp - valueDown) / (2.0 * dv), vega, 1.0e-5 * spot0);

        assertEquals(value, option.NPV(), 0.0);
        assertEquals(delta, option.delta(), 0.0);
        assertEquals(gamma, option.gamma(), 0.0);
        assertEquals(theta, option.theta(), 0.0);
        assertEquals(rho, option.rho(), 0.0);
        assertEquals(dividendRho, option.dividendRho(), 0.0);
        assertEquals(vega, option.vega(), 0.0);
        assertEquals(elasticity, option.elasticity(), 0.0);
    }

    @Test
    public void testImpliedVolatilityRecoversHaugVol() {
        // Haug call 60/65, q=0, r=0.08, t=0.25 is 2.1334 at vol 0.30.
        // The quote is moved to 0.27 before the solve, so the root is not
        // the volatility already stored on the curve.
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(60.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.08);
        SimpleQuote vol = new SimpleQuote(0.30);
        AnalyticEuropeanEngine engine = engine(today, spot, qRate, rRate, vol, dc);
        EuropeanOption call = option(engine, Option.Type.Call, 65.0, today, 0.25);
        EuropeanOption put = option(engine, Option.Type.Put, 65.0, today, 0.25);
        double callAt30 = call.NPV();
        double putAt30 = put.NPV();
        assertEquals(2.1334, callAt30, TOLERANCE);

        vol.setValue(0.27);
        double callAt27 = call.NPV();
        double putAt27 = put.NPV();
        assertTrue(Math.abs(callAt27 - callAt30) > TOLERANCE);

        double callIv = call.impliedVolatility(callAt30, engine, 1.0e-6, 100, 1.0e-7, 4.0);
        double putIv = put.impliedVolatility(putAt30, engine, 1.0e-6, 100, 1.0e-7, 4.0);
        assertEquals(0.30, callIv, 1.0e-5);
        assertEquals(0.30, putIv, 1.0e-5);
        assertEquals(callIv, call.impliedVolatility(callAt30, engine, 1.0e-6, 100, 1.0e-7, 4.0), 0.0);

        assertEquals(0.27, vol.value(), 0.0);
        assertEquals(60.0, spot.value(), 0.0);
        assertEquals(0.0, qRate.value(), 0.0);
        assertEquals(0.08, rRate.value(), 0.0);
        assertEquals(callAt27, call.NPV(), 0.0);
        assertEquals(putAt27, put.NPV(), 0.0);

        vol.setValue(0.40);
        assertTrue(Math.abs(call.NPV() - callAt27) > TOLERANCE);
        vol.setValue(0.27);
        assertEquals(callAt27, call.NPV(), 0.0);

        // Haug put 95/100, q=0.05, r=0.10, t=0.50 is 2.4648 at vol 0.20.
        SimpleQuote putSpot = new SimpleQuote(100.0);
        SimpleQuote putQ = new SimpleQuote(0.05);
        SimpleQuote putR = new SimpleQuote(0.10);
        SimpleQuote putVol = new SimpleQuote(0.20);
        AnalyticEuropeanEngine putEngine = engine(today, putSpot, putQ, putR, putVol, dc);
        EuropeanOption haugPut = option(putEngine, Option.Type.Put, 95.0, today, 0.50);
        double putAt20 = haugPut.NPV();
        assertEquals(2.4648, putAt20, TOLERANCE);
        putVol.setValue(0.27);
        assertEquals(0.20, haugPut.impliedVolatility(putAt20, putEngine, 1.0e-6, 100, 1.0e-7, 4.0), 1.0e-5);
        assertEquals(0.27, putVol.value(), 0.0);
    }

    @Test
    public void testImpliedVolatilityRestoresQuoteWhenItFails() {
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(60.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.08);
        SimpleQuote vol = new SimpleQuote(0.27);
        AnalyticEuropeanEngine engine = engine(today, spot, qRate, rRate, vol, dc);
        EuropeanOption call = option(engine, Option.Type.Call, 65.0, today, 0.25);
        double price = call.NPV();

        assertThrows(IllegalArgumentException.class,
                () -> call.impliedVolatility(price, engine, 1.0e-8, 1, 1.0e-7, 4.0));
        assertEquals(0.27, vol.value(), 0.0);
        assertEquals(price, call.NPV(), 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> call.impliedVolatility(-1.0, engine, 1.0e-6, 100, 1.0e-7, 4.0));
        // This call is out of the money, so the price at the volatility floor is
        // already within QuantLib's 1e4*epsilon band of zero. The floor is the
        // root. A zero target on an in-the-money option is below intrinsic and fails.
        assertEquals(1.0e-7, call.impliedVolatility(0.0, engine, 1.0e-6, 100, 1.0e-7, 4.0), 0.0);
        EuropeanOption itmZero = option(engine, Option.Type.Call, 50.0, today, 0.25);
        assertThrows(IllegalArgumentException.class,
                () -> itmZero.impliedVolatility(0.0, engine, 1.0e-6, 100, 1.0e-7, 4.0));
        assertThrows(IllegalArgumentException.class,
                () -> call.impliedVolatility(1.0e6, engine, 1.0e-6, 100, 1.0e-7, 4.0));

        vol.setValue(1.0e-7);
        EuropeanOption itm = option(engine, Option.Type.Call, 50.0, today, 0.25);
        double floor = itm.NPV();
        vol.setValue(0.27);
        assertThrows(IllegalArgumentException.class,
                () -> itm.impliedVolatility(floor - 1.0e-8, engine, 1.0e-6, 100, 1.0e-7, 4.0));

        assertEquals(0.27, vol.value(), 0.0);
        assertEquals(60.0, spot.value(), 0.0);
        assertEquals(price, call.NPV(), 0.0);
        vol.setValue(0.30);
        assertEquals(2.1334, call.NPV(), TOLERANCE);
        vol.setValue(0.27);
        assertEquals(price, call.NPV(), 0.0);
    }

    @Test
    public void testImpliedVolatilityLeavesLiteralCurveUntouched() {
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);
        BlackConstantVol volCurve = new BlackConstantVol(today, new NullCalendar(), 0.30, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(60.0), true),
                new Handle<>(new FlatForward(today, new Handle<>(new SimpleQuote(0.0), true), dc,
                        Compounding.Continuous, Frequency.ANNUAL), true),
                new Handle<>(new FlatForward(today, new Handle<>(new SimpleQuote(0.08), true), dc,
                        Compounding.Continuous, Frequency.ANNUAL), true),
                new Handle<>(volCurve, true));
        AnalyticEuropeanEngine engine = new AnalyticEuropeanEngine(process);
        EuropeanOption call = option(engine, Option.Type.Call, 65.0, today, 0.25);
        double price = call.NPV();
        assertEquals(2.1334, price, TOLERANCE);
        assertEquals(0.30, call.impliedVolatility(price, engine, 1.0e-6, 100, 1.0e-7, 4.0), 1.0e-5);
        assertEquals(0.30, call.impliedVolatility(price, engine), 1.0e-4);
        assertTrue(process.blackVolatility().getValue() == volCurve);
        assertTrue(volCurve.dayCounter() == dc);
        assertEquals(price, call.NPV(), 0.0);
    }

    private static AnalyticEuropeanEngine engine(Date today, SimpleQuote spot, SimpleQuote qRate,
                                                 SimpleQuote rRate, SimpleQuote vol, DayCounter dc) {
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        return new AnalyticEuropeanEngine(process);
    }

    private static EuropeanOption option(AnalyticEuropeanEngine engine, Option.Type type, double strike,
                                         Date today, double t) {
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(type, strike),
                new EuropeanExercise(today.add(timeToDays(t))));
        option.setPricingEngine(engine);
        return option;
    }

    private static void assertGreek(AnalyticEuropeanEngine engine, SimpleQuote spot, SimpleQuote qRate,
                                    SimpleQuote rRate, SimpleQuote vol, Option.Type type, double strike,
                                    double spotValue, double q, double r, double t, double v,
                                    String greek, double expected) {
        Date today = Settings.instance.evaluationDate().Date();
        spot.setValue(spotValue);
        qRate.setValue(q);
        rRate.setValue(r);
        vol.setValue(v);
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(type, strike),
                new EuropeanExercise(today.add(timeToDays(t))));
        option.setPricingEngine(engine);
        double calculated = switch (greek) {
            case "delta" -> option.delta();
            case "elasticity" -> option.elasticity();
            case "gamma" -> option.gamma();
            case "vega" -> option.vega();
            case "theta" -> option.theta();
            case "thetaPerDay" -> option.thetaPerDay();
            case "rho" -> option.rho();
            case "dividendRho" -> option.dividendRho();
            default -> throw new IllegalArgumentException(greek);
        };
        assertEquals(expected, calculated, TOLERANCE, greek + " " + type + " K=" + strike);
    }

    private static int timeToDays(double t) {
        return (int) (t * 360.0 + 0.5);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate, DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc, Compounding.Continuous, Frequency.ANNUAL);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol, DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(), new Handle<>(vol, true), dc);
    }
}
