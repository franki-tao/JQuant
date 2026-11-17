package pricingengines;

import jquant.AmericanExercise;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Payoff;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.Dividend;
import jquant.indexes.IndexManager;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BaroneAdesiWhaleyApproximationEngine;
import jquant.pricingengines.vanilla.BjerksundStenslandApproximationEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.pricingengines.vanilla.JuQuadraticApproximationEngine;
import jquant.pricingengines.vanilla.MakeFdBlackScholesVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
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
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.Compounding.Continuous;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * American analytic approximations from QuantLib
 * {@code test-suite/americanoption.cpp}.
 *
 * <p>Barone-Adesi-Whaley, Bjerksund-Stensland and Ju quadratic.
 */
public class AmericanOptionTest {

    private record Data(Option.Type type, double strike, double s, double q,
                        double r, double t, double vol, double result) {
    }

    private record OptionSpec(double spot, double strike, int maturityInDays,
                              double volatility, double r, double q) {
    }

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

    // ------------------------------------------------------------------
    // Barone-Adesi-Whaley
    // ------------------------------------------------------------------

    @Test
    public void testBaroneAdesiWhaleyValues() {
        // "Option pricing formulas", E.G. Haug, McGraw-Hill 1998, pag 24.
        Data[] values = {
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 0.0206),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8771),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 10.0089),
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 0.3159),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1280),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 10.3919),
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 0.9495),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3777),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 11.1679),
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 0.8208),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0842),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 10.8087),
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 2.7437),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.8015),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 13.0170),
                new Data(Option.Type.Call, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 5.0063),
                new Data(Option.Type.Call, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.5106),
                new Data(Option.Type.Call, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 15.5689),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.15, 10.0000),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.15, 1.8770),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.15, 0.0410),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.25, 10.2533),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.25, 3.1277),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.25, 0.4562),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.10, 0.35, 10.8787),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.10, 0.35, 4.3777),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.10, 0.35, 1.2402),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.15, 10.5595),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.15, 4.0842),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.15, 1.0822),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.25, 12.4419),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.25, 6.8014),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.25, 3.3226),
                new Data(Option.Type.Put, 100.00, 90.00, 0.10, 0.10, 0.50, 0.35, 14.6945),
                new Data(Option.Type.Put, 100.00, 100.00, 0.10, 0.10, 0.50, 0.35, 9.5104),
                new Data(Option.Type.Put, 100.00, 110.00, 0.10, 0.10, 0.50, 0.35, 5.8823),
                new Data(Option.Type.Put, 100.00, 100.00, 0.00, 0.00, 0.50, 0.15, 4.2294)
        };

        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        BaroneAdesiWhaleyApproximationEngine engine =
                new BaroneAdesiWhaleyApproximationEngine(process);

        double tolerance = 3.0e-3;
        for (Data value : values) {
            Date exDate = today.add(timeToDays(value.t));
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.vol);

            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(value.type, value.strike),
                    new AmericanExercise(today, exDate));
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double error = Math.abs(calculated - value.result);
            if (error > tolerance) {
                fail(reportFailure("value", value, today, exDate, value.result,
                        calculated, error, tolerance));
            }
        }
    }

    @Test
    public void testBaroneAdesiWhaleyValuesAtNonPositiveRate() {
        Date today = new Date(19, Month.AUGUST, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.30);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        Date maturity = today.add(new Period(1, TimeUnit.YEARS));
        AmericanExercise american = new AmericanExercise(today, maturity);
        EuropeanExercise european = new EuropeanExercise(maturity);

        double tolerance = 1.0e-6;
        for (double r : new double[]{0.0, -1.0e-10, -0.05}) {
            for (double q : new double[]{0.0, 0.04}) {
                for (double s : new double[]{70.0, 85.0, 100.0, 115.0, 130.0}) {
                    spot.setValue(s);
                    qRate.setValue(q);
                    rRate.setValue(r);

                    Payoff payoff = new PlainVanillaPayoff(Option.Type.Put, 100.0);

                    EuropeanOption reference = new EuropeanOption(payoff, european);
                    reference.setPricingEngine(new AnalyticEuropeanEngine(process));
                    double expected = reference.NPV();

                    EuropeanOption option = new EuropeanOption(payoff, american);
                    option.setPricingEngine(
                            new BaroneAdesiWhaleyApproximationEngine(process));
                    double calculated = option.NPV();

                    double error = Math.abs(calculated - expected);
                    if (!(error <= tolerance)) {
                        fail("value at non-positive rate: type=" + Option.Type.Put
                                + " spot=" + s + " strike=100.0 q=" + q + " r=" + r
                                + " today=" + today + " maturity=" + maturity
                                + " vol=" + vol.value() + " expected=" + expected
                                + " calculated=" + calculated + " error=" + error
                                + " tolerance=" + tolerance);
                    }
                }
            }
        }
    }

    @Test
    public void testBaroneAdesiWhaleyNegativeRates() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360(false);

        SimpleQuote spot = new SimpleQuote(36.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(-0.012);
        SimpleQuote vol = new SimpleQuote(0.20);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));

        PlainVanillaPayoff putPayoff = new PlainVanillaPayoff(Option.Type.Put, 40.0);
        Date exDate = today.add(new Period(1, TimeUnit.YEARS));
        AmericanExercise exercise = new AmericanExercise(today, exDate);

        EuropeanOption option = new EuropeanOption(putPayoff, exercise);
        option.setPricingEngine(
                new BaroneAdesiWhaleyApproximationEngine(process));

        EuropeanOption europeanPut = new EuropeanOption(putPayoff,
                new EuropeanExercise(exDate));
        europeanPut.setPricingEngine(new AnalyticEuropeanEngine(process));
        assertAbs("BAW European shortcut at negative rate",
                europeanPut.NPV(), option.NPV(), 1.0e-6);

        option.setPricingEngine(new JuQuadraticApproximationEngine(process));
        assertAbs("Ju European shortcut at negative rate",
                europeanPut.NPV(), option.NPV(), 1.0e-6);

        // also verify with a call and positive dividends
        PlainVanillaPayoff callPayoff = new PlainVanillaPayoff(Option.Type.Call, 40.0);
        qRate.setValue(0.06);
        EuropeanOption callOption = new EuropeanOption(callPayoff, exercise);
        callOption.setPricingEngine(
                new BaroneAdesiWhaleyApproximationEngine(process));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                callOption::NPV);
        assertTrue(error.getMessage().contains("negative interest rates"),
                "unexpected error: " + error.getMessage());

        // With r < q <= 0 early exercise is optimal, so the European shortcut
        // must not fire. Ju goes through the same critical price.
        spot.setValue(100.0);
        qRate.setValue(0.0);
        rRate.setValue(-0.05);
        vol.setValue(0.03);

        PlainVanillaPayoff itmCallPayoff =
                new PlainVanillaPayoff(Option.Type.Call, 80.0);
        AmericanExercise longExercise = new AmericanExercise(today,
                today.add(new Period(3, TimeUnit.YEARS)));
        EuropeanOption itmCall = new EuropeanOption(itmCallPayoff, longExercise);

        itmCall.setPricingEngine(
                new BaroneAdesiWhaleyApproximationEngine(process));
        error = assertThrows(IllegalArgumentException.class, itmCall::NPV);
        assertTrue(error.getMessage().contains("negative interest rates"),
                "unexpected error: " + error.getMessage());

        itmCall.setPricingEngine(new JuQuadraticApproximationEngine(process));
        error = assertThrows(IllegalArgumentException.class, itmCall::NPV);
        assertTrue(error.getMessage().contains("negative interest rates"),
                "unexpected error: " + error.getMessage());
    }

    // ------------------------------------------------------------------
    // Bjerksund-Stensland
    // ------------------------------------------------------------------

    @Test
    public void testBjerksundStenslandValues() {
        Data[] values = {
                // Haug, McGraw-Hill 1998, pag 27
                new Data(Option.Type.Call, 40.00, 42.00, 0.08, 0.04, 0.75, 0.35, 5.2704),
                // Haug VBA code
                new Data(Option.Type.Put, 40.00, 36.00, 0.00, 0.06, 1.00, 0.20, 4.4531),
                // ATM option with very small volatility, reference from R
                new Data(Option.Type.Call, 100, 100, 0.05, 0.05, 1.0, 0.0021, 0.08032314),
                // ATM option with very small volatility, reference from BAW
                new Data(Option.Type.Call, 100, 100, 0.05, 0.05, 1.0, 0.0001, 0.003860656),
                new Data(Option.Type.Call, 100, 99.99, 0.05, 0.05, 1.0, 0.0001, 0.00081),
                // ITM option with a very small volatility
                new Data(Option.Type.Call, 100, 110, 0.05, 0.05, 1.0, 0.0001, 10.0),
                new Data(Option.Type.Put, 110, 100, 0.05, 0.05, 1.0, 0.0001, 10.0),
                // OTM option with a very large volatility; QdFp high precision
                // 99.20350, CRR 4001 steps 99.20338
                new Data(Option.Type.Put, 100, 110, 0.05, 0.05, 1.0, 10, 99.20350)
        };

        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        BjerksundStenslandApproximationEngine engine =
                new BjerksundStenslandApproximationEngine(process);

        double tolerance = 5.0e-5;
        for (Data value : values) {
            Date exDate = today.add(timeToDays(value.t));
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.vol);

            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(value.type, value.strike),
                    new AmericanExercise(today, exDate));
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double error = Math.abs(calculated - value.result);
            if (error > tolerance) {
                fail(reportFailure("value", value, today, exDate, value.result,
                        calculated, error, tolerance));
            }
        }
    }

    @Test
    public void testBjerksundStenslandAtUnitBeta() {
        Date today = new Date(15, Month.AUGUST, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        AmericanExercise exercise = new AmericanExercise(today,
                today.add(new Period(5, TimeUnit.YEARS)));

        record Case(Option.Type type, double r, double q, double rBumped,
                    double qBumped) {
        }
        Case[] cases = {
                new Case(Option.Type.Call, -0.005, 0.0, -0.005, 1.0e-6),
                new Case(Option.Type.Put, 0.0, -0.005, 1.0e-6, -0.005)
        };

        for (Case c : cases) {
            Payoff payoff = new PlainVanillaPayoff(c.type, 100.0);

            EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(engineForUnitBeta(spot, today, dc, c.r, c.q));
            EuropeanOption bumped = new EuropeanOption(payoff, exercise);
            bumped.setPricingEngine(
                    engineForUnitBeta(spot, today, dc, c.rBumped, c.qBumped));

            checkRelative("value", c, option.NPV(), bumped.NPV());
            checkRelative("delta", c, option.delta(), bumped.delta());
            checkRelative("gamma", c, option.gamma(), bumped.gamma());
            checkRelative("rho", c, option.rho(), bumped.rho());
            checkRelative("dividend rho", c, option.dividendRho(), bumped.dividendRho());
            checkRelative("vega", c, option.vega(), bumped.vega());
        }

        // where r = -sigma^2/2 exactly, rounding can take the radicand of beta
        // below zero
        PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        EuropeanOption kink = new EuropeanOption(call, exercise);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, 0.0, new Actual360(false)), true),
                new Handle<>(flatRate(today, -0.02, new Actual360(false)), true),
                new Handle<>(flatVol(today, new SimpleQuote(0.20),
                        new Actual360(false)), true));
        kink.setPricingEngine(new BjerksundStenslandApproximationEngine(process));
        assertTrue(Double.isFinite(kink.NPV()), "kink NPV is not finite: " + kink.NPV());
    }

    private static BjerksundStenslandApproximationEngine engineForUnitBeta(
            SimpleQuote spot, Date today, DayCounter dc, double r, double q) {
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, r, dc), true),
                new Handle<>(flatVol(today, new SimpleQuote(0.20), dc), true));
        return new BjerksundStenslandApproximationEngine(process);
    }

    private static void checkRelative(String name, Object c, double calculated,
                                      double expected) {
        double error = Math.abs(calculated - expected);
        double tolerance = 1.0e-3 * Math.max(1.0, Math.abs(expected));
        if (!(error <= tolerance)) {
            fail(name + " " + c + ": expected=" + expected + " calculated="
                    + calculated + " error=" + error + " tolerance=" + tolerance);
        }
    }

    @Test
    public void testBjerksundStenslandEuropeanGreeks() {
        Date today = new Date(5, Month.NOVEMBER, 2022);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(100);
        double strike = 105;
        double sigma = 0.40;
        Date maturityDate = today.add(new Period(724, TimeUnit.DAYS));

        SimpleQuote qTS = new SimpleQuote(0.0);
        SimpleQuote rTS = new SimpleQuote(0.0);
        DayCounter divDc = new Actual365Fixed();
        DayCounter rfDc = new Actual360(false);
        DayCounter volDc = new Thirty360(Thirty360.Convention.European);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qTS, divDc), true),
                new Handle<>(flatRate(today, rTS, rfDc), true),
                new Handle<>(flatVol(today, new SimpleQuote(sigma), volDc), true));

        record Spec(Option.Type type, double r, double q) {
        }
        Spec[] specs = {
                new Spec(Option.Type.Put, -0.05, 0.02),
                new Spec(Option.Type.Call, 0.05, -0.025)
        };

        EuropeanExercise europeanExercise = new EuropeanExercise(maturityDate);
        AmericanExercise americanExercise = new AmericanExercise(today, maturityDate);

        AnalyticEuropeanEngine europeanEngine = new AnalyticEuropeanEngine(process);
        BjerksundStenslandApproximationEngine bsEngine =
                new BjerksundStenslandApproximationEngine(process);

        double tol = 1000 * QL_EPSILON;
        for (Spec spec : specs) {
            qTS.setValue(spec.q);
            rTS.setValue(spec.r);

            EuropeanOption american = new EuropeanOption(
                    new PlainVanillaPayoff(spec.type, strike), americanExercise);
            american.setPricingEngine(bsEngine);

            EuropeanOption european = new EuropeanOption(
                    new PlainVanillaPayoff(spec.type, strike), europeanExercise);
            european.setPricingEngine(europeanEngine);

            String prefix = spec.type + " r=" + spec.r + " q=" + spec.q;
            checkClose(prefix + " NPV", european.NPV(), american.NPV(), tol);
            checkClose(prefix + " delta", european.delta(), american.delta(), tol);
            checkClose(prefix + " strikeSensitivity",
                    european.strikeSensitivity(), american.strikeSensitivity(), tol);
            checkClose(prefix + " gamma", european.gamma(), american.gamma(), tol);
            checkClose(prefix + " vega", european.vega(), american.vega(), tol);
            checkClose(prefix + " theta", european.theta(), american.theta(), tol);
            checkClose(prefix + " thetaPerDay",
                    european.thetaPerDay(), american.thetaPerDay(), tol);
            checkClose(prefix + " rho", european.rho(), american.rho(), tol);
            checkClose(prefix + " dividendRho",
                    european.dividendRho(), american.dividendRho(), tol);
        }
    }

    @Test
    public void testBjerksundStenslandAmericanGreeks() {
        Date today = new Date(5, Month.DECEMBER, 2022);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0);
        SimpleQuote vol = new SimpleQuote(0);
        SimpleQuote qRate = new SimpleQuote(0);
        SimpleQuote rRate = new SimpleQuote(0);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, new Actual360(false)), true),
                new Handle<>(flatRate(today, rRate, new Actual365Fixed()), true),
                new Handle<>(flatVol(today, vol,
                        new Thirty360(Thirty360.Convention.ISDA)), true));

        BjerksundStenslandApproximationEngine engine =
                new BjerksundStenslandApproximationEngine(process);

        double strike = 100;
        Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        double[] rf = {0.0, 0.02, 0.06, 0.1, 0.2};
        double[] qy = {0.0, 0.08, 0.12};
        double[] sig = {0.1, 0.2, 0.4, 1.0};
        double[] spots = {25, 50, 99.9, 110, 150, 200};
        int[] maturities = {30, 182, 365, 1825};

        double f_d = 1e-5;
        double f_g = 5e-5;
        double f_q = 1e-6;

        for (Option.Type type : types) {
            for (int t : maturities) {
                Date maturityDate = today.add(new Period(t, TimeUnit.DAYS));
                Payoff stdPayoff = new PlainVanillaPayoff(type, strike);

                EuropeanOption option = new EuropeanOption(stdPayoff,
                        new AmericanExercise(today, maturityDate));
                option.setPricingEngine(engine);

                EuropeanOption strikeUp = new EuropeanOption(
                        new PlainVanillaPayoff(type, strike * (1 + f_d)),
                        new AmericanExercise(today, maturityDate));
                strikeUp.setPricingEngine(engine);

                EuropeanOption strikeDown = new EuropeanOption(
                        new PlainVanillaPayoff(type, strike * (1 - f_d)),
                        new AmericanExercise(today, maturityDate));
                strikeDown.setPricingEngine(engine);

                EuropeanOption dayUp = new EuropeanOption(stdPayoff,
                        new AmericanExercise(today,
                                maturityDate.add(new Period(1, TimeUnit.DAYS))));
                dayUp.setPricingEngine(engine);

                EuropeanOption dayDown = new EuropeanOption(stdPayoff,
                        new AmericanExercise(today,
                                maturityDate.substract(new Period(1, TimeUnit.DAYS))));
                dayDown.setPricingEngine(engine);

                for (double r : rf) {
                    rRate.setValue(r);
                    for (double q : qy) {
                        qRate.setValue(q);
                        for (double v : sig) {
                            vol.setValue(v);
                            for (double s : spots) {
                                spot.setValue(s);

                                String prefix = type + " spot=" + s + " strike="
                                        + strike + " q=" + q + " r=" + r + " today="
                                        + today + " maturity=" + maturityDate
                                        + " vol=" + v;

                                double npv = option.NPV();
                                double delta = option.delta();
                                double gamma = option.gamma();
                                double strikeSensitivity = option.strikeSensitivity();
                                double dividendRho = option.dividendRho();
                                double rho = option.rho();
                                double vega = option.vega();
                                double theta = option.theta();
                                String exerciseType = (String)
                                        option.additionalResults().get("exerciseType");

                                spot.setValue(s * (1 + f_d));
                                double f2 = option.NPV();
                                spot.setValue(s * (1 - f_d));
                                double f1 = option.NPV();
                                spot.setValue(s);
                                double numericalDelta = (f2 - f1) / (2 * f_d * s);

                                double error = Math.abs(delta - numericalDelta);
                                if (error > 5e-6) {
                                    fail(prefix + " delta: expected=" + numericalDelta
                                            + " calculated=" + delta + " error="
                                            + error + " tolerance=" + 5e-6);
                                }

                                spot.setValue(s * (1 + 2 * f_g));
                                double gp2 = option.NPV();
                                spot.setValue(s * (1 + f_g));
                                double gp1 = option.NPV();
                                spot.setValue(s * (1 - f_g));
                                double gm1 = option.NPV();
                                spot.setValue(s * (1 - 2 * f_g));
                                double gm2 = option.NPV();
                                spot.setValue(s);
                                double numericalGamma = (-gp2 + 16 * gp1 - 30 * npv
                                        + 16 * gm1 - gm2) / (12 * f_g * s * f_g * s);

                                error = Math.abs(gamma - numericalGamma);
                                if (error > 1e-4 && t < 1000) {
                                    fail(prefix + " gamma: expected=" + numericalGamma
                                            + " calculated=" + gamma + " error="
                                            + error + " tolerance=" + 1e-4);
                                }

                                double k2 = strikeUp.NPV();
                                double k1 = strikeDown.NPV();
                                double numericalStrikeSensitivity =
                                        (k2 - k1) / (2 * f_d * strike);
                                error = Math.abs(strikeSensitivity
                                        - numericalStrikeSensitivity);
                                if (error > 5e-6) {
                                    fail(prefix + " strikeSensitivity: expected="
                                            + numericalStrikeSensitivity
                                            + " calculated=" + strikeSensitivity
                                            + " error=" + error + " tolerance=" + 5e-6);
                                }

                                if (q != 0.0) {
                                    qRate.setValue(q + f_q);
                                    double q2 = option.NPV();
                                    qRate.setValue(q - f_q);
                                    double q1 = option.NPV();
                                    qRate.setValue(q);
                                    double numericalDividendRho =
                                            (q2 - q1) / (2 * f_q);

                                    error = Math.abs(dividendRho
                                            - numericalDividendRho);
                                    if (error > 3e-2) {
                                        fail(prefix + " dividendRho: expected="
                                                + numericalDividendRho
                                                + " calculated=" + dividendRho
                                                + " error=" + error
                                                + " tolerance=" + 3e-2);
                                    }

                                    rRate.setValue(r + f_q);
                                    double r2 = option.NPV();
                                    rRate.setValue(r - f_q);
                                    double r1 = option.NPV();
                                    rRate.setValue(r);
                                    double numericalRho = (r2 - r1) / (2 * f_q);

                                    error = Math.abs(rho - numericalRho);
                                    if (error > 3e-2) {
                                        fail(prefix + " rho: expected="
                                                + numericalRho + " calculated=" + rho
                                                + " error=" + error
                                                + " tolerance=" + 3e-2);
                                    }
                                }

                                vol.setValue(v + f_d);
                                double v2 = option.NPV();
                                vol.setValue(v - f_d);
                                double v1 = option.NPV();
                                vol.setValue(v);
                                double numericalVega = (v2 - v1) / (2 * f_d);

                                error = Math.abs(vega - numericalVega);
                                if (error > 5e-4) {
                                    fail(prefix + " vega: expected=" + numericalVega
                                            + " calculated=" + vega + " error="
                                            + error + " tolerance=" + 5e-4);
                                }

                                if ("American".equals(exerciseType)) {
                                    double t2 = dayUp.NPV();
                                    double t1 = dayDown.NPV();
                                    double numericalThetaPerDay = (t1 - t2) / 2;
                                    double numericalTheta = 365 * numericalThetaPerDay;
                                    error = Math.abs(theta - numericalTheta);
                                    double thetaTol = (t < 60) ? 3.0 : 5e-4;
                                    if (error > thetaTol) {
                                        fail(prefix + " theta: expected="
                                                + numericalTheta + " calculated="
                                                + theta + " error=" + error
                                                + " tolerance=" + thetaTol);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSingleBjerksundStenslandGreeks() {
        Date today = new Date(20, Month.JANUARY, 2023);
        Settings.instance.evaluationDate().equal(today);

        double s = 100;
        double v = 0.3;
        double q = 0.04;
        double r = 0.07;

        SimpleQuote spot = new SimpleQuote(s);
        SimpleQuote vol = new SimpleQuote(v);
        SimpleQuote qRate = new SimpleQuote(q);
        SimpleQuote rRate = new SimpleQuote(r);

        DayCounter dc = new Actual365Fixed();
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, qRate, dc), true),
                new Handle<>(flatRate(today, rRate, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));

        Date maturityDate = today.add(new Period(2, TimeUnit.YEARS));
        AmericanExercise exercise = new AmericanExercise(today, maturityDate);
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100);

        EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(
                new BjerksundStenslandApproximationEngine(process));

        double npv = option.NPV();
        double delta = option.delta();
        double gamma = option.gamma();
        double strikeSensitivity = option.strikeSensitivity();
        double divRho = option.dividendRho();
        double rho = option.rho();
        double vega = option.vega();
        double theta = option.theta();
        double thetaPerDay = option.thetaPerDay();
        Object exerciseType = option.additionalResults().get("exerciseType");

        double expectedNpv = 17.9251834488399169;
        double expectedDelta = 0.590801845261082592;
        double expectedGamma = 0.00825347110063545664;
        double expectedStrikeSensitivity = -0.411550010772683383;
        double expectedDivRho = -114.137818682236826;
        double expectedRho = 80.4900013901554416;
        double expectedVega = 49.2906331545933227;
        double expectedTheta = -4.22540293840206704;

        double tol = 1e6 * QL_EPSILON;
        assertAbs("npv", expectedNpv, npv, tol);
        assertAbs("delta", expectedDelta, delta, tol);
        assertAbs("gamma", expectedGamma, gamma, tol);
        assertAbs("strikeSensitivity", expectedStrikeSensitivity,
                strikeSensitivity, tol);
        assertAbs("dividendRho", expectedDivRho, divRho, tol);
        assertAbs("rho", expectedRho, rho, tol);
        assertAbs("vega", expectedVega, vega, tol);
        assertAbs("theta", expectedTheta, theta, tol);
        assertAbs("thetaPerDay", expectedTheta / 365, thetaPerDay, tol);

        assertTrue("American".equals(exerciseType),
                "American exercise type expected, got " + exerciseType);
    }

    // ------------------------------------------------------------------
    // Ju quadratic
    // ------------------------------------------------------------------

    private static Data[] juValues() {
        return new Data[] {
                // Exhibit 3 - Short dated Put Options
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.006),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 0.201),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 0.433),

                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 0.851),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 1.576),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 1.984),

                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.2, 5.000),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.2, 5.084),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.2, 5.260),

                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 0.078),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 0.697),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 1.218),

                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 1.309),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 2.477),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 3.161),

                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.3, 5.059),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.3, 5.699),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.3, 6.231),

                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 0.247),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 1.344),
                new Data(Option.Type.Put, 35.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 2.150),

                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 1.767),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 3.381),
                new Data(Option.Type.Put, 40.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 4.342),

                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.0833, 0.4, 5.288),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.3333, 0.4, 6.501),
                new Data(Option.Type.Put, 45.00, 40.00, 0.0, 0.0488, 0.5833, 0.4, 7.367),

                // Exhibit 6 - Long dated Call Options with dividends
                new Data(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.2, 2.605),
                new Data(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.2, 5.182),
                new Data(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.2, 9.065),
                new Data(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.2, 14.430),
                new Data(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.2, 21.398),

                new Data(Option.Type.Call, 100.00, 80.00, 0.07, 0.03, 3.0, 0.4, 11.336),
                new Data(Option.Type.Call, 100.00, 90.00, 0.07, 0.03, 3.0, 0.4, 15.711),
                new Data(Option.Type.Call, 100.00, 100.00, 0.07, 0.03, 3.0, 0.4, 20.760),
                new Data(Option.Type.Call, 100.00, 110.00, 0.07, 0.03, 3.0, 0.4, 26.440),
                new Data(Option.Type.Call, 100.00, 120.00, 0.07, 0.03, 3.0, 0.4, 32.709),

                new Data(Option.Type.Call, 100.00, 80.00, 0.07, 0.00001, 3.0, 0.3, 5.552),
                new Data(Option.Type.Call, 100.00, 90.00, 0.07, 0.00001, 3.0, 0.3, 8.868),
                new Data(Option.Type.Call, 100.00, 100.00, 0.07, 0.00001, 3.0, 0.3, 13.158),
                new Data(Option.Type.Call, 100.00, 110.00, 0.07, 0.00001, 3.0, 0.3, 18.458),
                new Data(Option.Type.Call, 100.00, 120.00, 0.07, 0.00001, 3.0, 0.3, 24.786),

                new Data(Option.Type.Call, 100.00, 80.00, 0.03, 0.07, 3.0, 0.3, 12.177),
                new Data(Option.Type.Call, 100.00, 90.00, 0.03, 0.07, 3.0, 0.3, 17.411),
                new Data(Option.Type.Call, 100.00, 100.00, 0.03, 0.07, 3.0, 0.3, 23.402),
                new Data(Option.Type.Call, 100.00, 110.00, 0.03, 0.07, 3.0, 0.3, 30.028),
                new Data(Option.Type.Call, 100.00, 120.00, 0.03, 0.07, 3.0, 0.3, 37.177)
        };
    }

    @Test
    public void testFdValues() {
        // QuantLib test-suite/americanoption.cpp: testFdValues
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        FdBlackScholesVanillaEngine pdeEngine =
                new FdBlackScholesVanillaEngine(process, 100, 400);
        // Official variable name says "QR+", but the source constructs a
        // default FdBlackScholesVanillaEngine (100 x 100).
        FdBlackScholesVanillaEngine qrPlusEngine =
                new FdBlackScholesVanillaEngine(process);

        double tolerance = 8.0e-2;
        for (Data value : juValues()) {
            Date exDate = today.add(timeToDays(value.t));
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.vol);

            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(value.type, value.strike),
                    new AmericanExercise(today, exDate));
            option.setPricingEngine(pdeEngine);
            double pdeCalculated = option.NPV();

            double error = Math.abs(pdeCalculated - value.result);
            if (error > tolerance) {
                fail(reportFailure("value", value, today, exDate, value.result,
                        pdeCalculated, error, tolerance));
            }

            option.setPricingEngine(qrPlusEngine);
            double qrPlusCalculated = option.NPV();
            if (Math.abs(pdeCalculated - qrPlusCalculated) > 2e-2) {
                fail("QR+ boundary approximation failed to reproduce PDE value"
                        + "\n    OptionType: " + value.type
                        + "\n    spot:       " + value.s
                        + "\n    strike:     " + value.strike
                        + "\n    r:          " + value.r
                        + "\n    q:          " + value.q
                        + "\n    vol:        " + value.vol
                        + "\n    PDE value:  " + pdeCalculated
                        + "\n    QR+ value:  " + qrPlusCalculated);
            }
        }
    }

    @Test
    public void testCallPutParity() {
        // QuantLib test-suite/americanoption.cpp: testCallPutParity
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(8, Month.APRIL, 2022);
        Settings.instance.evaluationDate().equal(today);

        final OptionSpec[] testCaseSpecs = {
                new OptionSpec(100.0, 100.0, 365, 0.5, 0.15, 0.02),
                new OptionSpec(100.0, 90.0, 365, 0.5, 0.15, 0.02),
                new OptionSpec(100.0, 125.0, 730, 0.4, 0.15, 0.05),
                new OptionSpec(100.0, 125.0, 730, 0.4, 0.06, 0.05)
        };

        final int xGrid = 400;
        final int timeStepsPerYear = 50;

        for (OptionSpec testCaseSpec : testCaseSpecs) {
            Date maturityDate = today.add(
                    new Period(testCaseSpec.maturityInDays(), TimeUnit.DAYS));
            double maturityTime = dc.yearFraction(today, maturityDate);
            int tGrid = (int) (maturityTime * timeStepsPerYear);

            AmericanExercise exercise = new AmericanExercise(today, maturityDate);

            BlackScholesMertonProcess putProcess = buildProcess(
                    today, dc, testCaseSpec);
            EuropeanOption putOption = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Put, testCaseSpec.strike()),
                    exercise);
            putOption.setPricingEngine(new FdBlackScholesVanillaEngine(
                    putProcess, tGrid, xGrid));
            double putNpv = putOption.NPV();

            OptionSpec callOptionSpec = new OptionSpec(
                    testCaseSpec.strike(), testCaseSpec.spot(),
                    testCaseSpec.maturityInDays(), testCaseSpec.volatility(),
                    testCaseSpec.q(), testCaseSpec.r());
            BlackScholesMertonProcess callProcess = buildProcess(
                    today, dc, callOptionSpec);
            EuropeanOption callOption = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Call, callOptionSpec.strike()),
                    exercise);
            callOption.setPricingEngine(new FdBlackScholesVanillaEngine(
                    callProcess, tGrid, xGrid));
            double callNpv = callOption.NPV();

            double diff = Math.abs(putNpv - callNpv);
            double tol = 0.001;
            if (diff > tol) {
                fail("failed to reproduce American call/put parity"
                        + "\n    Put NPV   : " + putNpv
                        + "\n    Call NPV  : " + callNpv
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }
        }
    }

    private static BlackScholesMertonProcess buildProcess(
            Date today, DayCounter dc, OptionSpec spec) {
        return buildProcess(today, dc, spec.spot(), spec.q(), spec.r(),
                spec.volatility());
    }

    private static BlackScholesMertonProcess buildProcess(
            Date today, DayCounter dc, double spot, double q, double r,
            double vol) {
        return new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(spot), true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, r, dc), true),
                new Handle<>(flatVol(today, new SimpleQuote(vol), dc), true));
    }

    @Test
    public void testFdEarliestExerciseDate() {
        // QuantLib test-suite/americanoption.cpp: testFdEarliestExerciseDate
        final Date today = new Date(15, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();

        final double S0 = 80.0;
        final double K = 100.0;
        final double sigma = 0.25;
        final double r = 0.05;
        final double q = 0.0;

        BlackScholesMertonProcess bsmProcess = buildProcess(
                today, dc, S0, q, r, sigma);

        final Date maturity = today.add(new Period(1, TimeUnit.YEARS));
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, K);

        // Full American exercise
        AmericanExercise fullExercise = new AmericanExercise(today, maturity);
        EuropeanOption fullOption = new EuropeanOption(payoff, fullExercise);
        FdBlackScholesVanillaEngine fdEngine =
                new FdBlackScholesVanillaEngine(bsmProcess, 200, 200, 0);
        fullOption.setPricingEngine(fdEngine);
        double fullPrice = fullOption.NPV();

        // European benchmark
        EuropeanExercise euroExercise = new EuropeanExercise(maturity);
        EuropeanOption euroOption = new EuropeanOption(payoff, euroExercise);
        euroOption.setPricingEngine(new AnalyticEuropeanEngine(bsmProcess));
        double euroPrice = euroOption.NPV();

        double earlyExPremium = fullPrice - euroPrice;
        assertTrue(earlyExPremium > 1.0,
                "early exercise premium should be significant for deep ITM put: "
                        + "full=" + fullPrice + " euro=" + euroPrice);

        // Restricted exercise: only last 3 months
        Date lateStart = maturity.substract(new Period(3, TimeUnit.MONTHS));
        AmericanExercise lateExercise = new AmericanExercise(lateStart, maturity);
        EuropeanOption lateOption = new EuropeanOption(payoff, lateExercise);
        lateOption.setPricingEngine(fdEngine);
        double latePrice = lateOption.NPV();

        assertTrue(fullPrice - latePrice > 0.01,
                "Restricting exercise window should reduce price: full="
                        + fullPrice + " late=" + latePrice);
        assertTrue(latePrice > euroPrice + 0.01,
                "Restricted American should exceed European: late="
                        + latePrice + " euro=" + euroPrice);

        // Monotonicity: longer exercise window -> higher price
        Date midStart = maturity.substract(new Period(6, TimeUnit.MONTHS));
        AmericanExercise midExercise = new AmericanExercise(midStart, maturity);
        EuropeanOption midOption = new EuropeanOption(payoff, midExercise);
        midOption.setPricingEngine(fdEngine);
        double midPrice = midOption.NPV();

        assertTrue(midPrice >= latePrice - 1e-8,
                "Wider window should give higher price: 6M=" + midPrice
                        + " 3M=" + latePrice);
        assertTrue(fullPrice >= midPrice - 1e-8,
                "Full window should give highest price: full=" + fullPrice
                        + " 6M=" + midPrice);
    }

    @Test
    public void testFdAmericanGreeks() {
        // QuantLib test-suite/americanoption.cpp: testFdGreeks
        final Map<String, Double> tolerance = new LinkedHashMap<>();
        tolerance.put("delta", 7.0e-4);
        tolerance.put("gamma", 2.0e-4);

        final Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        final double[] strikes = {50.0, 99.5, 100.0, 100.5, 150.0};
        final double[] underlyings = {100.0};
        final double[] qRates = {0.04, 0.05, 0.06};
        final double[] rRates = {0.01, 0.05, 0.15};
        final int[] years = {1, 2};
        final double[] vols = {0.11, 0.50, 1.20};

        final DayCounter dc = new Actual360(false);
        final Date today = Settings.instance.evaluationDate().Date();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final YieldTermStructure qTS = flatRate(today, qRate, dc);
        final YieldTermStructure rTS = flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (Option.Type type : types) {
            for (double strike : strikes) {
                for (int year : years) {
                    final Date exDate = today.add(
                            new Period(year, TimeUnit.YEARS));
                    final PlainVanillaPayoff payoff =
                            new PlainVanillaPayoff(type, strike);
                    final AmericanExercise exercise =
                            new AmericanExercise(today, exDate);
                    final BlackScholesMertonProcess stochProcess =
                            new BlackScholesMertonProcess(
                                    new Handle<>(spot, true),
                                    new Handle<>(qTS, true),
                                    new Handle<>(rTS, true),
                                    new Handle<>(volTS, true));

                    final FdBlackScholesVanillaEngine engine =
                            new FdBlackScholesVanillaEngine(stochProcess, 50);

                    final EuropeanOption option =
                            new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    final double q = m, r = n;
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    final double value = option.NPV();
                                    final Map<String, Double> calculated =
                                            new LinkedHashMap<>();
                                    calculated.put("delta", option.delta());
                                    calculated.put("gamma", option.gamma());

                                    if (value > spot.value() * 1.0e-5) {
                                        // perturb spot and get delta and gamma
                                        final double du = u * 1.0e-4;
                                        spot.setValue(u + du);
                                        final double value_p = option.NPV();
                                        final double delta_p = option.delta();
                                        spot.setValue(u - du);
                                        final double value_m = option.NPV();
                                        final double delta_m = option.delta();
                                        spot.setValue(u);

                                        final Map<String, Double> expected =
                                                new LinkedHashMap<>();
                                        expected.put("delta",
                                                (value_p - value_m) / (2 * du));
                                        expected.put("gamma",
                                                (delta_p - delta_m) / (2 * du));

                                        for (Map.Entry<String, Double> entry
                                                : calculated.entrySet()) {
                                            final String greek = entry.getKey();
                                            final double expct =
                                                    expected.get(greek);
                                            final double calcl =
                                                    entry.getValue();
                                            final double tol =
                                                    tolerance.get(greek);
                                            final double error = relativeError(
                                                    expct, calcl, u);
                                            if (error > tol) {
                                                fail(greek + " greek failure"
                                                        + "\n    type:       "
                                                        + type
                                                        + "\n    strike:     "
                                                        + strike
                                                        + "\n    spot:       " + u
                                                        + "\n    q:          " + q
                                                        + "\n    r:          " + r
                                                        + "\n    today:      " + today
                                                        + "\n    vol:        " + v
                                                        + "\n    expected:   " + expct
                                                        + "\n    calculated: " + calcl
                                                        + "\n    error:      " + error
                                                        + "\n    tolerance:  " + tol);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testEscrowedVsSpotAmericanOption() {
        // QuantLib test-suite/americanoption.cpp: testEscrowedVsSpotAmericanOption
        final DayCounter dc = new Actual360(false);
        final Date today = new Date(27, Month.FEBRUARY, 2021);
        Settings.instance.evaluationDate().equal(today);

        final SimpleQuote vol = new SimpleQuote(0.3);

        final BlackScholesMertonProcess process =
                new BlackScholesMertonProcess(
                        new Handle<>(new SimpleQuote(100.0), true),
                        new Handle<>(flatRate(today, 0.08, dc), true),
                        new Handle<>(flatRate(today, 0.04, dc), true),
                        new Handle<>(flatVol(today, vol, dc), true));

        final Date maturityDate = today.add(
                new Period(12, TimeUnit.MONTHS));
        final List<Date> dividendDates = new ArrayList<>();
        dividendDates.add(today.add(new Period(10, TimeUnit.MONTHS)));
        final double[] dividendAmounts = {10.0};
        final List<Dividend> dividends =
                Dividend.dividendVector(dividendDates, dividendAmounts);

        final double strike = 100.0;
        final EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, strike),
                new AmericanExercise(today, maturityDate));

        option.setPricingEngine(new FdBlackScholesVanillaEngine(
                process, dividends, 100, 400));

        final double spotNpv = option.NPV();
        final double spotDelta = option.delta();

        vol.setValue(100 / 90. * 0.3);

        option.setPricingEngine(new MakeFdBlackScholesVanillaEngine(process)
                .withTGrid(100)
                .withXGrid(400)
                .withCashDividends(dividendDates, dividendAmounts)
                .withCashDividendModel(
                        FdBlackScholesVanillaEngine.CashDividendModel.Escrowed)
                .build());

        final double escrowedNpv = option.NPV();
        final double escrowedDelta = option.delta();

        final double diffNpv = Math.abs(escrowedNpv - spotNpv);
        final double tol = 1e-2;

        if (diffNpv > tol) {
            fail("failed to compare American option NPV with "
                    + "escrowed and spot dividend model"
                    + "\n    escrowed div: " + escrowedNpv
                    + "\n    spot div    : " + spotNpv
                    + "\n    difference:  " + diffNpv
                    + "\n    tolerance:   " + tol);
        }

        final double diffDelta = Math.abs(escrowedDelta - spotDelta);

        if (diffDelta > tol) {
            fail("failed to compare American option Delta with "
                    + "escrowed and spot dividend model"
                    + "\n    escrowed div: " + escrowedDelta
                    + "\n    spot div    : " + spotDelta
                    + "\n    difference:  " + diffDelta
                    + "\n    tolerance:   " + tol);
        }
    }

    @Test
    public void testTodayIsDividendDate() {
        // QuantLib test-suite/americanoption.cpp: testTodayIsDividendDate
        final DayCounter dc = new Actual360(false);
        final Date today = new Date(27, Month.FEBRUARY, 2021);
        Settings.instance.evaluationDate().equal(today);

        final SimpleQuote vol = new SimpleQuote(0.3);

        final BlackScholesMertonProcess process =
                new BlackScholesMertonProcess(
                        new Handle<>(new SimpleQuote(100.0), true),
                        new Handle<>(flatRate(today, 0.05, dc), true),
                        new Handle<>(flatRate(today, 0.07, dc), true),
                        new Handle<>(flatVol(today, vol, dc), true));

        final Date maturityDate = today.add(
                new Period(12, TimeUnit.MONTHS));
        final List<Date> dividendDates = new ArrayList<>();
        dividendDates.add(today);
        dividendDates.add(today.add(new Period(11, TimeUnit.MONTHS)));
        final double[] dividendAmounts = {5.0, 5.0};

        FdBlackScholesVanillaEngine spotEngine =
                new MakeFdBlackScholesVanillaEngine(process)
                        .withTGrid(100)
                        .withXGrid(400)
                        .withCashDividends(dividendDates, dividendAmounts)
                        .withCashDividendModel(
                                FdBlackScholesVanillaEngine.CashDividendModel.Spot)
                        .build();

        FdBlackScholesVanillaEngine escrowedEngine =
                new MakeFdBlackScholesVanillaEngine(process)
                        .withTGrid(100)
                        .withXGrid(400)
                        .withCashDividends(dividendDates, dividendAmounts)
                        .withCashDividendModel(
                                FdBlackScholesVanillaEngine.CashDividendModel.Escrowed)
                        .build();

        final double strike = 90.0;
        final EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Put, strike),
                new AmericanExercise(today, maturityDate));

        option.setPricingEngine(spotEngine);

        double spotNpv = option.NPV();
        final double spotDelta = option.delta();
        assertThrows(IllegalArgumentException.class, option::theta);

        vol.setValue(100 / 95. * 0.3);

        option.setPricingEngine(escrowedEngine);

        double escrowedNpv = option.NPV();
        final double escrowedDelta = option.delta();
        assertThrows(IllegalArgumentException.class, option::theta);

        double diffNpv = Math.abs(escrowedNpv - spotNpv);
        final double tol = 5e-2;

        if (diffNpv > tol) {
            fail("failed to compare American option NPV with "
                    + "escrowed and spot dividend model"
                    + "\n    escrowed div: " + escrowedNpv
                    + "\n    spot div    : " + spotNpv
                    + "\n    difference:  " + diffNpv
                    + "\n    tolerance:   " + tol);
        }

        final double diffDelta = Math.abs(escrowedDelta - spotDelta);

        if (diffDelta > tol) {
            fail("failed to compare American option Delta with "
                    + "escrowed and spot dividend model"
                    + "\n    escrowed div: " + escrowedDelta
                    + "\n    spot div    : " + spotDelta
                    + "\n    difference:  " + diffDelta
                    + "\n    tolerance:   " + tol);
        }

        dividendDates.set(0, today.add(1));

        spotEngine = new MakeFdBlackScholesVanillaEngine(process)
                .withTGrid(100)
                .withXGrid(400)
                .withCashDividends(dividendDates, dividendAmounts)
                .withCashDividendModel(
                        FdBlackScholesVanillaEngine.CashDividendModel.Spot)
                .build();

        escrowedEngine = new MakeFdBlackScholesVanillaEngine(process)
                .withTGrid(100)
                .withXGrid(400)
                .withCashDividends(dividendDates, dividendAmounts)
                .withCashDividendModel(
                        FdBlackScholesVanillaEngine.CashDividendModel.Escrowed)
                .build();

        vol.setValue(0.3);

        option.setPricingEngine(spotEngine);
        spotNpv = option.NPV();

        vol.setValue(100 / 95.0 * 0.3);
        option.setPricingEngine(escrowedEngine);

        escrowedNpv = option.NPV();
        assertDoesNotThrow(option::theta);

        diffNpv = Math.abs(escrowedNpv - spotNpv);

        if (diffNpv > tol) {
            fail("failed to compare American option NPV with "
                    + "escrowed and spot dividend model"
                    + "\n    escrowed div: " + escrowedNpv
                    + "\n    spot div    : " + spotNpv
                    + "\n    difference:  " + diffNpv
                    + "\n    tolerance:   " + tol);
        }
    }

    @Test
    public void testJuValues() {
        Data[] juValues = juValues();

        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        JuQuadraticApproximationEngine engine =
                new JuQuadraticApproximationEngine(process);

        double tolerance = 1.0e-3;
        for (Data value : juValues) {
            Date exDate = today.add(timeToDays(value.t));
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.vol);

            EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(value.type, value.strike),
                    new AmericanExercise(today, exDate));
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double error = Math.abs(calculated - value.result);
            if (error > tolerance) {
                fail(reportFailure("value", value, today, exDate, value.result,
                        calculated, error, tolerance));
            }
        }
    }

    @Test
    public void testJuValuesAtZeroRate() {
        Date today = new Date(19, Month.AUGUST, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual360(false);
        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.04);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.30);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        JuQuadraticApproximationEngine engine =
                new JuQuadraticApproximationEngine(process);

        AmericanExercise exercise = new AmericanExercise(today,
                today.add(new Period(1, TimeUnit.YEARS)));

        double tolerance = 1.0e-6;
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            for (double q : new double[]{0.04, 0.0}) {
                for (double s : new double[]{70.0, 85.0, 100.0, 115.0, 130.0}) {
                    Payoff payoff = new PlainVanillaPayoff(type, 100.0);
                    EuropeanOption option = new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    spot.setValue(s);
                    qRate.setValue(q);

                    rRate.setValue(0.0);
                    double calculated = option.NPV();

                    rRate.setValue(1.0e-10);
                    double expected = option.NPV();

                    double error = Math.abs(calculated - expected);
                    if (!(error <= tolerance)) {
                        fail("value at zero rate: type=" + type + " spot=" + s
                                + " strike=100.0 q=" + q + " r=0.0 today=" + today
                                + " maturity=" + exercise.lastDate()
                                + " vol=" + vol.value() + " expected=" + expected
                                + " calculated=" + calculated + " error=" + error
                                + " tolerance=" + tolerance);
                    }
                }
            }
        }
    }

    @Test
    public void testJuNotBelowExerciseValue() {
        Date today = new Date(15, Month.AUGUST, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);

        record Case(Option.Type type, double strike, double r, double q,
                    double v, int years) {
        }
        Case[] cases = {
                new Case(Option.Type.Call, 60.0, 0.03, 0.02, 0.10, 3),
                new Case(Option.Type.Call, 60.0, 0.06, 0.04, 0.10, 10),
                new Case(Option.Type.Put, 140.0, 0.03, 0.04, 0.03, 10)
        };

        for (Case c : cases) {
            BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    new Handle<>(spot, true),
                    new Handle<>(flatRate(today, c.q, dc), true),
                    new Handle<>(flatRate(today, c.r, dc), true),
                    new Handle<>(flatVol(today, new SimpleQuote(c.v), dc), true));

            Payoff payoff = new PlainVanillaPayoff(c.type, c.strike);
            AmericanExercise exercise = new AmericanExercise(today,
                    today.add(new Period(c.years, TimeUnit.YEARS)));

            EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(new JuQuadraticApproximationEngine(process));

            double intrinsic = payoff.value(spot.value());
            double calculated = option.NPV();
            if (calculated < intrinsic) {
                fail("value below exercise: " + c + " spot=" + spot.value()
                        + " today=" + today + " expected=" + intrinsic
                        + " calculated=" + calculated
                        + " error=" + (intrinsic - calculated) + " tolerance=0.0");
            }
        }
    }

    @Test
    public void testJuNotBelowEuropeanValue() {
        Date today = new Date(15, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);

        record Case(Option.Type type, double strike, double r, double q,
                    double v, int days) {
        }
        Case[] cases = {
                new Case(Option.Type.Put, 110.0, 0.02, 0.00, 0.80, 3),
                new Case(Option.Type.Put, 100.0, 0.05, 0.03, 1.20, 1),
                new Case(Option.Type.Call, 90.0, 0.01, 0.03, 1.20, 3)
        };

        for (Case c : cases) {
            BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    new Handle<>(spot, true),
                    new Handle<>(flatRate(today, c.q, dc), true),
                    new Handle<>(flatRate(today, c.r, dc), true),
                    new Handle<>(flatVol(today, new SimpleQuote(c.v), dc), true));

            Payoff payoff = new PlainVanillaPayoff(c.type, c.strike);
            Date exDate = today.add(c.days);
            AmericanExercise exercise = new AmericanExercise(today, exDate);

            EuropeanOption european = new EuropeanOption(payoff,
                    new EuropeanExercise(exDate));
            european.setPricingEngine(new AnalyticEuropeanEngine(process));

            EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(new JuQuadraticApproximationEngine(process));

            double expected = european.NPV();
            double calculated = option.NPV();
            if (calculated < expected) {
                fail("value below European: " + c + " spot=" + spot.value()
                        + " today=" + today + " expected=" + expected
                        + " calculated=" + calculated
                        + " error=" + (expected - calculated) + " tolerance=0.0");
            }
        }
    }

    @Test
    public void testBaroneAdesiWhaleyAndJuAtLowVolatility() {
        Date today = new Date(15, Month.MAY, 2026);
        Settings.instance.evaluationDate().equal(today);

        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote vol = new SimpleQuote(0.20);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true),
                new Handle<>(flatRate(today, 0.02, dc), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));

        Date exDate = today.add(180);
        AmericanExercise exercise = new AmericanExercise(today, exDate);
        EuropeanExercise europeanExercise = new EuropeanExercise(exDate);

        double tolerance = 1.0e-6;
        double band = 1.0e-2;

        var engines = new Object[][]{
                {"Barone-Adesi-Whaley", new BaroneAdesiWhaleyApproximationEngine(process)},
                {"Ju", new JuQuadraticApproximationEngine(process)},
                {"Bjerksund-Stensland",
                        new BjerksundStenslandApproximationEngine(process)}
        };

        for (Option.Type type : new Option.Type[]{Option.Type.Put, Option.Type.Call}) {
            for (double s : new double[]{80.0, 100.0, 120.0}) {
                for (double v : new double[]{1.0e-3, 5.0e-4, 0.0}) {
                    spot.setValue(s);
                    vol.setValue(v);

                    Payoff payoff = new PlainVanillaPayoff(type, 100.0);
                    double intrinsic = payoff.value(s);

                    EuropeanOption european = new EuropeanOption(payoff,
                            europeanExercise);
                    european.setPricingEngine(new AnalyticEuropeanEngine(process));
                    double floor = Math.max(european.NPV(), intrinsic);

                    for (Object[] entry : engines) {
                        String name = (String) entry[0];
                        EuropeanOption option = new EuropeanOption(payoff, exercise);
                        option.setPricingEngine((jquant.PricingEngine) entry[1]);
                        double calculated = option.NPV();
                        if (!(calculated >= floor - tolerance
                                && calculated <= floor + band)) {
                            fail("low-volatility value " + name + ": " + type
                                    + " spot=" + s + " strike=100.0 q=0.02 r=0.05"
                                    + " today=" + today + " maturity=" + exDate
                                    + " vol=" + v + " floor=" + floor
                                    + " calculated=" + calculated
                                    + " error=" + (calculated - floor)
                                    + " band=" + band);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int timeToDays(double t) {
        return (int) Math.round(t * 360.0);
    }

    /**
     * {@code relativeError} from {@code test-suite/utilities.cpp}: absolute
     * difference over the reference, falling back to the absolute difference
     * when the reference is zero.
     */
    private static double relativeError(double x1, double x2,
                                        double reference) {
        if (reference != 0.0)
            return Math.abs(x1 - x2) / reference;
        return Math.abs(x1 - x2);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Continuous, Frequency.ANNUAL);
    }

    private static YieldTermStructure flatRate(Date today, double rate,
                                               DayCounter dc) {
        return flatRate(today, new SimpleQuote(rate), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }

    private static void assertAbs(String name, double expected, double calculated,
                                  double tolerance) {
        double error = Math.abs(calculated - expected);
        if (!(error <= tolerance)) {
            fail(name + ": expected=" + expected + " calculated=" + calculated
                    + " error=" + error + " tolerance=" + tolerance);
        }
    }

    /**
     * {@code QL_CHECK_CLOSE}: {@code percent} is a percentage tolerance, so
     * {@code 1000*QL_EPSILON} allows a relative error of {@code 10*QL_EPSILON}.
     */
    private static void checkClose(String name, double expected, double calculated,
                                   double percent) {
        // QL_CHECK_CLOSE is BOOST_CHECK_CLOSE: the percentage tolerance is
        // measured against the larger of the two values.
        double error = Math.abs(calculated - expected)
                / Math.max(Math.abs(expected), Math.abs(calculated)) * 100.0;
        if (!(error <= percent)) {
            fail(name + ": expected=" + expected + " calculated=" + calculated
                    + " relative error=" + error + " tolerance=" + percent);
        }
    }

    private static String reportFailure(String greek, Data value, Date today,
                                        Date exDate, double expected,
                                        double calculated, double error,
                                        double tolerance) {
        return greek + " " + value.type + " option with strike " + value.strike
                + " payoff:\n    spot value:       " + value.s
                + "\n    strike:           " + value.strike
                + "\n    dividend yield:   " + value.q
                + "\n    risk-free rate:   " + value.r
                + "\n    reference date:   " + today
                + "\n    maturity:         " + exDate
                + "\n    volatility:       " + value.vol
                + "\n    expected   " + greek + ": " + expected
                + "\n    calculated " + greek + ": " + calculated
                + "\n    error:            " + error
                + "\n    tolerance:        " + tolerance;
    }
}
