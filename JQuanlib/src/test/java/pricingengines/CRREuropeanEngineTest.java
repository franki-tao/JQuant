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
import jquant.pricingengines.vanilla.CRREuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRR European prices checked against Haug numbers already used by the
 * analytic engine, not against a price produced by the tree.
 *
 * <p>QuantLib {@code testCRRBinomialEngines} allows a relative error of 0.02
 * versus the analytic value, scaled by the spot. The checks here are tighter
 * where the 501-step tree actually lands, and a separate assertion only
 * requires that more steps move closer to the published price.
 */
public class CRREuropeanEngineTest {
    @Test
    public void testCrrApproachesHaugCall() {
        // Haug call, flat dividend yield 0: spot 60, strike 65, q=0, r=0.08, t=0.25, vol=0.30.
        double published = 2.1334;
        double steps51 = price(Option.Type.Call, 65, 60, 0, 0.08, 0.25, 0.30, 51);
        double steps201 = price(Option.Type.Call, 65, 60, 0, 0.08, 0.25, 0.30, 201);
        double steps501 = price(Option.Type.Call, 65, 60, 0, 0.08, 0.25, 0.30, 501);
        double analytic = analytic(Option.Type.Call, 65, 60, 0, 0.08, 0.25, 0.30);
        assertEquals(published, analytic, 1.0e-4);
        assertTrue(Math.abs(steps501 - published) < Math.abs(steps51 - published));
        assertTrue(Math.abs(steps501 - published) < Math.abs(steps201 - published));
        // 501-step distance to the published price. Not the QuantLib 0.02*spot allowance.
        assertEquals(published, steps501, 0.02);
    }

    @Test
    public void testCrrPutCallParityMatchesAnalytic() {
        // Haug at-the-money row: spot = strike = 100, q = r = 0.10, t = 0.50, vol = 0.25.
        // Published call and put are both 6.6997.
        double call = price(Option.Type.Call, 100, 100, 0.10, 0.10, 0.50, 0.25, 501);
        double put = price(Option.Type.Put, 100, 100, 0.10, 0.10, 0.50, 0.25, 501);
        double analyticCall = analytic(Option.Type.Call, 100, 100, 0.10, 0.10, 0.50, 0.25);
        double analyticPut = analytic(Option.Type.Put, 100, 100, 0.10, 0.10, 0.50, 0.25);
        assertEquals(6.6997, analyticCall, 1.0e-4);
        assertEquals(6.6997, analyticPut, 1.0e-4);
        // 501-step residual against the closed form. The published prices above stay at 0.05.
        assertEquals(analyticCall - analyticPut, call - put, 1.0e-5);
        assertEquals(6.6997, call, 0.05);
        assertEquals(6.6997, put, 0.05);
    }

    private static double price(Option.Type type, double strike, double spot, double q, double r,
                                double maturity, double vol, int steps) {
        Market market = new Market(spot, q, r, vol, maturity);
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(type, strike), new EuropeanExercise(market.exercise));
        option.setPricingEngine(new CRREuropeanEngine(market.process, steps));
        return option.NPV();
    }

    private static double analytic(Option.Type type, double strike, double spot, double q, double r,
                                   double maturity, double vol) {
        Market market = new Market(spot, q, r, vol, maturity);
        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(type, strike), new EuropeanExercise(market.exercise));
        option.setPricingEngine(new AnalyticEuropeanEngine(market.process));
        return option.NPV();
    }

    private static final class Market {
        final Date exercise;
        final BlackScholesMertonProcess process;

        Market(double spot, double q, double r, double vol, double maturity) {
            Date today = Date.todaysDate();
            Settings.instance.evaluationDate().equal(today);
            DayCounter dc = new Actual360(false);
            exercise = today.add((int) (maturity * 360.0 + 0.5));
            YieldTermStructure qTS = new FlatForward(today, new Handle<>(new SimpleQuote(q), true),
                    dc, Compounding.Continuous, Frequency.ANNUAL);
            YieldTermStructure rTS = new FlatForward(today, new Handle<>(new SimpleQuote(r), true),
                    dc, Compounding.Continuous, Frequency.ANNUAL);
            BlackVolTermStructure volTS = new BlackConstantVol(today, new NullCalendar(), vol, dc);
            process = new BlackScholesMertonProcess(
                    new Handle<>(new SimpleQuote(spot), true),
                    new Handle<>(qTS, true),
                    new Handle<>(rTS, true),
                    new Handle<>(volTS, true));
        }
    }
}
