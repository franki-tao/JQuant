package pricingengines;

import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Settings;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BinomialVanillaEngine;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binomial-tree European engines checked against analytic results.
 *
 * <p>Ports {@code testEngineConsistency} and the official
 * {@code test*BinomialEngines} grids from
 * {@code test-suite/europeanoption.cpp}: calls and puts, strikes
 * 75/100/125, one year, spot 100, q in {0, 0.05}, r in {0.01, 0.05, 0.15},
 * vols in {0.11, 0.50, 1.20}. Errors are the official
 * {@code relativeError(expected, calculated, spot)}.
 */
public class BinomialVanillaEngineTest {
    private static final double DELTA_TOLERANCE = 1.0e-3;
    private static final double GAMMA_TOLERANCE = 1.0e-4;
    private static final double THETA_TOLERANCE = 0.03;

    @Test
    public void testJRBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.JARROW_RUDD,
                251, 0.002);
    }

    @Test
    public void testCRRBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.COX_ROSS_RUBINSTEIN,
                501, 0.02);
    }

    @Test
    public void testEQPBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.ADDITIVE_EQP,
                501, 0.02);
    }

    @Test
    public void testTGEOBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.TRIGEORGIS,
                251, 0.002);
    }

    @Test
    public void testTIANBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.TIAN,
                251, 0.002);
    }

    @Test
    public void testLRBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.LEISEN_REIMER,
                251, 1.0e-6);
    }

    @Test
    public void testJOSHIBinomialEngines() {
        testEngineConsistency(BinomialVanillaEngine.TreeType.JOSHI,
                251, 1.0e-7);
    }

    private static void testEngineConsistency(BinomialVanillaEngine.TreeType treeType,
                                              int binomialSteps,
                                              double valueTolerance) {
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
        BinomialVanillaEngine binomialEngine = new BinomialVanillaEngine(
                process, binomialSteps, treeType);

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
                    option.setPricingEngine(binomialEngine);

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    double q = m, r = n;
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    Map<String, Double> expected = new LinkedHashMap<>();
                                    Map<String, Double> calculated = new LinkedHashMap<>();
                                    Map<String, Double> tolerance = new LinkedHashMap<>();

                                    expected.put("value", refOption.NPV());
                                    calculated.put("value", option.NPV());
                                    tolerance.put("value", valueTolerance);

                                    if (option.NPV() > spot.value() * 1.0e-5) {
                                        expected.put("delta", refOption.delta());
                                        expected.put("gamma", refOption.gamma());
                                        expected.put("theta", refOption.theta());
                                        calculated.put("delta", option.delta());
                                        calculated.put("gamma", option.gamma());
                                        calculated.put("theta", option.theta());
                                        tolerance.put("delta", DELTA_TOLERANCE);
                                        tolerance.put("gamma", GAMMA_TOLERANCE);
                                        tolerance.put("theta", THETA_TOLERANCE);
                                    }

                                    for (Map.Entry<String, Double> entry : calculated.entrySet()) {
                                        String greek = entry.getKey();
                                        double expct = expected.get(greek);
                                        double calcl = entry.getValue();
                                        double tol = tolerance.get(greek);
                                        double error = relativeError(expct, calcl, u);
                                        assertTrue(error <= tol,
                                                treeType + " " + type + " option, strike "
                                                        + strike + ", q " + q + ", r " + r
                                                        + ", vol " + v + ": expected " + greek
                                                        + " " + expct + ", calculated " + calcl
                                                        + ", error " + error
                                                        + ", tolerance " + tol);
                                        assertFalse(Double.isNaN(calcl),
                                                treeType + " " + greek + " is NaN");
                                    }
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
