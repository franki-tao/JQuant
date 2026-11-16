package pricingengines;

import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Payoff;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.BlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ForwardCurve;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite-difference European engine checked against analytic results.
 *
 * <p>Ports {@code testFdEngines} and {@code testEngineConsistency} from
 * {@code test-suite/europeanoption.cpp}: calls and puts, strikes 75/100/125,
 * one year, spot 100, q in {0, 0.05}, r in {0.01, 0.05, 0.15}, vols in
 * {0.11, 0.50, 1.20}. Errors are the official
 * {@code relativeError(expected, calculated, spot)}.
 */
public class FdBlackScholesVanillaEngineTest {
    private static final double VALUE_TOLERANCE = 1.0e-4;
    private static final double DELTA_TOLERANCE = 1.0e-6;
    private static final double GAMMA_TOLERANCE = 1.0e-6;
    private static final double THETA_TOLERANCE = 1.0e-3;

    @Test
    public void testFdEngines() {
        final int timeSteps = 500;
        final int gridPoints = 500;

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
        FdBlackScholesVanillaEngine fdEngine = new FdBlackScholesVanillaEngine(
                process, timeSteps, gridPoints);

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
                    option.setPricingEngine(fdEngine);

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
                                    tolerance.put("value", VALUE_TOLERANCE);

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
                                                "FD " + type + " option, strike " + strike
                                                        + ", q " + q + ", r " + r
                                                        + ", vol " + v + ": expected " + greek
                                                        + " " + expct + ", calculated " + calcl
                                                        + ", error " + error
                                                        + ", tolerance " + tol);
                                        assertFalse(Double.isNaN(calcl),
                                                "FD " + greek + " is NaN");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Port of {@code testPDESchemes} from
     * {@code test-suite/europeanoption.cpp}.
     */
    @Test
    public void testPDESchemes() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(18, Month.FEBRUARY, 2018);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.06);
        SimpleQuote rRate = new SimpleQuote(0.10);
        SimpleQuote vol = new SimpleQuote(0.35);

        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        Date maturity = today.add(new Period(6, TimeUnit.MONTHS));
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, spot.value());
        EuropeanExercise exercise = new EuropeanExercise(maturity);
        EuropeanOption option = new EuropeanOption(payoff, exercise);

        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        double expected = option.NPV();

        Map<String, PricingEngine> engines = new LinkedHashMap<>();
        engines.put("Douglas", new FdBlackScholesVanillaEngine(
                process, 15, 100, 0, FdmSchemeDesc.Douglas()));
        engines.put("Crank-Nicolson", new FdBlackScholesVanillaEngine(
                process, 15, 100, 0, FdmSchemeDesc.CrankNicolson()));
        engines.put("Implicit-Euler", new FdBlackScholesVanillaEngine(
                process, 500, 100, 0, FdmSchemeDesc.ImplicitEuler()));
        engines.put("Explicit-Euler", new FdBlackScholesVanillaEngine(
                process, 1000, 100, 0, FdmSchemeDesc.ExplicitEuler()));
        engines.put("Method-of-Lines", new FdBlackScholesVanillaEngine(
                process, 1, 100, 0, FdmSchemeDesc.MethodOfLines()));
        engines.put("Hundsdorfer", new FdBlackScholesVanillaEngine(
                process, 10, 100, 0, FdmSchemeDesc.Hundsdorfer()));
        engines.put("Craig-Sneyd", new FdBlackScholesVanillaEngine(
                process, 10, 100, 0, FdmSchemeDesc.CraigSneyd()));
        engines.put("Modified Craig-Sneyd", new FdBlackScholesVanillaEngine(
                process, 15, 100, 0, FdmSchemeDesc.ModifiedCraigSneyd()));
        engines.put("TR-BDF2", new FdBlackScholesVanillaEngine(
                process, 15, 100, 0, FdmSchemeDesc.TrBDF2()));

        double tol = 0.006;
        for (Map.Entry<String, PricingEngine> entry : engines.entrySet()) {
            option.setPricingEngine(entry.getValue());
            double calculated = option.NPV();
            double diff = Math.abs(expected - calculated);

            assertTrue(diff <= tol,
                    "Failed to reproduce European option values with the "
                            + entry.getKey() + " PDE scheme"
                            + "\n    calculated: " + calculated
                            + "\n    expected:   " + expected
                            + "\n    difference: " + diff
                            + "\n    tolerance:  " + tol);
        }
    }

    /**
     * Port of {@code testDouglasVsCrankNicolson} from
     * {@code test-suite/europeanoption.cpp}.
     */
    @Test
    public void testDouglasVsCrankNicolson() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(5, Month.OCTOBER, 2018);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.02);
        SimpleQuote rRate = new SimpleQuote(0.075);
        SimpleQuote vol = new SimpleQuote(0.25);

        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Put, spot.value() + 2),
                new EuropeanExercise(today.add(new Period(6, TimeUnit.MONTHS))));

        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        double npv = option.NPV();

        double schemeTol = 1e-12;
        double npvTol = 1e-2;

        for (double theta = 0.2; theta < 0.81; theta += 0.1) {
            option.setPricingEngine(new FdBlackScholesVanillaEngine(
                    process, 500, 100, 0,
                    new FdmSchemeDesc(FdmSchemeDesc.FdmSchemeType.CrankNicolsonType,
                            theta, 0.0)));
            double crankNicolsonNPV = option.NPV();

            double npvDiff = Math.abs(crankNicolsonNPV - npv);
            assertTrue(npvDiff <= npvTol,
                    "Failed to reproduce european option values "
                            + "with the Crank-Nicolson PDE scheme "
                            + "\n    Analytic NPV:       " + npv
                            + "\n    Crank-Nicolson NPV: " + crankNicolsonNPV
                            + "\n    theta:              " + theta
                            + "\n    difference:         " + npvDiff
                            + "\n    tolerance:          " + npvTol);

            option.setPricingEngine(new FdBlackScholesVanillaEngine(
                    process, 500, 100, 0,
                    new FdmSchemeDesc(FdmSchemeDesc.FdmSchemeType.DouglasType,
                            theta, 0.0)));
            double douglasNPV = option.NPV();

            double schemeDiff = Math.abs(crankNicolsonNPV - douglasNPV);
            assertTrue(schemeDiff <= schemeTol,
                    "Failed to reproduce Douglas scheme option values "
                            + "with the Crank-Nicolson PDE scheme "
                            + "\n    Douglas NPV:        " + douglasNPV
                            + "\n    Crank-Nicolson NPV: " + crankNicolsonNPV
                            + "\n    difference:         " + schemeDiff
                            + "\n    tolerance:          " + schemeTol);
        }
    }

    /**
     * Port of {@code testFdEngineWithNonConstantParameters} from
     * {@code test-suite/europeanoption.cpp}.
     */
    @Test
    public void testFdEngineWithNonConstantParameters() {
        double u = 190.0;
        double v = 0.20;

        DayCounter dc = new Actual360(false);
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(u);
        BlackVolTermStructure volTS = flatVol(today, new SimpleQuote(v), dc);

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        dates.add(today);          rates.add(0.0);
        dates.add(today.add(90));  rates.add(0.001);
        dates.add(today.add(180)); rates.add(0.002);
        dates.add(today.add(270)); rates.add(0.005);
        dates.add(today.add(360)); rates.add(0.01);
        YieldTermStructure rTS = new ForwardCurve(dates, rates, dc);
        double r = rTS.zeroRate(dates.get(4), dc, Compounding.Continuous,
                Frequency.ANNUAL, false).rate();

        BlackScholesProcess process = new BlackScholesProcess(
                new Handle<>(spot, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 190.0),
                new EuropeanExercise(today.add(360)));

        option.setPricingEngine(new AnalyticEuropeanEngine(process));
        double expected = option.NPV();

        int timeSteps = 200;
        int gridPoints = 201;
        option.setPricingEngine(new FdBlackScholesVanillaEngine(
                process, timeSteps, gridPoints));
        double calculated = option.NPV();

        double tolerance = 0.01;
        double error = Math.abs(expected - calculated);
        assertTrue(error <= tolerance,
                "Failed to reproduce finite-difference European value with "
                        + "non-constant parameters"
                        + "\n    spot:       " + u
                        + "\n    rate:       " + r
                        + "\n    volatility: " + v
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance);
    }

    /**
     * Black-Scholes part of {@code testFdEnginesWithNonStrikedPayoff} from
     * {@code test-suite/europeanoption.cpp}. The official Heston and CIR
     * engines are not available in this port and are intentionally omitted.
     */
    @Test
    public void testFdEnginesWithNonStrikedPayoff() {
        class CappedCallPayoff implements Payoff {
            @Override
            public String name() {
                return "CappedCall";
            }

            @Override
            public String description() {
                return name();
            }

            @Override
            public double value(double s) {
                return Math.min(Math.max(s - 100.0, 0.0), 20.0);
            }
        }

        Date today = new Date(15, Month.MAY, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360(false);

        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure rTS = flatRate(today, new SimpleQuote(0.03), dc);
        YieldTermStructure qTS = flatRate(today, new SimpleQuote(0.0), dc);
        BlackVolTermStructure volTS = flatVol(today, new SimpleQuote(0.2), dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        EuropeanOption option = new EuropeanOption(
                new CappedCallPayoff(),
                new EuropeanExercise(today.add(new Period(1, TimeUnit.YEARS))));

        option.setPricingEngine(new FdBlackScholesVanillaEngine(process));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, option::NPV);
        assertTrue(exception.getMessage().contains("non-striked payoff given"),
                "expected the non-striked payoff error, got: "
                        + exception.getMessage());
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
