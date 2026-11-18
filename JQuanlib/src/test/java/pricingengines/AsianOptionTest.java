package pricingengines;

import jquant.Average;
import jquant.Compounding;
import jquant.ContinuousAveragingAsianOption;
import jquant.DiscreteAveragingAsianOption;
import jquant.EuropeanExercise;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.MathUtils;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.asian.AnalyticContinuousGeometricAveragePriceAsianEngine;
import jquant.pricingengines.asian.AnalyticDiscreteGeometricAveragePriceAsianEngine;
import jquant.pricingengines.asian.AnalyticDiscreteGeometricAverageStrikeAsianEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jquant.Compounding.Continuous;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Analytic geometric average price Asian options from QuantLib
 * {@code test-suite/asianoptions.cpp}.
 */
public class AsianOptionTest {

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

    /**
     * QuantLib {@code testAnalyticContinuousGeometricAveragePrice}.
     *
     * <p>Data from "Option Pricing Formulas", Haug, pag.96-97.
     */
    @Test
    public void testAnalyticContinuousGeometricAveragePrice() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(80.0);
        SimpleQuote qRate = new SimpleQuote(-0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.05);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        AnalyticContinuousGeometricAveragePriceAsianEngine engine =
                new AnalyticContinuousGeometricAveragePriceAsianEngine(stochProcess);

        Average.Type averageType = Average.Type.Geometric;
        Option.Type type = Option.Type.Put;
        double strike = 85.0;
        Date exerciseDate = today.add(90);

        Double runningAccumulator = null;
        Integer pastFixings = null;

        PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        Exercise exercise = new EuropeanExercise(exerciseDate);

        ContinuousAveragingAsianOption option =
                new ContinuousAveragingAsianOption(averageType, payoff, exercise);
        option.setPricingEngine(engine);

        double calculated = option.NPV();
        double expected = 4.6922;
        double tolerance = 1.0e-4;
        if (!(Math.abs(calculated - expected) <= tolerance)) {
            reportFailure("value", averageType, runningAccumulator, pastFixings,
                    new ArrayList<>(), payoff, exercise, spot.value(),
                    qRate.value(), rRate.value(), today, vol.value(),
                    expected, calculated, tolerance);
        }

        // Trying to approximate the continuous version with the discrete
        // version.
        runningAccumulator = 1.0;
        pastFixings = 0;
        int numberOfFixings = exerciseDate.serialNumber() - today.serialNumber() + 1;
        List<Date> fixingDates = new ArrayList<>(numberOfFixings);
        for (int i = 0; i < numberOfFixings; i++) {
            fixingDates.add(today.add(i));
        }
        AnalyticDiscreteGeometricAveragePriceAsianEngine engine2 =
                new AnalyticDiscreteGeometricAveragePriceAsianEngine(stochProcess);
        DiscreteAveragingAsianOption option2 = new DiscreteAveragingAsianOption(
                averageType, runningAccumulator, pastFixings, fixingDates,
                payoff, exercise);
        option2.setPricingEngine(engine2);

        calculated = option2.NPV();
        tolerance = 3.0e-3;
        if (!(Math.abs(calculated - expected) <= tolerance)) {
            reportFailure("value", averageType, runningAccumulator, pastFixings,
                    fixingDates, payoff, exercise, spot.value(),
                    qRate.value(), rRate.value(), today, vol.value(),
                    expected, calculated, tolerance);
        }
    }

    /**
     * QuantLib {@code testAnalyticContinuousGeometricAveragePriceGreeks}.
     */
    @Test
    public void testAnalyticContinuousGeometricAveragePriceGreeks() {
        Map<String, Double> calculated = new LinkedHashMap<>();
        Map<String, Double> expected = new LinkedHashMap<>();
        Map<String, Double> tolerance = new LinkedHashMap<>();
        tolerance.put("delta", 1.0e-5);
        tolerance.put("gamma", 1.0e-5);
        tolerance.put("theta", 1.0e-5);
        tolerance.put("rho", 1.0e-5);
        tolerance.put("divRho", 1.0e-5);
        tolerance.put("vega", 1.0e-5);

        Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        double[] underlyings = {100.0};
        double[] strikes = {90.0, 100.0, 110.0};
        double[] qRates = {0.04, 0.05, 0.06};
        double[] rRates = {0.01, 0.05, 0.15};
        int[] lengths = {1, 2};
        double[] vols = {0.11, 0.50, 1.20};

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        Handle<YieldTermStructure> qTS = flatRate(qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        Handle<YieldTermStructure> rTS = flatRate(rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.0);
        Handle<BlackVolTermStructure> volTS = flatVol(vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);

        for (Option.Type type : types) {
            for (double strike : strikes) {
                for (int length : lengths) {
                    EuropeanExercise maturity = new EuropeanExercise(
                            today.add(new Period(length, TimeUnit.YEARS)));

                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

                    AnalyticContinuousGeometricAveragePriceAsianEngine engine =
                            new AnalyticContinuousGeometricAveragePriceAsianEngine(process);

                    ContinuousAveragingAsianOption option =
                            new ContinuousAveragingAsianOption(
                                    Average.Type.Geometric, payoff, maturity);
                    option.setPricingEngine(engine);

                    Integer pastFixings = null;
                    Double runningAverage = null;

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    double q = m;
                                    double r = n;
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    double value = option.NPV();
                                    calculated.put("delta", option.delta());
                                    calculated.put("gamma", option.gamma());
                                    calculated.put("theta", option.theta());
                                    calculated.put("rho", option.rho());
                                    calculated.put("divRho", option.dividendRho());
                                    calculated.put("vega", option.vega());

                                    if (value > spot.value() * 1.0e-5) {
                                        // Perturb spot and get delta and gamma.
                                        double du = u * 1.0e-4;
                                        spot.setValue(u + du);
                                        double value_p = option.NPV();
                                        double delta_p = option.delta();
                                        spot.setValue(u - du);
                                        double value_m = option.NPV();
                                        double delta_m = option.delta();
                                        spot.setValue(u);
                                        expected.put("delta", (value_p - value_m) / (2 * du));
                                        expected.put("gamma", (delta_p - delta_m) / (2 * du));

                                        // Perturb rates and get rho and dividend rho.
                                        double dr = r * 1.0e-4;
                                        rRate.setValue(r + dr);
                                        value_p = option.NPV();
                                        rRate.setValue(r - dr);
                                        value_m = option.NPV();
                                        rRate.setValue(r);
                                        expected.put("rho", (value_p - value_m) / (2 * dr));

                                        double dq = q * 1.0e-4;
                                        qRate.setValue(q + dq);
                                        value_p = option.NPV();
                                        qRate.setValue(q - dq);
                                        value_m = option.NPV();
                                        qRate.setValue(q);
                                        expected.put("divRho", (value_p - value_m) / (2 * dq));

                                        // Perturb volatility and get vega.
                                        double dv = v * 1.0e-4;
                                        vol.setValue(v + dv);
                                        value_p = option.NPV();
                                        vol.setValue(v - dv);
                                        value_m = option.NPV();
                                        vol.setValue(v);
                                        expected.put("vega", (value_p - value_m) / (2 * dv));

                                        // Perturb date and get theta.
                                        double dT = dc.yearFraction(
                                                today.substract(1), today.add(1));
                                        Settings.instance.evaluationDate()
                                                .equal(today.substract(1));
                                        value_m = option.NPV();
                                        Settings.instance.evaluationDate()
                                                .equal(today.add(1));
                                        value_p = option.NPV();
                                        Settings.instance.evaluationDate().equal(today);
                                        expected.put("theta", (value_p - value_m) / dT);

                                        compareGreeks(calculated, expected, tolerance,
                                                runningAverage, pastFixings, payoff,
                                                maturity, u, q, r, today, v);
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
     * QuantLib {@code testAnalyticDiscreteGeometricAveragePrice}.
     *
     * <p>Data from "Implementing Derivatives Model", Clewlow, Strickland,
     * p.118-123.
     */
    @Test
    public void testAnalyticDiscreteGeometricAveragePrice() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.06);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        AnalyticDiscreteGeometricAveragePriceAsianEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianEngine(stochProcess);

        Average.Type averageType = Average.Type.Geometric;
        double runningAccumulator = 1.0;
        int pastFixings = 0;
        int futureFixings = 10;
        Option.Type type = Option.Type.Call;
        double strike = 100.0;
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

        Date exerciseDate = today.add(360);
        Exercise exercise = new EuropeanExercise(exerciseDate);

        List<Date> fixingDates = new ArrayList<>(futureFixings);
        int dt = (int) Math.round(360.0 / futureFixings);
        fixingDates.add(today.add(dt));
        for (int j = 1; j < futureFixings; j++) {
            fixingDates.add(fixingDates.get(j - 1).add(dt));
        }

        DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                averageType, runningAccumulator, pastFixings, fixingDates,
                payoff, exercise);
        option.setPricingEngine(engine);

        double calculated = option.NPV();
        double expected = 5.3425606635;
        double tolerance = 1e-10;
        if (!(Math.abs(calculated - expected) <= tolerance)) {
            reportFailure("value", averageType, runningAccumulator, pastFixings,
                    fixingDates, payoff, exercise, spot.value(),
                    qRate.value(), rRate.value(), today, vol.value(),
                    expected, calculated, tolerance);
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAveragePriceGreeks}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAveragePriceGreeks() {
        Map<String, Double> calculated = new LinkedHashMap<>();
        Map<String, Double> expected = new LinkedHashMap<>();
        Map<String, Double> tolerance = new LinkedHashMap<>();
        tolerance.put("delta", 1.0e-5);
        tolerance.put("gamma", 1.0e-5);
        tolerance.put("theta", 1.0e-5);
        tolerance.put("rho", 1.0e-5);
        tolerance.put("divRho", 1.0e-5);
        tolerance.put("vega", 1.0e-5);

        Option.Type[] types = {Option.Type.Call, Option.Type.Put};
        double[] underlyings = {100.0};
        double[] strikes = {90.0, 100.0, 110.0};
        double[] qRates = {0.04, 0.05, 0.06};
        double[] rRates = {0.01, 0.05, 0.15};
        int[] lengths = {1, 2};
        double[] vols = {0.11, 0.50, 1.20};

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote spot = new SimpleQuote(0.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        Handle<YieldTermStructure> qTS = flatRate(qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        Handle<YieldTermStructure> rTS = flatRate(rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.0);
        Handle<BlackVolTermStructure> volTS = flatVol(vol, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);

        for (Option.Type type : types) {
            for (double strike : strikes) {
                for (int length : lengths) {
                    EuropeanExercise maturity = new EuropeanExercise(
                            today.add(new Period(length, TimeUnit.YEARS)));

                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

                    double runningAverage = 120.0;
                    int pastFixings = 1;

                    List<Date> fixingDates = new ArrayList<>();
                    Period threeMonths = new Period(3, TimeUnit.MONTHS);
                    for (Date d = today.add(threeMonths);
                         !TimeUtils.greater(d, maturity.lastDate());
                         d = d.add(threeMonths)) {
                        fixingDates.add(d);
                    }

                    AnalyticDiscreteGeometricAveragePriceAsianEngine engine =
                            new AnalyticDiscreteGeometricAveragePriceAsianEngine(process);

                    DiscreteAveragingAsianOption option =
                            new DiscreteAveragingAsianOption(
                                    Average.Type.Geometric, runningAverage,
                                    pastFixings, fixingDates, payoff, maturity);
                    option.setPricingEngine(engine);

                    for (double u : underlyings) {
                        for (double m : qRates) {
                            for (double n : rRates) {
                                for (double v : vols) {
                                    double q = m;
                                    double r = n;
                                    spot.setValue(u);
                                    qRate.setValue(q);
                                    rRate.setValue(r);
                                    vol.setValue(v);

                                    double value = option.NPV();
                                    calculated.put("delta", option.delta());
                                    calculated.put("gamma", option.gamma());
                                    calculated.put("theta", option.theta());
                                    calculated.put("rho", option.rho());
                                    calculated.put("divRho", option.dividendRho());
                                    calculated.put("vega", option.vega());

                                    if (value > spot.value() * 1.0e-5) {
                                        // Perturb spot and get delta and gamma.
                                        double du = u * 1.0e-4;
                                        spot.setValue(u + du);
                                        double value_p = option.NPV();
                                        double delta_p = option.delta();
                                        spot.setValue(u - du);
                                        double value_m = option.NPV();
                                        double delta_m = option.delta();
                                        spot.setValue(u);
                                        expected.put("delta", (value_p - value_m) / (2 * du));
                                        expected.put("gamma", (delta_p - delta_m) / (2 * du));

                                        // Perturb rates and get rho and dividend rho.
                                        double dr = r * 1.0e-4;
                                        rRate.setValue(r + dr);
                                        value_p = option.NPV();
                                        rRate.setValue(r - dr);
                                        value_m = option.NPV();
                                        rRate.setValue(r);
                                        expected.put("rho", (value_p - value_m) / (2 * dr));

                                        double dq = q * 1.0e-4;
                                        qRate.setValue(q + dq);
                                        value_p = option.NPV();
                                        qRate.setValue(q - dq);
                                        value_m = option.NPV();
                                        qRate.setValue(q);
                                        expected.put("divRho", (value_p - value_m) / (2 * dq));

                                        // Perturb volatility and get vega.
                                        double dv = v * 1.0e-4;
                                        vol.setValue(v + dv);
                                        value_p = option.NPV();
                                        vol.setValue(v - dv);
                                        value_m = option.NPV();
                                        vol.setValue(v);
                                        expected.put("vega", (value_p - value_m) / (2 * dv));

                                        // Perturb date and get theta.
                                        double dT = dc.yearFraction(
                                                today.substract(1), today.add(1));
                                        Settings.instance.evaluationDate()
                                                .equal(today.substract(1));
                                        value_m = option.NPV();
                                        Settings.instance.evaluationDate()
                                                .equal(today.add(1));
                                        value_p = option.NPV();
                                        Settings.instance.evaluationDate().equal(today);
                                        expected.put("theta", (value_p - value_m) / dT);

                                        compareGreeks(calculated, expected, tolerance,
                                                runningAverage, pastFixings, payoff,
                                                maturity, u, q, r, today, v);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Discrete geometric average-strike Asians
    // QuantLib test-suite/asianoptions.cpp
    // ------------------------------------------------------------------

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrike}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrike() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.06);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                new AnalyticDiscreteGeometricAverageStrikeAsianEngine(stochProcess);

        Average.Type averageType = Average.Type.Geometric;
        double runningAccumulator = 1.0;
        int pastFixings = 0;
        int futureFixings = 10;
        Option.Type type = Option.Type.Call;
        double strike = 100.0;
        PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

        Date exerciseDate = today.add(360);
        Exercise exercise = new EuropeanExercise(exerciseDate);

        List<Date> fixingDates = new ArrayList<>(futureFixings);
        int dt = (int) Math.round(360.0 / futureFixings);
        fixingDates.add(today.add(dt));
        for (int j = 1; j < futureFixings; j++) {
            fixingDates.add(fixingDates.get(j - 1).add(dt));
        }

        DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                averageType, runningAccumulator, pastFixings, fixingDates,
                payoff, exercise);
        option.setPricingEngine(engine);

        double calculated = option.NPV();
        // Joint lognormal moments: E[ln G] = ln S + (r-q-sigma^2/2)*mean(t_i),
        // Var[ln G] = sigma^2/N^2 * sum_ij min(t_i,t_j),
        // Cov[ln S_T, ln G] = sigma^2*mean(t_i);
        // the call is D_r(T)*(F_T*N(d1) - E[G]*N(d2)).
        double expected = 4.95620;
        double tolerance = 1e-5;
        if (!(Math.abs(calculated - expected) <= tolerance)) {
            reportFailure("value", averageType, runningAccumulator, pastFixings,
                    fixingDates, payoff, exercise, spot.value(),
                    qRate.value(), rRate.value(), today, vol.value(),
                    expected, calculated, tolerance);
        }
    }

    /**
     * QuantLib {@code geometricAverageStrikeEngine} helper from
     * {@code test-suite/asianoptions.cpp}.
     */
    private static AnalyticDiscreteGeometricAverageStrikeAsianEngine
    geometricAverageStrikeEngine(Date today) {
        return geometricAverageStrikeEngine(today, 0.06, 0.03, 0.20,
                new Actual360(), new Actual360(), new Actual360());
    }

    private static AnalyticDiscreteGeometricAverageStrikeAsianEngine
    geometricAverageStrikeEngine(Date today, double r, double q, double vol) {
        return geometricAverageStrikeEngine(today, r, q, vol,
                new Actual360(), new Actual360(), new Actual360());
    }

    private static AnalyticDiscreteGeometricAverageStrikeAsianEngine
    geometricAverageStrikeEngine(Date today, double r, double q, double vol,
                                 DayCounter rdc, DayCounter qdc,
                                 DayCounter vdc) {
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(flatRate(today, q, qdc), true),
                new Handle<>(flatRate(today, r, rdc), true),
                new Handle<>(flatVol(today, vol, vdc), true));
        return new AnalyticDiscreteGeometricAverageStrikeAsianEngine(process);
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeForwardStart}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeForwardStart() {
        Date today = Settings.instance.evaluationDate().Date();
        Exercise exercise = new EuropeanExercise(today.add(360));
        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                geometricAverageStrikeEngine(today);
        double r = 0.06, q = 0.03, sigma = 0.20, t1 = 0.5, T = 1.0;
        double forward = Math.exp((r - q) * (T - t1));
        double discount = Math.exp(-r * (T - t1));
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
            DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    Average.Type.Geometric, 1.0, 0,
                    List.of(today.add(180)), payoff, exercise);
            option.setPricingEngine(engine);
            double expected = 100.0 * Math.exp(-q * t1)
                    * BlackFormula.blackFormula(type, 1.0, forward,
                    sigma * Math.sqrt(T - t1), discount, 0.0);
            checkSmall("ForwardStart " + type, option.NPV() - expected, 1e-12);
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeAtMaturity}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeAtMaturity() {
        Date today = Settings.instance.evaluationDate().Date();
        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                geometricAverageStrikeEngine(today);
        // 157 days gives year fractions whose rounding must not leave a
        // spurious variance.
        for (int days : new int[]{157, 360}) {
            Exercise exercise = new EuropeanExercise(today.add(days));
            for (int n : new int[]{1, 3, 7}) {
                for (Option.Type type : new Option.Type[]{Option.Type.Call,
                        Option.Type.Put}) {
                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
                    List<Date> fixingDates = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        fixingDates.add(today.add(days));
                    }
                    DiscreteAveragingAsianOption option =
                            new DiscreteAveragingAsianOption(
                                    Average.Type.Geometric, 1.0, 0,
                                    fixingDates, payoff, exercise);
                    option.setPricingEngine(engine);
                    double calculated = option.NPV();
                    if (!Double.isFinite(calculated)) {
                        fail("AtMaturity " + type + " days=" + days
                                + " n=" + n + ": non-finite NPV " + calculated);
                    }
                    checkSmall("AtMaturity " + type + " days=" + days
                            + " n=" + n, calculated, 1e-12);
                }
            }
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeDayCounters}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeDayCounters() {
        Date today = Settings.instance.evaluationDate().Date();
        Exercise exercise = new EuropeanExercise(today.add(360));
        List<Date> fixingDates = List.of(today.add(36), today.add(90),
                today.add(180), today.add(360));
        DayCounter[] dc = {new Actual360(), new Actual365Fixed()};
        double[] scale = {1.0, 365.0 / 360.0};
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
            DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    Average.Type.Geometric, 1.0, 0, fixingDates, payoff, exercise);
            option.setPricingEngine(geometricAverageStrikeEngine(today));
            double expected = option.NPV();
            // Also vary clocks separately: changing all three together can
            // mask mixed clocks.
            for (int r = 0; r < 2; ++r) {
                for (int q = 0; q < 2; ++q) {
                    for (int v = 0; v < 2; ++v) {
                        option.setPricingEngine(geometricAverageStrikeEngine(
                                today, 0.06 * scale[r], 0.03 * scale[q],
                                0.20 * Math.sqrt(scale[v]),
                                dc[r], dc[q], dc[v]));
                        checkClose("DayCounters " + type + " r=" + dc[r]
                                        + " q=" + dc[q] + " vol=" + dc[v],
                                option.NPV(), expected, 1e-8);
                    }
                }
            }
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeFirstFixingToday}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeFirstFixingToday() {
        Date today = Settings.instance.evaluationDate().Date();
        Exercise exercise = new EuropeanExercise(today.add(360));
        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                geometricAverageStrikeEngine(today);
        List<Date> fixingDates = List.of(today, today.add(90), today.add(180),
                today.add(270), today.add(360));
        // The old formula for t_i = 0, 1/4, 1/2, 3/4, 1 has mean(t_i) = 1/2,
        // Var[ln G] = 0.3*sigma^2 and Cov[ln S_T, ln G] = 0.5*sigma^2.
        double sigma2 = 0.20 * 0.20;
        double variance = 0.3 * sigma2, covariance = 0.5 * sigma2;
        double meanLog = Math.log(100.0) + (0.06 - 0.03 - 0.5 * sigma2) * 0.5;
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
            DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    Average.Type.Geometric, 1.0, 0, fixingDates, payoff, exercise);
            option.setPricingEngine(engine);
            double expected = BlackFormula.blackFormula(
                    type,
                    Math.exp(meanLog + variance / 2.0),
                    100.0 * Math.exp(0.06 - 0.03),
                    Math.sqrt(variance + sigma2 - 2.0 * covariance),
                    Math.exp(-0.06), 0.0);
            checkSmall("FirstFixingToday " + type,
                    option.NPV() - expected, 1e-12);
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeZeroVolatility}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeZeroVolatility() {
        Date today = Settings.instance.evaluationDate().Date();
        Exercise exercise = new EuropeanExercise(today.add(360));
        for (double r : new double[]{0.06, 0.03, -0.02}) {
            for (double vol : new double[]{0.0, 1e-14}) {
                AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                        geometricAverageStrikeEngine(today, r, 0.03, vol);
                for (Option.Type type : new Option.Type[]{Option.Type.Call,
                        Option.Type.Put}) {
                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
                    DiscreteAveragingAsianOption option =
                            new DiscreteAveragingAsianOption(
                                    Average.Type.Geometric, 1.0, 0,
                                    List.of(today.add(180), today.add(360)),
                                    payoff, exercise);
                    option.setPricingEngine(engine);
                    // Deterministic G averages the forwards at t = 1/2 and t = 1.
                    double forward = 100.0 * Math.exp(r - 0.03);
                    double average = 100.0 * Math.exp((r - 0.03) * 0.75);
                    // QuantLib Option::Type is Put=-1, Call=+1.
                    double typeSign = type == Option.Type.Call ? 1.0 : -1.0;
                    double expected = Math.exp(-r)
                            * Math.max(typeSign * (forward - average), 0.0);
                    checkSmall("ZeroVolatility " + type + " r=" + r
                                    + " vol=" + vol,
                            option.NPV() - expected, 1e-12);
                }
            }
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeSmallVolatility}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeSmallVolatility() {
        Date today = Settings.instance.evaluationDate().Date();
        Exercise exercise = new EuropeanExercise(today.add(360));
        double vol = 1e-9;
        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                geometricAverageStrikeEngine(today, 0.03, 0.03, vol);
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
            DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    Average.Type.Geometric, 1.0, 0, List.of(today),
                    payoff, exercise);
            option.setPricingEngine(engine);
            // With the only fixing today G = S: an at-the-money option worth
            // about 3.9e-8.
            double expected = BlackFormula.blackFormula(type, 100.0, 100.0,
                    vol, Math.exp(-0.03), 0.0);
            checkClose("SmallVolatility " + type, option.NPV(), expected, 1e-2);
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAverageStrikeNonFlatRates}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAverageStrikeNonFlatRates() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual360();
        Exercise exercise = new EuropeanExercise(today.add(360));
        // The zero rate to maturity is 6%, as on a flat curve, but the rate
        // to the first fixing is 1%.
        ZeroCurve riskFree = new ZeroCurve(
                List.of(today, today.add(180), today.add(360)),
                List.of(0.01, 0.01, 0.06), dc);
        YieldTermStructure dividends = flatRate(today, 0.03, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.0, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(dividends, true),
                new Handle<>(riskFree, true),
                new Handle<>(volTS, true));
        AnalyticDiscreteGeometricAverageStrikeAsianEngine engine =
                new AnalyticDiscreteGeometricAverageStrikeAsianEngine(process);
        // With zero volatility G is the geometric mean of the forwards to
        // the two fixings.
        double forward1 = 100.0 * dividends.discount(today.add(180), false)
                / riskFree.discount(today.add(180), false);
        double forward2 = 100.0 * dividends.discount(today.add(360), false)
                / riskFree.discount(today.add(360), false);
        double average = Math.sqrt(forward1 * forward2);
        for (Option.Type type : new Option.Type[]{Option.Type.Call, Option.Type.Put}) {
            PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, 100.0);
            DiscreteAveragingAsianOption option = new DiscreteAveragingAsianOption(
                    Average.Type.Geometric, 1.0, 0,
                    List.of(today.add(180), today.add(360)), payoff, exercise);
            option.setPricingEngine(engine);
            // QuantLib Option::Type is Put=-1, Call=+1.
            double typeSign = type == Option.Type.Call ? 1.0 : -1.0;
            double expected = riskFree.discount(today.add(360), false)
                    * Math.max(typeSign * (forward2 - average), 0.0);
            checkSmall("NonFlatRates " + type, option.NPV() - expected, 1e-12);
        }
    }

    /**
     * Partial port of QuantLib {@code testPastFixings}.
     *
     * <p>This is only the "analytic geometric average-price" section of the
     * official case (about asianoptions.cpp:1695-1725). The full official
     * case also covers MC arithmetic average-price/average-strike, the
     * vector-fixings arithmetic MC interface and MC geometric average-price;
     * those engines are not part of this step and are deliberately not
     * covered here.
     */
    @Test
    public void testPastFixingsAnalyticGeometricAveragePricePartial() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.06);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 100.0);
        Exercise exercise = new EuropeanExercise(
                today.add(new Period(1, TimeUnit.YEARS)));

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        double runningProduct = 1.0;
        int pastFixings = 0;
        List<Date> fixingDates1 = new ArrayList<>();
        for (int i = 0; i <= 12; ++i) {
            fixingDates1.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        DiscreteAveragingAsianOption option3 = new DiscreteAveragingAsianOption(
                Average.Type.Geometric, runningProduct, pastFixings,
                fixingDates1, payoff, exercise);

        pastFixings = 2;
        runningProduct = spot.value() * spot.value();
        List<Date> fixingDates2 = new ArrayList<>();
        for (int i = -2; i <= 12; ++i) {
            fixingDates2.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        DiscreteAveragingAsianOption option4 = new DiscreteAveragingAsianOption(
                Average.Type.Geometric, runningProduct, pastFixings,
                fixingDates2, payoff, exercise);

        AnalyticDiscreteGeometricAveragePriceAsianEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianEngine(stochProcess);

        option3.setPricingEngine(engine);
        option4.setPricingEngine(engine);

        double price3 = option3.NPV();
        double price4 = option4.NPV();

        if (MathUtils.close(price3, price4)) {
            fail("past fixings had no effect on geometric average-price option"
                    + "\n  without fixings: " + price3
                    + "\n  with fixings:    " + price4);
        }
    }

    private static void compareGreeks(Map<String, Double> calculated,
                                      Map<String, Double> expected,
                                      Map<String, Double> tolerance,
                                      Double runningAverage,
                                      Integer pastFixings,
                                      PlainVanillaPayoff payoff,
                                      Exercise maturity,
                                      double u, double q, double r,
                                      Date today, double v) {
        for (Map.Entry<String, Double> entry : calculated.entrySet()) {
            String greek = entry.getKey();
            double expct = expected.get(greek);
            double calcl = entry.getValue();
            double tol = tolerance.get(greek);
            double error = relativeError(expct, calcl, u);
            if (!(error <= tol)) {
                reportFailure(greek, Average.Type.Geometric,
                        runningAverage, pastFixings, new ArrayList<>(),
                        payoff, maturity, u, q, r, today, v,
                        expct, calcl, tol);
            }
        }
    }

    /** QuantLib {@code relativeError} from {@code test-suite/utilities.cpp}. */
    private static double relativeError(double x1, double x2, double reference) {
        if (reference != 0.0) {
            return Math.abs(x1 - x2) / reference;
        }
        return Math.abs(x1 - x2);
    }

    private static void reportFailure(String greekName,
                                      Average.Type averageType,
                                      Double runningAccumulator,
                                      Integer pastFixings,
                                      List<Date> fixingDates,
                                      PlainVanillaPayoff payoff,
                                      Exercise exercise,
                                      double s, double q, double r,
                                      Date today, double v,
                                      double expected, double calculated,
                                      double tolerance) {
        fail(exercise.type() + " Asian option with "
                + averageTypeToString(averageType) + " and "
                + payoff.optionType() + " payoff:\n"
                + "    running variable: " + checkNull(runningAccumulator) + "\n"
                + "    past fixings:     " + checkNull(pastFixings) + "\n"
                + "    future fixings:   " + fixingDates.size() + "\n"
                + "    underlying value: " + s + "\n"
                + "    strike:           " + payoff.strike() + "\n"
                + "    dividend yield:   " + q + "\n"
                + "    risk-free rate:   " + r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + exercise.lastDate() + "\n"
                + "    volatility:       " + v + "\n\n"
                + "    expected   " + greekName + ": " + expected + "\n"
                + "    calculated " + greekName + ": " + calculated + "\n"
                + "    error:            " + Math.abs(expected - calculated) + "\n"
                + "    tolerance:        " + tolerance);
    }

    private static String checkNull(Number value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double && Double.isNaN((Double) value)) {
            return "null";
        }
        return value.toString();
    }

    private static String averageTypeToString(Average.Type averageType) {
        if (averageType == Average.Type.Geometric) {
            return "Geometric Averaging";
        } else if (averageType == Average.Type.Arithmetic) {
            return "Arithmetic Averaging";
        }
        throw new IllegalArgumentException("unknown averaging");
    }

    private static YieldTermStructure flatRate(Date today, double rate,
                                               DayCounter dc) {
        return flatRate(today, new SimpleQuote(rate), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, double vol,
                                                 DayCounter dc) {
        return flatVol(today, new SimpleQuote(vol), dc);
    }

    /** QuantLib {@code QL_CHECK_SMALL}: absolute error tolerance. */
    private static void checkSmall(String label, double value, double tolerance) {
        if (!(Math.abs(value) <= tolerance)) {
            fail(label + " failed: |" + value + "| > " + tolerance);
        }
    }

    /**
     * QuantLib {@code QL_CHECK_CLOSE}: {@code percent} is a percentage, as in
     * Boost's {@code check_is_close}.
     */
    private static void checkClose(String label, double calculated,
                                   double expected, double percent) {
        double error = Math.abs(calculated - expected)
                / Math.max(Math.abs(expected), Math.abs(calculated)) * 100.0;
        if (!(error <= percent)) {
            fail(label + " failed: " + calculated + " vs " + expected
                    + " (" + error + "% > " + percent + "%)");
        }
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Continuous, Frequency.ANNUAL);
    }

    /** QuantLib {@code flatRate(quote, dc)}: reference date follows Settings. */
    private static Handle<YieldTermStructure> flatRate(SimpleQuote rate,
                                                        DayCounter dc) {
        return new Handle<>(new FlatForward(0, new NullCalendar(),
                new Handle<>(rate, true), dc, Compounding.Continuous,
                Frequency.ANNUAL), true);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }

    /** QuantLib {@code flatVol(quote, dc)}: reference date follows Settings. */
    private static Handle<BlackVolTermStructure> flatVol(SimpleQuote vol,
                                                          DayCounter dc) {
        return new Handle<>(new BlackConstantVol(0, new NullCalendar(),
                new Handle<>(vol, true), dc), true);
    }
}
