package pricingengines;

import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.IntegralEngine;
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
 * Integral European engine checked against analytic results.
 *
 * <p>Ports the official {@code testIntegralEngines} and
 * {@code testEngineConsistency} grid from
 * {@code test-suite/europeanoption.cpp}: calls and puts, strikes
 * 75/100/125, one year, spot 100, q in {0, 0.05}, r in {0.01, 0.05, 0.15},
 * vols in {0.11, 0.50, 1.20}. Errors are the official
 * {@code relativeError(expected, calculated, spot)}.
 */
public class IntegralEngineTest {
    private static final double VALUE_TOLERANCE = 0.0001;

    @Test
    public void testIntegralEngines() {
        Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        double[] strikes = {75.0, 100.0, 125.0};
        int[] lengths = {1};
        double[] underlyings = {100.0};
        double[] qRates = {0.00, 0.05};
        double[] rRates = {0.01, 0.05, 0.15};
        double[] vols = {0.11, 0.50, 1.20};

        DayCounter dc = new Actual360(false);
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));
        AnalyticEuropeanEngine referenceEngine = new AnalyticEuropeanEngine(process);
        IntegralEngine integralEngine = new IntegralEngine(process);

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
                    option.setPricingEngine(integralEngine);

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    double q = m, r = n;
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

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
                                            "Integral value is NaN");
                                }
                            }
                        }
                    }
                }
            }
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
