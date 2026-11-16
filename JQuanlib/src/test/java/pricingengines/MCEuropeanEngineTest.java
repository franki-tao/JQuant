package pricingengines;

import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.math.randomnumbers.RngType;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.MakeMCEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monte Carlo European engines checked against analytic results.
 *
 * <p>Ports the official {@code testMcEngines} and {@code testQmcEngines}
 * with the {@code testEngineConsistency} grid from
 * {@code test-suite/europeanoption.cpp}: calls and puts, strikes
 * 75/100/125, one year, spot 100, q in {0, 0.05}, r in {0.01, 0.05, 0.15},
 * vols in {0.11, 0.50, 1.20}. Only the value is checked, with the official
 * relative tolerance 0.01; errors are the official
 * {@code relativeError(expected, calculated, spot)}.
 */
public class MCEuropeanEngineTest {
    private static final double VALUE_TOLERANCE = 0.01;

    @Test
    public void testMcEngines() {
        Market market = new Market();
        PricingEngine engine = new MakeMCEuropeanEngine(market.process, RngType.PSEUDO_RANDOM)
                .withSteps(1)
                .withSamples(40000)
                .withSeed(42)
                .build();

        testEngineConsistency(market, engine);
    }

    @Test
    public void testQmcEngines() {
        Market market = new Market();
        PricingEngine engine = new MakeMCEuropeanEngine(market.process, RngType.LOW_DISCREPANCY)
                .withSteps(1)
                .withSamples(4095) // 2^12-1
                .build();

        testEngineConsistency(market, engine);
    }

    private static void testEngineConsistency(Market market, PricingEngine engine) {
        Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        double[] strikes = {75.0, 100.0, 125.0};
        int[] lengths = {1};
        double[] underlyings = {100.0};
        double[] qRates = {0.00, 0.05};
        double[] rRates = {0.01, 0.05, 0.15};
        double[] vols = {0.11, 0.50, 1.20};

        Date today = Date.todaysDate();
        AnalyticEuropeanEngine referenceEngine = new AnalyticEuropeanEngine(market.process);

        for (Option.Type type : types) {
            for (double strike : strikes) {
                for (int length : lengths) {
                    Date exDate = today.add(length * 360);
                    EuropeanExercise exercise = new EuropeanExercise(exDate);
                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

                    // reference option
                    EuropeanOption refOption = new EuropeanOption(payoff, exercise);
                    refOption.setPricingEngine(referenceEngine);
                    // option to check
                    EuropeanOption option = new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    double q = m, r = n;
                                    market.spot.setValue(u);
                                    market.qRate.setValue(q);
                                    market.rRate.setValue(r);
                                    market.vol.setValue(v);

                                    double expected = refOption.NPV();
                                    double calculated = option.NPV();
                                    double error = relativeError(expected, calculated, u);
                                    assertTrue(error <= VALUE_TOLERANCE,
                                            type + " option, strike " + strike + ", q " + q
                                                    + ", r " + r + ", vol " + v
                                                    + ": expected value " + expected
                                                    + ", calculated " + calculated
                                                    + ", error " + error
                                                    + ", tolerance " + VALUE_TOLERANCE);
                                    assertFalse(Double.isNaN(calculated),
                                            "Monte Carlo value is NaN");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Market quotes shared by the process and by the consistency grid,
     * mirroring the official test's shared {@code SimpleQuote}s.
     */
    private static final class Market {
        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final BlackScholesMertonProcess process;

        Market() {
            Date today = Date.todaysDate();
            Settings.instance.evaluationDate().equal(today);

            DayCounter dc = new Actual360(false);
            BlackVolTermStructure volTS = flatVol(today, vol, dc);
            YieldTermStructure qTS = flatRate(today, qRate, dc);
            YieldTermStructure rTS = flatRate(today, rRate, dc);

            process = new BlackScholesMertonProcess(
                    new Handle<>(spot, true),
                    new Handle<>(qTS, true),
                    new Handle<>(rTS, true),
                    new Handle<>(volTS, true));
        }
    }

    private static double relativeError(double x1, double x2, double reference) {
        if (reference != 0.0)
            return Math.abs(x1 - x2) / reference;
        return Math.abs(x1 - x2);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate, DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol, DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(), new Handle<>(vol, true), dc);
    }
}
