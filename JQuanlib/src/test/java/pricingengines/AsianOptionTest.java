package pricingengines;

import jquant.Average;
import jquant.Compounding;
import jquant.ContinuousAveragingAsianOption;
import jquant.DiscreteAveragingAsianOption;
import jquant.EuropeanExercise;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.MathUtils;
import jquant.math.integrals.GaussLegendreIntegration;
import jquant.math.randomnumbers.RngType;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.asian.AnalyticContinuousGeometricAveragePriceAsianEngine;
import jquant.pricingengines.asian.AnalyticContinuousGeometricAveragePriceAsianHestonEngine;
import jquant.pricingengines.asian.AnalyticDiscreteGeometricAveragePriceAsianEngine;
import jquant.pricingengines.asian.AnalyticDiscreteGeometricAveragePriceAsianHestonEngine;
import jquant.pricingengines.asian.AnalyticDiscreteGeometricAverageStrikeAsianEngine;
import jquant.pricingengines.asian.ChoiAsianEngine;
import jquant.pricingengines.asian.ContinuousArithmeticAsianLevyEngine;
import jquant.pricingengines.asian.ContinuousArithmeticAsianVecerEngine;
import jquant.pricingengines.asian.FdBlackScholesAsianEngine;
import jquant.pricingengines.asian.MakeMCDiscreteArithmeticAPEngine;
import jquant.pricingengines.asian.MakeMCDiscreteArithmeticAPHestonEngine;
import jquant.pricingengines.asian.MakeMCDiscreteArithmeticASEngine;
import jquant.pricingengines.asian.MakeMCDiscreteGeometricAPEngine;
import jquant.pricingengines.asian.MakeMCDiscreteGeometricAPHestonEngine;
import jquant.pricingengines.asian.PastFixingsOnly;
import jquant.pricingengines.asian.TurnbullWakemanAsianEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVarianceCurve;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
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

    /**
     * Default evaluation date used by the official QuantLib test suite when
     * no {@code --date} argument is given.  The Heston Asian reference values
     * below are compared on that known-good date; the suite notes that some
     * dates make date-dependent tests fail.
     */
    private static final Date KNOWN_GOOD_EVALUATION_DATE =
            new Date(16, Month.SEPTEMBER, 2015);

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
     * QuantLib {@code testMCDiscreteGeometricAveragePrice}.
     *
     * <p>Data from "Implementing Derivatives Model", Clewlow, Strickland,
     * p.118-123.
     */
    @Test
    public void testMCDiscreteGeometricAveragePrice() {
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

        double tolerance = 4.0e-3;

        PricingEngine engine = new MakeMCDiscreteGeometricAPEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(8191)
                .build();

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

        PricingEngine engine2 =
                new AnalyticDiscreteGeometricAveragePriceAsianEngine(
                        stochProcess);
        option.setPricingEngine(engine2);
        double expected = option.NPV();

        if (Math.abs(calculated - expected) > tolerance) {
            reportFailure("value", averageType, runningAccumulator,
                    pastFixings, fixingDates, payoff, exercise, spot.value(),
                    qRate.value(), rRate.value(), today, vol.value(),
                    expected, calculated, tolerance);
        }
    }

    /**
     * QuantLib {@code testMCDiscreteArithmeticAverageStrike}.
     *
     * <p>Data from "Asian Option", Levy, 1997, in "Exotic Options: The
     * State of the Art", edited by Clewlow, Strickland.
     */
    @Test
    public void testMCDiscreteArithmeticAverageStrike() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.06);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        Average.Type averageType = Average.Type.Arithmetic;
        double runningSum = 0.0;
        int pastFixings = 0;
        for (DiscreteAverageData l : cases5) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(l.type, l.strike);

            double dt = l.length / (l.fixings - 1);
            double[] timeIncrements = new double[l.fixings];
            Date[] fixingDates = new Date[l.fixings];
            timeIncrements[0] = l.first;
            fixingDates[0] = today.add(timeToDays(timeIncrements[0]));
            for (int i = 1; i < l.fixings; i++) {
                timeIncrements[i] = i * dt + l.first;
                fixingDates[i] = today.add(timeToDays(timeIncrements[i]));
            }
            Exercise exercise =
                    new EuropeanExercise(fixingDates[l.fixings - 1]);

            spot.setValue(l.underlying);
            qRate.setValue(l.dividendYield);
            rRate.setValue(l.riskFreeRate);
            vol.setValue(l.volatility);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true), new Handle<>(qTS, true),
                            new Handle<>(rTS, true), new Handle<>(volTS, true));

            PricingEngine engine = new MakeMCDiscreteArithmeticASEngine(
                    stochProcess, RngType.LOW_DISCREPANCY)
                    .withSeed(3456789)
                    .withSamples(1023)
                    .build();

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(
                            averageType, runningSum, pastFixings,
                            List.of(fixingDates), payoff, exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double expected = l.result;
            double tolerance = 2.0e-2;
            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        List.of(fixingDates), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today, vol.value(),
                        expected, calculated, tolerance);
            }
        }
    }

    /**
     * QuantLib {@code testMCDiscreteArithmeticAverageStrikeExerciseDate}.
     *
     * <p>Issue #646: the engine was insensitive to the exercise date because
     * the time grid did not include it.
     */
    @Test
    public void testMCDiscreteArithmeticAverageStrikeExerciseDate() {
        Date today = Date.todaysDate();
        DayCounter dc = new Actual360();

        SimpleQuote spot = new SimpleQuote(90.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.0);
        SimpleQuote vol = new SimpleQuote(0.20);

        YieldTermStructure qTS = flatRate(today, qRate, dc);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));

        PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 90.0);
        Average.Type averageType = Average.Type.Arithmetic;
        double runningSum = 0.0;
        int pastFixings = 0;

        // monthly fixings for 6 months
        List<Date> fixingDates = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            fixingDates.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        // price with exercise at last fixing
        Exercise exercise1 =
                new EuropeanExercise(fixingDates.get(fixingDates.size() - 1));
        DiscreteAveragingAsianOption option1 = new DiscreteAveragingAsianOption(
                averageType, runningSum, pastFixings, fixingDates,
                payoff, exercise1);
        option1.setPricingEngine(new MakeMCDiscreteArithmeticASEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSeed(42).withSamples(8191).build());
        double price1 = option1.NPV();

        // price with exercise 3 months after last fixing
        Date lastFixing = fixingDates.get(fixingDates.size() - 1);
        Exercise exercise2 = new EuropeanExercise(
                lastFixing.add(new Period(3, TimeUnit.MONTHS)));
        DiscreteAveragingAsianOption option2 = new DiscreteAveragingAsianOption(
                averageType, runningSum, pastFixings, fixingDates,
                payoff, exercise2);
        option2.setPricingEngine(new MakeMCDiscreteArithmeticASEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSeed(42).withSamples(8191).build());
        double price2 = option2.NPV();

        // with r=q=0 and vol>0, a later exercise date must give a higher price
        if (price2 <= price1) {
            fail("average-strike Asian option should be sensitive to "
                    + "exercise date: price with exercise at last fixing = "
                    + price1 + ", price with exercise 3 months later = "
                    + price2);
        }
    }

    /**
     * QuantLib {@code testTurnbullWakemanAsianEngine}.
     *
     * <p>Data from Haug, "Option Pricing Formulas", Table 4-28, p.201,
     * with flat, upward- and downward-sloping volatility term structures.
     */
    @Test
    public void testTurnbullWakemanAsianEngine() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        for (DiscreteAverageDataTermStructure l : casesTW) {
            double dt = (l.expiry - l.first) / (l.fixings - 1);
            List<Date> fixingDates = new ArrayList<>(l.fixings);
            fixingDates.add(today.add(timeToDays(l.first)));
            for (int i = 1; i < l.fixings; i++) {
                fixingDates.add(today.add(timeToDays(i * dt + l.first)));
            }

            SimpleQuote spot = new SimpleQuote(l.underlying);
            YieldTermStructure qTS =
                    flatRate(today, l.b + l.riskFreeRate, dc);
            YieldTermStructure rTS = flatRate(today, l.riskFreeRate, dc);
            BlackVolTermStructure volTS;
            double volSlope = 0.005;
            if ("flat".equals(l.slope)) {
                volTS = flatVol(today, l.volatility, dc);
            } else {
                List<Double> volatilities = new ArrayList<>(l.fixings);
                for (int i = 0; i < l.fixings; ++i) {
                    if ("up".equals(l.slope)) {
                        volatilities.add(l.volatility
                                - (l.fixings - 1) * volSlope + i * volSlope);
                    } else if ("down".equals(l.slope)) {
                        volatilities.add(l.volatility
                                + (l.fixings - 1) * volSlope - i * volSlope);
                    } else {
                        fail("unexpected slope type in engine test case");
                    }
                }
                volTS = new BlackVarianceCurve(today, fixingDates,
                        volatilities, dc, "up".equals(l.slope));
            }

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(l.type, l.strike);
            Date maturity = today.add(timeToDays(l.expiry));
            Exercise exercise = new EuropeanExercise(maturity);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true), new Handle<>(qTS, true),
                            new Handle<>(rTS, true), new Handle<>(volTS, true));

            PricingEngine engine = new TurnbullWakemanAsianEngine(stochProcess);

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(
                            Average.Type.Arithmetic, 0.0, 0, fixingDates,
                            payoff, exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double expected = l.result;
            double tolerance = 2.5e-3;
            double error = Math.abs(expected - calculated);
            if (error > tolerance) {
                fail("Failed to reproduce expected NPV:"
                        + "\n    type:            " + l.type
                        + "\n    spot:            " + l.underlying
                        + "\n    strike:          " + l.strike
                        + "\n    dividend yield:  "
                        + (l.b + l.riskFreeRate)
                        + "\n    risk-free rate:  " + l.riskFreeRate
                        + "\n    volatility:      " + l.volatility
                        + "\n    slope:           " + l.slope
                        + "\n    reference date:  " + today
                        + "\n    expiry:          " + l.expiry
                        + "\n    expected value:  " + expected
                        + "\n    calculated:      " + calculated
                        + "\n    error:           " + error);
            }

            // Compare greeks to numerical greeks
            double dS = 0.001;
            double delta = option.delta();
            double gamma = option.gamma();

            SimpleQuote spotUp = new SimpleQuote(l.underlying + dS);
            SimpleQuote spotDown = new SimpleQuote(l.underlying - dS);

            BlackScholesMertonProcess stochProcessUp =
                    new BlackScholesMertonProcess(
                            new Handle<>(spotUp, true), new Handle<>(qTS, true),
                            new Handle<>(rTS, true), new Handle<>(volTS, true));
            BlackScholesMertonProcess stochProcessDown =
                    new BlackScholesMertonProcess(
                            new Handle<>(spotDown, true), new Handle<>(qTS, true),
                            new Handle<>(rTS, true), new Handle<>(volTS, true));

            option.setPricingEngine(
                    new TurnbullWakemanAsianEngine(stochProcessUp));
            double calculatedUp = option.NPV();

            option.setPricingEngine(
                    new TurnbullWakemanAsianEngine(stochProcessDown));
            double calculatedDown = option.NPV();

            double deltaBump = (calculatedUp - calculatedDown) / (2.0 * dS);
            double gammaBump = (calculatedUp + calculatedDown
                    - 2.0 * calculated) / (dS * dS);

            tolerance = 1.0e-6;
            double deltaError = Math.abs(deltaBump - delta);
            if (deltaError > tolerance) {
                fail("Analytical delta failed to match bump delta:"
                        + "\n    type:            " + l.type
                        + "\n    spot:            " + l.underlying
                        + "\n    strike:          " + l.strike
                        + "\n    dividend yield:  "
                        + (l.b + l.riskFreeRate)
                        + "\n    risk-free rate:  " + l.riskFreeRate
                        + "\n    volatility:      " + l.volatility
                        + "\n    slope:           " + l.slope
                        + "\n    reference date:  " + today
                        + "\n    expiry:          " + l.expiry
                        + "\n    analytic delta:  " + delta
                        + "\n    bump delta:      " + deltaBump
                        + "\n    error:           " + deltaError);
            }

            double gammaError = Math.abs(gammaBump - gamma);
            if (gammaError > tolerance) {
                fail("Analytical gamma failed to match bump gamma:"
                        + "\n    type:            " + l.type
                        + "\n    spot:            " + l.underlying
                        + "\n    strike:          " + l.strike
                        + "\n    dividend yield:  "
                        + (l.b + l.riskFreeRate)
                        + "\n    risk-free rate:  " + l.riskFreeRate
                        + "\n    volatility:      " + l.volatility
                        + "\n    slope:           " + l.slope
                        + "\n    reference date:  " + today
                        + "\n    expiry:          " + l.expiry
                        + "\n    analytic gamma:  " + gamma
                        + "\n    bump gamma:      " + gammaBump
                        + "\n    error:           " + gammaError);
            }
        }
    }

    /**
     * QuantLib {@code testTurnbullWakemanAllFixingsInThePast}.
     */
    @Test
    public void testTurnbullWakemanAllFixingsInThePast() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.25, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = new ArrayList<>();
        for (int i = 12; i > 0; --i) {
            fixingDates.add(today.substract(30 * i));
        }
        List<Double> pastFixings = new ArrayList<>();
        for (int i = 0; i < 12; ++i) {
            pastFixings.add(100.0);
        }
        Exercise exercise = new EuropeanExercise(today.add(14));

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            for (double strike : new double[]{95.0, 100.0, 105.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                DiscreteAveragingAsianOption option =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, fixingDates, payoff,
                                exercise, pastFixings);
                option.setPricingEngine(engine);

                // The fixed average is 100; only discounting to the payment
                // date remains.
                double expected = payoff.value(100.0)
                        * rTS.discount(exercise.lastDate(), false);
                double npvError = option.NPV() - expected;
                if (!(Math.abs(npvError) <= 1.0e-12)) {
                    fail("NPV failed: |" + npvError + "| > " + 1.0e-12);
                }
                if (option.delta() != 0.0) {
                    fail("delta should be exactly zero, got " + option.delta());
                }
                if (option.gamma() != 0.0) {
                    fail("gamma should be exactly zero, got " + option.gamma());
                }
            }
        }
    }

    /**
     * QuantLib {@code testTurnbullWakemanLastFixingToday}.
     */
    @Test
    public void testTurnbullWakemanLastFixingToday() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.25, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = new ArrayList<>();
        for (int i = 5; i > 0; --i) {
            fixingDates.add(today.substract(30 * i));
        }
        fixingDates.add(today);
        List<Double> pastFixings = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            pastFixings.add(100.0);
        }
        Exercise exercise = new EuropeanExercise(today.add(2));

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            for (double strike : new double[]{95.0, 105.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                DiscreteAveragingAsianOption option =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, fixingDates, payoff,
                                exercise, pastFixings);
                option.setPricingEngine(engine);

                // Today's fixing equals spot, so the average is
                // (5 * 100 + 100) / 6.
                double discount = rTS.discount(exercise.lastDate(), false);
                double expected = payoff.value(100.0) * discount;
                int typeSign = type == Option.Type.Call ? 1 : -1;
                double expectedDelta = expected > 0.0
                        ? typeSign * discount / 6.0 : 0.0;
                double npvError = option.NPV() - expected;
                if (!(Math.abs(npvError) <= 1.0e-12)) {
                    fail("NPV failed: |" + npvError + "| > " + 1.0e-12);
                }
                double deltaError = option.delta() - expectedDelta;
                if (!(Math.abs(deltaError) <= 1.0e-12)) {
                    fail("delta failed: |" + deltaError + "| > " + 1.0e-12);
                }
                if (option.gamma() != 0.0) {
                    fail("gamma should be exactly zero, got " + option.gamma());
                }
                if (option.result("sigma", Double.class) != 0.0) {
                    fail("sigma should be exactly zero, got "
                            + option.result("sigma", Double.class));
                }
                @SuppressWarnings("unchecked")
                List<Double> spotVols = option.result("spotVols", List.class);
                if (spotVols.size() != 1) {
                    fail("expected one spot volatility, got "
                            + spotVols.size());
                }
                double spotVolError = spotVols.get(0) - 0.25;
                if (!(Math.abs(spotVolError) <= 1.0e-12)) {
                    fail("spot volatility failed: |" + spotVolError
                            + "| > " + 1.0e-12);
                }
            }
        }
    }

    /**
     * QuantLib {@code testLevyEngine}.
     *
     * <p>Data from Haug, "Option Pricing Formulas", p.99-100.
     */
    @Test
    public void testLevyEngine() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        for (ContinuousAverageData l : casesLevy) {
            SimpleQuote spot = new SimpleQuote(l.spot);
            YieldTermStructure qTS =
                    flatRate(today, l.dividendYield, dc);
            YieldTermStructure rTS =
                    flatRate(today, l.riskFreeRate, dc);
            BlackVolTermStructure volTS = flatVol(today, l.volatility, dc);

            SimpleQuote average = new SimpleQuote(l.currentAverage);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(l.type, l.strike);

            Date startDate = today.substract(l.elapsed);
            Date maturity = startDate.add(l.length);
            Exercise exercise = new EuropeanExercise(maturity);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true), new Handle<>(qTS, true),
                            new Handle<>(rTS, true), new Handle<>(volTS, true));

            PricingEngine engine = new ContinuousArithmeticAsianLevyEngine(
                    stochProcess, new Handle<>(average, true));

            ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(
                            Average.Type.Arithmetic, startDate, payoff,
                            exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double expected = l.result;
            double tolerance = 1.0e-4;
            double error = Math.abs(expected - calculated);
            if (error > tolerance) {
                fail("Asian option with Levy engine:"
                        + "\n    spot:            " + l.spot
                        + "\n    current average: " + l.currentAverage
                        + "\n    strike:          " + l.strike
                        + "\n    dividend yield:  " + l.dividendYield
                        + "\n    risk-free rate:  " + l.riskFreeRate
                        + "\n    volatility:      " + l.volatility
                        + "\n    reference date:  " + today
                        + "\n    length:          " + l.length
                        + "\n    elapsed:         " + l.elapsed
                        + "\n    expected value:  " + expected
                        + "\n    calculated:      " + calculated
                        + "\n    error:           " + error);
            }
        }
    }

    /**
     * QuantLib {@code testLevyZeroVolatility}.
     */
    @Test
    public void testLevyZeroVolatility() {
        Date today = new Date(20, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360();
        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote vol = new SimpleQuote(0.0);
        SimpleQuote q = new SimpleQuote(0.05);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        SimpleQuote average = new SimpleQuote(97.0);
        PricingEngine engine = new ContinuousArithmeticAsianLevyEngine(
                process, new Handle<>(average, true));
        Exercise exercise = new EuropeanExercise(today.add(360));

        double previousCutoff = Math.sqrt(MathUtils.QL_EPSILON);
        double[] sigmaValues = {
                0.0, 1.0e-8, 0.99 * previousCutoff, previousCutoff,
                1.01 * previousCutoff, 2.0 * previousCutoff, 1.0e-7
        };
        for (int elapsed : new int[]{0, 180}) {
            for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
                for (double strike : new double[]{30.0, 90.0, 100.0, 110.0}) {
                    PlainVanillaPayoff payoff =
                            new PlainVanillaPayoff(type, strike);
                    ContinuousAveragingAsianOption option =
                            new ContinuousAveragingAsianOption(
                                    Average.Type.Arithmetic,
                                    today.substract(elapsed), payoff, exercise);
                    option.setPricingEngine(engine);
                    for (double yield : new double[]{0.05, 0.02, 0.08}) {
                        q.setValue(yield);
                        // Integrate the deterministic forward
                        // S(t)=100 exp((r-q)t), and combine it with the
                        // already observed running average.
                        double b = 0.05 - yield;
                        double futureIntegral = b == 0.0 ? 100.0
                                : 100.0 * Math.expm1(b) / b;
                        double mean = (97.0 * elapsed / 360.0
                                + futureIntegral)
                                / (1.0 + elapsed / 360.0);
                        double expected = Math.exp(-0.05)
                                * payoff.value(mean);
                        for (double sigma : sigmaValues) {
                            vol.setValue(sigma);
                            double calculated = option.NPV();
                            if (!Double.isFinite(calculated)) {
                                fail("Levy " + type + ", elapsed=" + elapsed
                                        + ", strike=" + strike + ", q=" + yield
                                        + ", vol=" + sigma
                                        + ": calculated is not finite");
                            }
                            // At the kink the true time value is
                            // O(sigma * spot).
                            double tolerance = sigma == 0.0
                                    ? 1.0e-12 : 1.0e-5;
                            if (!(Math.abs(calculated - expected) < tolerance)) {
                                fail("Levy " + type + ", elapsed=" + elapsed
                                        + ", strike=" + strike + ", q=" + yield
                                        + ", vol=" + sigma + ": calculated "
                                        + calculated + ", expected " + expected);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * QuantLib {@code testLevyLowVolatilityContinuity}.
     */
    @Test
    public void testLevyLowVolatilityContinuity() {
        Date today = new Date(20, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360();
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        PricingEngine engine = new ContinuousArithmeticAsianLevyEngine(
                process, new Handle<>(new SimpleQuote(100.0), true));

        for (int elapsed : new int[]{0, 180}) {
            for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
                ContinuousAveragingAsianOption option =
                        new ContinuousAveragingAsianOption(
                                Average.Type.Arithmetic,
                                today.substract(elapsed),
                                new PlainVanillaPayoff(type, 100.0),
                                new EuropeanExercise(today.add(360)));
                option.setPricingEngine(engine);
                vol.setValue(0.0);
                double zeroVolNpv = option.NPV();
                if (!(Math.abs(zeroVolNpv) <= 1.0e-12)) {
                    fail("zero-volatility NPV failed: |" + zeroVolNpv
                            + "| > " + 1.0e-12);
                }
                // At zero carry Var(A_future) ~ (100/T)^2 sigma^2/3.
                // An ATM call or put has value ~ discount * stddev /
                // sqrt(2*pi).
                double slope = Math.exp(-0.05) * 100.0
                        / ((1.0 + elapsed / 360.0)
                        * Math.sqrt(6.0 * MathUtils.M_PI));
                double previous = MathUtils.QL_MAX_REAL;
                for (double sigma : new double[]{1.0e-3, 1.0e-4, 1.0e-5,
                        1.0e-6, 1.0e-7, 1.0e-8}) {
                    vol.setValue(sigma);
                    double npv = option.NPV();
                    if (!Double.isFinite(npv)) {
                        fail("type=" + type + ", elapsed=" + elapsed
                                + ", vol=" + sigma + ": NPV is not finite");
                    }
                    if (!(npv > 0.0)) {
                        fail("type=" + type + ", elapsed=" + elapsed
                                + ", vol=" + sigma + ": NPV is not positive");
                    }
                    if (!(npv < previous)) {
                        fail("type=" + type + ", elapsed=" + elapsed
                                + ", vol=" + sigma
                                + ": NPV is not decreasing");
                    }
                    if (!(Math.abs(npv / sigma - slope) <= 1.0e-5)) {
                        fail("type=" + type + ", elapsed=" + elapsed
                                + ", vol=" + sigma + ": npv/sigma - slope = "
                                + (npv / sigma - slope));
                    }
                    previous = npv;
                }
            }
        }
    }

    /**
     * QuantLib {@code testLevyVarianceReference}.
     */
    @Test
    public void testLevyVarianceReference() {
        Date today = new Date(20, Month.OCTOBER, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual360();
        SimpleQuote q = new SimpleQuote(0.05);
        SimpleQuote vol = new SimpleQuote(1.0e-3);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(flatRate(today, q, dc), true),
                new Handle<>(flatRate(today, 0.05, dc), true),
                new Handle<>(flatVol(today, vol, dc), true));
        PricingEngine engine = new ContinuousArithmeticAsianLevyEngine(
                process, new Handle<>(new SimpleQuote(97.0), true));
        GaussLegendreIntegration integrate = new GaussLegendreIntegration(32);

        for (int elapsed : new int[]{0, 180}) {
            // Include near-zero carry, both signs, and the series scaling
            // boundary.
            for (double b : new double[]{0.0, -1.0e-10, 1.0e-10, -0.03,
                    0.03, -1.0, 1.0}) {
                for (double sigma : new double[]{1.0e-8, 1.0e-3,
                        Math.sqrt(0.499), Math.sqrt(0.501)}) {
                    checkLevyVariancePrice(today, q, vol, engine, integrate,
                            360, elapsed, b, sigma);
                }
            }
            for (int remaining : new int[]{1, 10800}) {
                for (double b : new double[]{-0.03, 0.0, 0.03}) {
                    checkLevyVariancePrice(today, q, vol, engine, integrate,
                            remaining, elapsed, b, 1.0e-3);
                }
            }
        }
    }

    /**
     * QuantLib {@code testVecerEngine}.
     */
    @Test
    public void testVecerEngine() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dayCounter = new Actual360();

        Option.Type type = Option.Type.Call;
        Handle<YieldTermStructure> q = new Handle<>(
                flatRate(today, 0.0, dayCounter), true);

        int timeSteps = 200;
        int assetSteps = 200;

        for (VecerData i : casesVecer) {
            Handle<Quote> u = new Handle<>(new SimpleQuote(i.spot()), true);
            Handle<YieldTermStructure> r = new Handle<>(
                    flatRate(today, i.riskFreeRate(), dayCounter), true);
            Handle<BlackVolTermStructure> sigma = new Handle<>(
                    flatVol(today, i.volatility(), dayCounter), true);
            BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(u, q, r, sigma);

            Date maturity = today.add(i.length() * 360);
            Exercise exercise = new EuropeanExercise(maturity);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, i.strike());
            Handle<Quote> average =
                    new Handle<>(new SimpleQuote(0.0), true);

            ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(
                            Average.Type.Arithmetic, payoff, exercise);
            option.setPricingEngine(
                    new ContinuousArithmeticAsianVecerEngine(
                            process, average, today, timeSteps, assetSteps,
                            -1.0, 1.0));

            double calculated = option.NPV();
            double error = Math.abs(calculated - i.result());
            if (error > i.tolerance()) {
                fail("Failed to reproduce expected NPV"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + i.result()
                        + "\n    expected:   " + i.result()
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + i.tolerance());
            }
        }
    }

    /**
     * QuantLib {@code testAnalyticContinuousGeometricAveragePriceHeston}.
     */
    @Test
    public void testAnalyticContinuousGeometricAveragePriceHeston() {
        Settings.instance.evaluationDate().equal(KNOWN_GOOD_EVALUATION_DATE);
        DayCounter dc = new Actual365Fixed();
        Date today = Settings.instance.evaluationDate().Date();
        Option.Type type = Option.Type.Call;
        Average.Type averageType = Average.Type.Geometric;

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.05);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        int[] days = {73, 73, 73, 73, 73, 548, 548, 548, 548, 548,
                1095, 1095, 1095, 1095, 1095};
        double[] strikes = {90.0, 95.0, 100.0, 105.0, 110.0,
                90.0, 95.0, 100.0, 105.0, 110.0,
                90.0, 95.0, 100.0, 105.0, 110.0};
        double[] prices = {10.6571, 6.5871, 3.4478, 1.4552, 0.4724,
                16.5030, 13.7625, 11.3374, 9.2245, 7.4122,
                20.5102, 18.3060, 16.2895, 14.4531, 12.7882};
        double[] prices2 = {10.6425, 6.4362, 3.1578, 1.1936, 0.3609,
                14.9955, 11.6707, 8.7767, 6.3818, 4.5118,
                18.1219, 15.2009, 12.5707, 10.2539, 8.2611};
        double tolerance = 1.0e-2;

        double v0 = 0.09;
        double kappa = 1.15;
        double theta = 0.348;
        double sigma = 0.39;
        double rho = -0.64;

        HestonProcess hestonProcess = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), v0, kappa, theta, sigma, rho);
        AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
                        hestonProcess);

        for (int i = 0; i < strikes.length; i++) {
            double strike = strikes[i];
            int day = days[i];
            double expected = prices[i];

            Date expiryDate = today.add(day);
            Exercise exercise = new EuropeanExercise(expiryDate);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, strike);

            ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff,
                            exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, 1.0, 0,
                        new ArrayList<>(), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today,
                        Math.sqrt(v0), expected, calculated, tolerance);
            }
        }

        double v0_2 = 0.09;
        double kappa_2 = 2.0;
        double theta_2 = 0.09;
        double sigma_2 = 1.0;
        double rho_2 = -0.3;

        HestonProcess hestonProcess_2 = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), v0_2, kappa_2, theta_2,
                sigma_2, rho_2);
        AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine_2 =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
                        hestonProcess_2);

        for (int i = 0; i < strikes.length; i++) {
            double strike = strikes[i];
            int day = days[i];
            double expected = prices2[i];

            Date expiryDate = today.add(day);
            Exercise exercise = new EuropeanExercise(expiryDate);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, strike);

            ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff,
                            exercise);
            option.setPricingEngine(engine_2);

            double calculated = option.NPV();
            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, 1.0, 0,
                        new ArrayList<>(), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today,
                        Math.sqrt(v0), expected, calculated, tolerance);
            }
        }

        int[] days_3 = {30, 91, 182, 365, 730, 1095,
                30, 91, 182, 365, 730, 1095,
                30, 91, 182, 365, 730, 1095};
        double[] strikes_3 = {90, 90, 90, 90, 90, 90,
                100, 100, 100, 100, 100, 100,
                110, 110, 110, 110, 110, 110};
        double[] tol_3 = {2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2,
                2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2,
                2.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2, 1.0e-2};
        double[] prices_3 = {10.1513, 10.8175, 11.8664, 13.5931, 16.0988,
                17.9475, 2.0472, 3.5735, 5.0588, 7.1132, 9.9139, 11.9959,
                0.0350, 0.4869, 1.3376, 2.8569, 5.2804, 7.2682};

        double v0_3 = 0.09;
        double kappa_3 = 1.15;
        double theta_3 = 0.0348;
        double sigma_3 = 0.39;
        double rho_3 = -0.64;

        HestonProcess hestonProcess_3 = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), v0_3, kappa_3, theta_3,
                sigma_3, rho_3);
        AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine_3 =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
                        hestonProcess_3);

        for (int i = 0; i < strikes_3.length; i++) {
            double strike = strikes_3[i];
            int day = days_3[i];
            double expected = prices_3[i];
            double tol = tol_3[i];

            Date expiryDate = today.add(day);
            Exercise exercise = new EuropeanExercise(expiryDate);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, strike);

            ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(averageType, payoff,
                            exercise);
            option.setPricingEngine(engine_3);

            double calculated = option.NPV();
            if (Math.abs(calculated - expected) > tol) {
                reportFailure("value", averageType, 1.0, 0,
                        new ArrayList<>(), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today,
                        Math.sqrt(v0), expected, calculated, tol);
            }
        }
    }

    /**
     * QuantLib {@code testAnalyticDiscreteGeometricAveragePriceHeston}.
     */
    @Test
    public void testAnalyticDiscreteGeometricAveragePriceHeston() {
        Settings.instance.evaluationDate().equal(KNOWN_GOOD_EVALUATION_DATE);
        double[] tol = {3.0e-2, 2.0e-2, 2.0e-2, 2.0e-2, 3.0e-2, 4.0e-2,
                8.0e-2, 1.0e-2, 2.0e-2, 3.0e-2, 3.0e-2, 4.0e-2,
                2.0e-2, 1.0e-2, 1.0e-2, 2.0e-2, 3.0e-2, 4.0e-2};

        DayCounter dc = new Actual365Fixed();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.05);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        HestonProcess hestonProcess = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), 0.09, 1.15, 0.0348, 0.39, -0.64);

        PricingEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(
                        hestonProcess);

        testDiscreteGeometricAveragePriceHeston(engine, tol);
    }

    /**
     * QuantLib {@code testMCDiscreteGeometricAveragePriceHeston}.
     */
    @Test
    public void testMCDiscreteGeometricAveragePriceHeston() {
        Settings.instance.evaluationDate().equal(KNOWN_GOOD_EVALUATION_DATE);
        double[] tol = {4.0e-2, 2.0e-2, 2.0e-2, 4.0e-2, 8.0e-2, 2.0e-1,
                1.0e-1, 4.0e-2, 3.0e-2, 2.0e-2, 9.0e-2, 2.0e-1,
                2.0e-2, 1.0e-2, 2.0e-2, 2.0e-2, 7.0e-2, 2.0e-1};

        DayCounter dc = new Actual365Fixed();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.05);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        HestonProcess hestonProcess = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), 0.09, 1.15, 0.0348, 0.39, -0.64);

        PricingEngine engine = new MakeMCDiscreteGeometricAPHestonEngine(
                hestonProcess, RngType.LOW_DISCREPANCY)
                .withSamples(8191)
                .withSeed(43)
                .build();

        testDiscreteGeometricAveragePriceHeston(engine, tol);
    }

    /**
     * QuantLib {@code testMCDiscreteArithmeticAveragePriceHeston}.
     */
    @Test
    public void testMCDiscreteArithmeticAveragePriceHeston() {
        Settings.instance.evaluationDate().equal(KNOWN_GOOD_EVALUATION_DATE);
        double vol = 0.3;
        double v0 = vol * vol;
        double kappa = 11.35;
        double theta = 0.022;
        double sigma = 0.618;
        double rho = -0.5;

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.03);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.06);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        Average.Type averageType = Average.Type.Arithmetic;
        double runningSum = 0.0;
        int pastFixings = 0;

        for (DiscreteAverageData l : casesHestonArithmetic) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(l.type(), l.strike());

            double dt = l.length() / (l.fixings() - 1);
            double[] timeIncrements = new double[l.fixings()];
            List<Date> fixingDates = new ArrayList<>();
            timeIncrements[0] = l.first();
            fixingDates.add(today.add(
                    (int) (timeIncrements[0] * 365.25)));
            for (int i = 1; i < l.fixings(); i++) {
                timeIncrements[i] = i * dt + l.first();
                fixingDates.add(today.add(
                        (int) (timeIncrements[i] * 365.25)));
            }
            Exercise exercise = new EuropeanExercise(
                    fixingDates.get(l.fixings() - 1));

            spot.setValue(l.underlying());
            qRate.setValue(l.dividendYield());
            rRate.setValue(l.riskFreeRate());

            HestonProcess hestonProcess = new HestonProcess(
                    new Handle<>(rTS, true), new Handle<>(qTS, true),
                    new Handle<>(spot, true), v0, kappa, theta, sigma, rho);

            PricingEngine engine = new MakeMCDiscreteArithmeticAPHestonEngine(
                    hestonProcess, RngType.LOW_DISCREPANCY)
                    .withSeed(42)
                    .withSamples(4095)
                    .build();

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(averageType, runningSum,
                            pastFixings, fixingDates, payoff, exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double expected = l.result();
            double tolerance = 5.0e-2;
            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        fixingDates, payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today, vol, expected,
                        calculated, tolerance);
            }

            PricingEngine engine2 =
                    new MakeMCDiscreteArithmeticAPHestonEngine(
                            hestonProcess, RngType.LOW_DISCREPANCY)
                            .withSeed(42)
                            .withSteps(48)
                            .withSamples(4095)
                            .withControlVariate(true)
                            .build();

            option.setPricingEngine(engine2);

            double calculatedCV = option.NPV();
            double expectedCV = l.result();
            tolerance = 3.0e-2;
            if (Math.abs(calculatedCV - expectedCV) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        fixingDates, payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today, vol, expectedCV,
                        calculatedCV, tolerance);
            }
        }

        double[] strikes = {60.0, 80.0, 100.0, 120.0, 140.0};
        double[] prices = {42.5990, 29.3698, 18.2360, 10.0565, 4.9609};

        double v02 = 0.0175;
        double kappa2 = 1.5768;
        double theta2 = 0.0398;
        double sigma2 = 0.5751;
        double rho2 = -0.5711;

        DayCounter dc2 = new Actual365Fixed();

        SimpleQuote spot2 = new SimpleQuote(100.0);
        SimpleQuote qRate2 = new SimpleQuote(0.0);
        YieldTermStructure qTS2 = flatRate(today, qRate2, dc2);
        SimpleQuote rRate2 = new SimpleQuote(0.03);
        YieldTermStructure rTS2 = flatRate(today, rRate2, dc2);

        HestonProcess hestonProcess2 = new HestonProcess(
                new Handle<>(rTS2, true), new Handle<>(qTS2, true),
                new Handle<>(spot2, true), v02, kappa2, theta2, sigma2, rho2);

        PricingEngine engine3 = new MakeMCDiscreteArithmeticAPHestonEngine(
                hestonProcess2, RngType.LOW_DISCREPANCY)
                .withSeed(42)
                .withSteps(180)
                .withSamples(8191)
                .build();

        PricingEngine engine4 = new MakeMCDiscreteArithmeticAPHestonEngine(
                hestonProcess2, RngType.LOW_DISCREPANCY)
                .withSeed(42)
                .withSteps(180)
                .withSamples(8191)
                .withControlVariate(true)
                .build();

        List<Date> fixingDates = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            fixingDates.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        Exercise exercise = new EuropeanExercise(
                fixingDates.get(119));

        for (int i = 0; i < prices.length; i++) {
            double strike = strikes[i];
            double expected = prices[i];

            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, strike);

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(averageType, runningSum,
                            pastFixings, fixingDates, payoff, exercise);

            option.setPricingEngine(engine3);
            double calculated = option.NPV();
            double tolerance = 9.0e-2;

            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        fixingDates, payoff, exercise, spot.value(),
                        qRate2.value(), rRate2.value(), today, vol, expected,
                        calculated, tolerance);
            }

            option.setPricingEngine(engine4);
            calculated = option.NPV();
            tolerance = 3.0e-2;

            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        fixingDates, payoff, exercise, spot.value(),
                        qRate2.value(), rRate2.value(), today, vol, expected,
                        calculated, tolerance);
            }
        }
    }

    /**
     * QuantLib {@code testDiscreteGeometricAveragePriceHestonPastFixings}.
     */
    @Test
    public void testDiscreteGeometricAveragePriceHestonPastFixings() {
        Settings.instance.evaluationDate().equal(KNOWN_GOOD_EVALUATION_DATE);
        double[][][] tol = {
                {{0.04, 0.04}, {0.04, 0.04}, {0.04, 0.04},
                        {0.05, 0.04}, {0.04, 0.04}},
                {{0.04, 0.04}, {0.04, 0.04}, {0.04, 0.04},
                        {0.06, 0.06}, {0.06, 0.05}},
                {{0.04, 0.04}, {0.04, 0.04}, {0.04, 0.04},
                        {0.05, 0.04}, {0.06, 0.05}}
        };

        int[] days = {30, 90, 180, 360, 720};
        double[] strikes = {90, 100, 110};

        DayCounter dc = new Actual365Fixed();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.05);
        YieldTermStructure rTS = flatRate(today, rRate, dc);

        double v0 = 0.09;
        HestonProcess hestonProcess = new HestonProcess(
                new Handle<>(rTS, true), new Handle<>(qTS, true),
                new Handle<>(spot, true), v0, 1.15, 0.0348, 0.39, -0.64);

        PricingEngine analyticEngine =
                new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(
                        hestonProcess);

        PricingEngine mcEngine = new MakeMCDiscreteGeometricAPHestonEngine(
                hestonProcess, RngType.LOW_DISCREPANCY)
                .withSamples(8191)
                .withSeed(43)
                .build();

        Option.Type type = Option.Type.Call;
        Average.Type averageType = Average.Type.Geometric;

        for (int strikeIndex = 0; strikeIndex < strikes.length;
             strikeIndex++) {
            for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
                for (int k = 0; k < 2; k++) {
                    int futureFixings = (int) Math.floor(
                            days[dayIndex] / 30.0);
                    List<Date> fixingDates = new ArrayList<>();
                    Date expiryDate = today.add(days[dayIndex]);

                    for (int i = futureFixings - 1; i >= 0; i--) {
                        fixingDates.add(expiryDate.substract(i * 30));
                    }

                    Exercise exercise = new EuropeanExercise(expiryDate);
                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(
                            type, strikes[strikeIndex]);

                    double runningAccumulator;
                    int pastFixingsCount;
                    if (k == 0) {
                        runningAccumulator = 100.0;
                        pastFixingsCount = 1;
                    } else {
                        runningAccumulator = 95.0 * 100.0 * 105.0;
                        pastFixingsCount = 3;
                    }

                    DiscreteAveragingAsianOption option =
                            new DiscreteAveragingAsianOption(averageType,
                                    runningAccumulator, pastFixingsCount,
                                    fixingDates, payoff, exercise);

                    option.setPricingEngine(analyticEngine);
                    double analyticPrice = option.NPV();

                    option.setPricingEngine(mcEngine);
                    double mcPrice = option.NPV();

                    double tolerance = tol[strikeIndex][dayIndex][k];

                    if (Math.abs(analyticPrice - mcPrice) > tolerance) {
                        reportFailure("value", averageType,
                                runningAccumulator, pastFixingsCount,
                                new ArrayList<>(), payoff, exercise,
                                spot.value(), qRate.value(), rRate.value(),
                                today, Math.sqrt(v0), analyticPrice, mcPrice,
                                tolerance);
                    }
                }
            }
        }
    }

    private void testDiscreteGeometricAveragePriceHeston(
            PricingEngine engine, double[] tol) {
        int[] days = {30, 91, 182, 365, 730, 1095,
                30, 91, 182, 365, 730, 1095,
                30, 91, 182, 365, 730, 1095};
        double[] strikes = {90, 90, 90, 90, 90, 90,
                100, 100, 100, 100, 100, 100,
                110, 110, 110, 110, 110, 110};
        double[] prices = {10.2732, 10.9554, 11.9916, 13.6950, 16.1773,
                18.0146, 2.4389, 3.7881, 5.2132, 7.2243, 9.9948, 12.0639,
                0.1012, 0.5949, 1.4444, 2.9479, 5.3531, 7.3315};

        DayCounter dc = new Actual365Fixed();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        SimpleQuote rRate = new SimpleQuote(0.05);

        double v0 = 0.09;
        Option.Type type = Option.Type.Call;
        Average.Type averageType = Average.Type.Geometric;

        double runningAccumulator = 1.0;
        int pastFixings = 0;

        for (int i = 0; i < strikes.length; i++) {
            double strike = strikes[i];
            int day = days[i];
            double expected = prices[i];
            double tolerance = tol[i];

            int futureFixings = (int) Math.floor(day / 7.0);
            List<Date> fixingDates = new ArrayList<>();
            Date expiryDate = today.add(day);

            for (int j = futureFixings - 1; j >= 0; j--) {
                fixingDates.add(expiryDate.substract(j * 7));
            }

            Exercise exercise = new EuropeanExercise(expiryDate);
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, strike);

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(averageType,
                            runningAccumulator, pastFixings, fixingDates,
                            payoff, exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();

            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, 1.0, 0,
                        new ArrayList<>(), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today, Math.sqrt(v0),
                        expected, calculated, tolerance);
            }
        }
    }

    /**
     * QuantLib {@code testChoiAsianEngineVsMC}.
     */
    @Test
    public void testChoiAsianEngineVsMC() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(5, Month.JANUARY, 2025);
        // Java-only adaptation: QuantLib's test does not assign the global
        // evaluation date, but Instrument.isExpired() consults Settings. If
        // Settings were the real current date this option would be expired,
        // so the official today is installed here. Market data, samples,
        // seed and tolerance are unchanged.
        Settings.instance.evaluationDate().equal(today);
        Date maturity = today.add(new Period(13, TimeUnit.MONTHS));

        List<Date> fixingDates = new ArrayList<>();
        fixingDates.add(today.add(new Period(1, TimeUnit.MONTHS)));
        Date lastAllowed = maturity.substract(
                new Period(1, TimeUnit.MONTHS));
        while (TimeUtils.less(
                fixingDates.get(fixingDates.size() - 1), lastAllowed)) {
            fixingDates.add(fixingDates.get(fixingDates.size() - 1)
                    .add(new Period(1, TimeUnit.MONTHS)));
        }

        int pastFixingsCount = 2;
        double runningAccumulator = pastFixingsCount * 97.0;

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 110.0);
        Exercise exercise = new EuropeanExercise(maturity);

        DiscreteAveragingAsianOption option =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, runningAccumulator,
                        pastFixingsCount, fixingDates, payoff, exercise);

        // market data is constant in time
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(flatRate(today, 0.035, dc), true),
                new Handle<>(flatRate(today, 0.1, dc), true),
                new Handle<>(flatVol(today, 0.5, dc), true));

        option.setPricingEngine(new MakeMCDiscreteArithmeticAPEngine(
                process, RngType.LOW_DISCREPANCY)
                .withSamples(32000).withSeed(43).build());
        double expected = option.NPV();

        option.setPricingEngine(new ChoiAsianEngine(process, 20, 2 << 12));
        double calculated = option.NPV();
        checkSmall("constant market Choi vs MC",
                calculated - expected, 0.01);

        // market data is not constant in time
        process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(new ZeroCurve(
                        List.of(today,
                                today.add(new Period(3, TimeUnit.MONTHS)),
                                today.add(new Period(13, TimeUnit.MONTHS))),
                        List.of(0.1, 0.0, 0.15), dc), true),
                new Handle<>(new ZeroCurve(
                        List.of(today,
                                today.add(new Period(3, TimeUnit.MONTHS)),
                                today.add(new Period(13, TimeUnit.MONTHS))),
                        List.of(0.1, 0.2, 0.05), dc), true),
                new Handle<>(new BlackVarianceCurve(
                        today,
                        List.of(today.add(new Period(1, TimeUnit.DAYS)),
                                today.add(new Period(100, TimeUnit.DAYS)),
                                today.add(new Period(13, TimeUnit.MONTHS))),
                        List.of(0.25, 0.5, 0.4), dc, true), true));

        option.setPricingEngine(new MakeMCDiscreteArithmeticAPEngine(
                process, RngType.LOW_DISCREPANCY)
                .withSamples(32000).withSeed(43).build());
        expected = option.NPV();

        option.setPricingEngine(new ChoiAsianEngine(process, 20, 2 << 12));
        calculated = option.NPV();
        checkSmall("non-constant market Choi vs MC",
                calculated - expected, 0.01);
    }

    /**
     * QuantLib {@code testChoiAsianEngineSpecialCases}.
     */
    @Test
    public void testChoiAsianEngineSpecialCases() {
        DayCounter dc = new Actual365Fixed();
        Date today = new Date(5, Month.JANUARY, 2025);
        // Same Java-only adaptation as testChoiAsianEngineVsMC: install the
        // official evaluation date so Instrument.isExpired() is meaningful.
        Settings.instance.evaluationDate().equal(today);
        Date maturity = today.add(new Period(1, TimeUnit.YEARS));

        int pastFixingsCount = 2;
        double runningAccumulator = pastFixingsCount * 97.0;

        List<Date> fixingDates = new ArrayList<>();
        fixingDates.add(today);
        fixingDates.add(today.add(new Period(3, TimeUnit.WEEKS)));

        YieldTermStructure rTS = flatRate(today, 0.2, dc);
        YieldTermStructure qTS = flatRate(today, 0.075, dc);
        BlackVolTermStructure vTS = flatVol(today, 0.5, dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(new SimpleQuote(100.0), true),
                new Handle<>(qTS, true), new Handle<>(rTS, true),
                new Handle<>(vTS, true));

        PricingEngine choiEngine = new ChoiAsianEngine(process);

        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 103.0);
        Exercise exercise = new EuropeanExercise(maturity);

        DiscreteAveragingAsianOption asianOption =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, runningAccumulator,
                        pastFixingsCount, fixingDates, payoff, exercise);
        asianOption.setPricingEngine(choiEngine);

        double calculated = asianOption.NPV();
        Date lastFixing = fixingDates.get(fixingDates.size() - 1);
        double expected = BlackFormula.blackFormula(
                payoff.optionType(),
                payoff.strike() - (runningAccumulator + process.x0())
                        / (pastFixingsCount + 2),
                100.0 / (pastFixingsCount + 2)
                        * qTS.discount(lastFixing)
                        / rTS.discount(lastFixing),
                Math.sqrt(vTS.blackVariance(lastFixing, payoff.strike())),
                rTS.discount(maturity),
                0.0);
        double tol = 1000 * MathUtils.QL_EPSILON;
        checkSmall("today folded into past", calculated - expected, tol);

        fixingDates = new ArrayList<>();
        fixingDates.add(today);

        asianOption = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningAccumulator,
                pastFixingsCount, fixingDates, payoff, exercise);
        asianOption.setPricingEngine(choiEngine);
        calculated = asianOption.NPV();
        expected = rTS.discount(maturity) * payoff.value(
                (runningAccumulator + process.x0())
                        / (pastFixingsCount + 1));
        checkSmall("only today fixing", calculated - expected, tol);

        fixingDates = new ArrayList<>();
        asianOption = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningAccumulator,
                pastFixingsCount, fixingDates, payoff, exercise);
        asianOption.setPricingEngine(choiEngine);
        calculated = asianOption.NPV();
        expected = rTS.discount(maturity) * payoff.value(
                runningAccumulator / pastFixingsCount);
        checkSmall("no remaining fixings", calculated - expected, tol);
    }

    /**
     * QuantLib {@code testChoiAsianZeroVolatility}.
     */
    @Test
    public void testChoiAsianZeroVolatility() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.0, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new ChoiAsianEngine(process);
        PricingEngine twEngine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = List.of(today.add(30), today.add(60));
        Exercise exercise = new EuropeanExercise(today.add(60));
        double discount = rTS.discount(exercise.lastDate());
        double m = fixingDates.size();

        double expectedAverage = 0.0;
        for (Date fd : fixingDates) {
            expectedAverage += spot.value() * qTS.discount(fd)
                    / rTS.discount(fd) / m;
        }

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            for (double strike : new double[]{
                    90.0, expectedAverage, 110.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                DiscreteAveragingAsianOption option =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                option.setPricingEngine(engine);
                DiscreteAveragingAsianOption twOption =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                twOption.setPricingEngine(twEngine);

                double expected = payoff.value(expectedAverage) * discount;
                checkSmall("zero-vol Choi NPV " + type + " " + strike,
                        option.NPV() - expected, 1.0e-12);
                checkSmall("zero-vol TW NPV " + type + " " + strike,
                        twOption.NPV() - expected, 1.0e-12);
                double sensitivity = discount * expectedAverage
                        / spot.value();
                double expectedDelta;
                if (type == Option.Type.Call) {
                    expectedDelta = expectedAverage > strike
                            ? sensitivity
                            : (expectedAverage == strike
                            ? 0.5 * sensitivity : 0.0);
                } else {
                    expectedDelta = expectedAverage < strike
                            ? -sensitivity
                            : (expectedAverage == strike
                            ? -0.5 * sensitivity : 0.0);
                }
                checkSmall("zero-vol delta " + type + " " + strike,
                        option.delta() - expectedDelta, 1.0e-12);
                if (option.gamma() != 0.0) {
                    fail("zero-vol gamma is not exactly zero: "
                            + option.gamma());
                }
            }
        }

        // call-minus-put delta at the ATM state
        PlainVanillaPayoff callPayoff = new PlainVanillaPayoff(
                Option.Type.Call, expectedAverage);
        PlainVanillaPayoff putPayoff = new PlainVanillaPayoff(
                Option.Type.Put, expectedAverage);
        DiscreteAveragingAsianOption callOption =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, 0.0, 0,
                        fixingDates, callPayoff, exercise);
        callOption.setPricingEngine(engine);
        DiscreteAveragingAsianOption putOption =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, 0.0, 0,
                        fixingDates, putPayoff, exercise);
        putOption.setPricingEngine(engine);
        double parityDelta = discount * expectedAverage / spot.value();
        checkSmall("zero-vol ATM delta parity",
                callOption.delta() - putOption.delta() - parityDelta,
                1.0e-12);
    }

    /**
     * QuantLib {@code testChoiAsianTodayFixingDelta}.
     */
    @Test
    public void testChoiAsianTodayFixingDelta() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.0, dc);
        YieldTermStructure rTS = flatRate(today, 0.0, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.2, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new ChoiAsianEngine(process);

        List<Date> fixingDates = List.of(today, today.add(30));
        Exercise exercise = new EuropeanExercise(today.add(30));
        PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 40.0);
        DiscreteAveragingAsianOption option =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, 0.0, 0,
                        fixingDates, payoff, exercise);
        option.setPricingEngine(engine);

        checkSmall("today fixing NPV",
                option.NPV() - (spot.value() - 40.0), 1.0e-12);
        checkSmall("today fixing delta", option.delta() - 1.0, 1.0e-12);
    }

    /**
     * QuantLib {@code testChoiAsianMixedZeroVariance}.
     */
    @Test
    public void testChoiAsianMixedZeroVariance() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVarianceCurve volTS = new BlackVarianceCurve(
                today, List.of(today.add(30), today.add(60)),
                List.of(0.0, 0.25), dc, true);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new ChoiAsianEngine(process);
        PricingEngine twEngine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = List.of(today.add(30), today.add(60));
        Exercise exercise = new EuropeanExercise(today.add(60));
        double discount = rTS.discount(exercise.lastDate());
        double m = fixingDates.size();

        double forward1 = spot.value() * qTS.discount(fixingDates.get(0))
                / rTS.discount(fixingDates.get(0));
        double forward2 = spot.value() * qTS.discount(fixingDates.get(1))
                / rTS.discount(fixingDates.get(1));
        double variance2 = volTS.blackVariance(fixingDates.get(1), 100.0);

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            for (double strike : new double[]{45.0, 90.0, 110.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                DiscreteAveragingAsianOption option =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                option.setPricingEngine(engine);
                DiscreteAveragingAsianOption twOption =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                twOption.setPricingEngine(twEngine);

                double residualStrike = strike - forward1 / m;
                double expected = BlackFormula.blackFormula(
                        type, Math.max(residualStrike, 0.0),
                        forward2 / m, Math.sqrt(variance2), discount, 0.0);
                if (residualStrike <= 0.0 && type == Option.Type.Call) {
                    expected = discount
                            * ((forward1 + forward2) / m - strike);
                    double expectedDelta = discount
                            * (forward1 + forward2) / m / spot.value();
                    checkSmall("mixed zero variance delta "
                                    + type + " " + strike,
                            option.delta() - expectedDelta, 1.0e-12);
                }
                checkSmall("mixed zero variance NPV "
                                + type + " " + strike,
                        option.NPV() - expected, 1.0e-12);
                checkSmall("mixed zero variance TW NPV "
                                + type + " " + strike,
                        twOption.NPV() - expected, 0.05);
            }
        }
    }

    /**
     * QuantLib {@code testChoiAsianMixedZeroVarianceMultipleStochastic}.
     */
    @Test
    public void testChoiAsianMixedZeroVarianceMultipleStochastic() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVarianceCurve volTS = new BlackVarianceCurve(
                today,
                List.of(today.add(30), today.add(60), today.add(90)),
                List.of(0.0, 0.20, 0.25), dc, true);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new ChoiAsianEngine(process);
        PricingEngine twEngine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = List.of(
                today.add(30), today.add(60), today.add(90));
        Exercise exercise = new EuropeanExercise(today.add(90));

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            for (double strike : new double[]{45.0, 90.0, 110.0}) {
                PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(type, strike);
                DiscreteAveragingAsianOption option =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                option.setPricingEngine(engine);
                DiscreteAveragingAsianOption twOption =
                        new DiscreteAveragingAsianOption(
                                Average.Type.Arithmetic, 0.0, 0,
                                fixingDates, payoff, exercise);
                twOption.setPricingEngine(twEngine);

                checkSmall("multiple stochastic Choi vs TW "
                                + type + " " + strike,
                        option.NPV() - twOption.NPV(), 0.05);
            }
        }
    }

    /**
     * QuantLib {@code testChoiAsianNegativeEffectiveStrike}.
     */
    @Test
    public void testChoiAsianNegativeEffectiveStrike() {
        Date today = Settings.instance.evaluationDate().Date();
        DayCounter dc = new Actual365Fixed();
        SimpleQuote spot = new SimpleQuote(100.0);
        YieldTermStructure qTS = flatRate(today, 0.01, dc);
        YieldTermStructure rTS = flatRate(today, 0.03, dc);
        BlackVolTermStructure volTS = flatVol(today, 0.25, dc);
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spot, true), new Handle<>(qTS, true),
                new Handle<>(rTS, true), new Handle<>(volTS, true));
        PricingEngine engine = new ChoiAsianEngine(process);
        PricingEngine twEngine = new TurnbullWakemanAsianEngine(process);

        List<Date> fixingDates = List.of(today.add(30), today.add(60));
        Exercise exercise = new EuropeanExercise(today.add(60));
        double discount = rTS.discount(exercise.lastDate());
        double m = 1 + fixingDates.size();

        double expectedAverage = 200.0 / m;
        for (Date fd : fixingDates) {
            expectedAverage += spot.value() * qTS.discount(fd)
                    / rTS.discount(fd) / m;
        }

        for (Option.Type type : List.of(Option.Type.Call, Option.Type.Put)) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(type, 50.0);
            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(
                            Average.Type.Arithmetic, 200.0, 1,
                            fixingDates, payoff, exercise);
            option.setPricingEngine(engine);
            DiscreteAveragingAsianOption twOption =
                    new DiscreteAveragingAsianOption(
                            Average.Type.Arithmetic, 200.0, 1,
                            fixingDates, payoff, exercise);
            twOption.setPricingEngine(twEngine);

            double expected = payoff.value(expectedAverage) * discount;
            checkSmall("negative effective strike Choi NPV " + type,
                    option.NPV() - expected, 1.0e-12);
            checkSmall("negative effective strike TW NPV " + type,
                    twOption.NPV() - expected, 1.0e-12);
            double expectedDelta = 0.0;
            if (type == Option.Type.Call) {
                expectedDelta = discount
                        * (expectedAverage - 200.0 / m) / spot.value();
            }
            checkSmall("negative effective strike delta " + type,
                    option.delta() - expectedDelta, 1.0e-12);
            if (option.gamma() != 0.0) {
                fail("negative effective strike gamma is not exactly zero: "
                        + option.gamma());
            }
        }
    }

    /**
     * QuantLib {@code testMCDiscreteArithmeticAveragePrice}.
     *
     * <p>Data from "Asian Option", Levy, 1997, in "Exotic Options: The
     * State of the Art", edited by Clewlow, Strickland.
     */
    @Test
    public void testMCDiscreteArithmeticAveragePrice() {
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

        PricingEngine fdEngine =
                new FdBlackScholesAsianEngine(stochProcess, 100, 100, 100);
        PricingEngine twEngine =
                new TurnbullWakemanAsianEngine(stochProcess);
        PricingEngine choiEngine =
                new ChoiAsianEngine(stochProcess, 10, 2 << 12);

        Average.Type averageType = Average.Type.Arithmetic;
        double runningSum = 0.0;
        int pastFixings = 0;
        for (DiscreteAverageData l : cases4) {
            PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(l.type, l.strike);

            double dt = l.length / (l.fixings - 1);
            double[] timeIncrements = new double[l.fixings];
            Date[] fixingDates = new Date[l.fixings];
            timeIncrements[0] = l.first;
            fixingDates[0] = today.add(timeToDays(timeIncrements[0]));
            for (int i = 1; i < l.fixings; i++) {
                timeIncrements[i] = i * dt + l.first;
                fixingDates[i] = today.add(timeToDays(timeIncrements[i]));
            }
            Exercise exercise =
                    new EuropeanExercise(fixingDates[l.fixings - 1]);

            spot.setValue(l.underlying);
            qRate.setValue(l.dividendYield);
            rRate.setValue(l.riskFreeRate);
            vol.setValue(l.volatility);

            PricingEngine engine = new MakeMCDiscreteArithmeticAPEngine(
                    stochProcess, RngType.LOW_DISCREPANCY)
                    .withSamples(2047)
                    .withControlVariate(l.controlVariate)
                    .build();

            DiscreteAveragingAsianOption option =
                    new DiscreteAveragingAsianOption(
                            averageType, runningSum, pastFixings,
                            List.of(fixingDates), payoff, exercise);
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double expected = l.result;
            double tolerance = 2.0e-2;
            if (Math.abs(calculated - expected) > tolerance) {
                reportFailure("value", averageType, runningSum, pastFixings,
                        List.of(fixingDates), payoff, exercise, spot.value(),
                        qRate.value(), rRate.value(), today, vol.value(),
                        expected, calculated, tolerance);
            }

            if (l.fixings < 100) {
                option.setPricingEngine(fdEngine);
                calculated = option.NPV();
                if (Math.abs(calculated - expected) > tolerance) {
                    fail("The consistency check of the PDE engine failed\n"
                            + failureMessage("value", averageType, runningSum,
                            pastFixings, List.of(fixingDates), payoff,
                            exercise, spot.value(), qRate.value(),
                            rRate.value(), today, vol.value(), expected,
                            calculated, tolerance));
                }
            }

            option.setPricingEngine(twEngine);
            calculated = option.NPV();
            tolerance = 3.0e-2;
            if (Math.abs(calculated - expected) > tolerance) {
                fail("The consistency check of the analytic approximation "
                        + "engine failed\n"
                        + failureMessage("value", averageType, runningSum,
                        pastFixings, List.of(fixingDates), payoff,
                        exercise, spot.value(), qRate.value(),
                        rRate.value(), today, vol.value(), expected,
                        calculated, tolerance));
            }

            // Official test reuses the current `tolerance` after the TW
            // assignment (3.0e-2); it is not reset to 2.0e-2.
            if (l.fixings < 100) {
                option.setPricingEngine(choiEngine);
                calculated = option.NPV();
                if (Math.abs(calculated - expected) > tolerance) {
                    fail("The consistency check of the Choi Asian engine "
                            + "failed\n"
                            + failureMessage("value", averageType,
                            runningSum, pastFixings, List.of(fixingDates),
                            payoff, exercise, spot.value(), qRate.value(),
                            rRate.value(), today, vol.value(), expected,
                            calculated, tolerance));
                }
            }
        }
    }

    /**
     * QuantLib {@code testAllFixingsInThePast}.
     */
    @Test
    public void testAllFixingsInThePast() {
        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.005);
        Handle<YieldTermStructure> qTS = flatRate(qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.01);
        Handle<YieldTermStructure> rTS = flatRate(rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.20);
        Handle<BlackVolTermStructure> volTS = flatVol(vol, dc);

        BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<>(spot, true), qTS, rTS, volTS);

        Date exerciseDate = today.add(14);
        Date startDate = exerciseDate.substract(
                new Period(1, TimeUnit.YEARS));
        List<Date> fixingDates = new ArrayList<>();
        for (int i = 0; i < 12; ++i) {
            fixingDates.add(startDate.add(
                    new Period(i, TimeUnit.MONTHS)));
        }
        int pastFixings = 12;

        PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Put, 100.0);
        Exercise exercise = new EuropeanExercise(exerciseDate);

        // MC arithmetic average-price
        double runningSum = pastFixings * spot.value();
        DiscreteAveragingAsianOption option1 = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningSum, pastFixings,
                fixingDates, payoff, exercise);
        option1.setPricingEngine(new MakeMCDiscreteArithmeticAPEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047).build());

        // MC arithmetic average-strike
        DiscreteAveragingAsianOption option2 = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningSum, pastFixings,
                fixingDates, payoff, exercise);
        option2.setPricingEngine(new MakeMCDiscreteArithmeticASEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047).build());

        // MC geometric average-price
        double runningProduct = Math.pow(spot.value(), pastFixings);
        DiscreteAveragingAsianOption option3 = new DiscreteAveragingAsianOption(
                Average.Type.Geometric, runningProduct, pastFixings,
                fixingDates, payoff, exercise);
        option3.setPricingEngine(new MakeMCDiscreteGeometricAPEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047).build());

        DiscreteAveragingAsianOption option4 =
                new DiscreteAveragingAsianOption(
                        Average.Type.Arithmetic, runningSum, pastFixings,
                        fixingDates, payoff, exercise);
        option4.setPricingEngine(new ChoiAsianEngine(stochProcess));

        List<DiscreteAveragingAsianOption> options =
                List.of(option1, option2, option3);
        for (DiscreteAveragingAsianOption option : options) {
            boolean raised = false;
            try {
                option.NPV();
            } catch (PastFixingsOnly e) {
                raised = true;
            }
            if (!raised) {
                fail("exception expected");
            }
        }

        boolean choiRaised = false;
        try {
            option4.NPV();
        } catch (IllegalArgumentException e) {
            choiRaised = true;
        }
        if (!choiRaised) {
            fail("exception expected");
        }

        // also check with the evaluation date on last fixing
        Settings.instance.evaluationDate().equal(
                fixingDates.get(fixingDates.size() - 1));

        for (DiscreteAveragingAsianOption option : options) {
            boolean raised = false;
            try {
                option.NPV();
            } catch (PastFixingsOnly e) {
                raised = true;
            }
            if (!raised) {
                fail("exception expected");
            }
        }

        choiRaised = false;
        try {
            option4.NPV();
        } catch (IllegalArgumentException e) {
            choiRaised = true;
        }
        if (!choiRaised) {
            fail("exception expected");
        }
    }

    /**
     * QuantLib {@code testPastFixings}, complete: MC arithmetic
     * average-price (including the vector-fixings interface), MC arithmetic
     * average-strike, analytic geometric average-price and MC geometric
     * average-price.
     */
    @Test
    public void testPastFixings() {
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

        // MC arithmetic average-price

        double runningSum = 0.0;
        int pastFixings = 0;
        List<Date> fixingDates1 = new ArrayList<>();
        for (int i = 0; i <= 12; ++i) {
            fixingDates1.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        DiscreteAveragingAsianOption option1 = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningSum, pastFixings,
                fixingDates1, payoff, exercise);

        pastFixings = 2;
        runningSum = pastFixings * spot.value() * 0.8;
        List<Date> fixingDates2 = new ArrayList<>();
        for (int i = -2; i <= 12; ++i) {
            fixingDates2.add(today.add(new Period(i, TimeUnit.MONTHS)));
        }

        DiscreteAveragingAsianOption option2 = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, runningSum, pastFixings,
                fixingDates2, payoff, exercise);

        PricingEngine engine = new MakeMCDiscreteArithmeticAPEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047)
                .build();

        option1.setPricingEngine(engine);
        option2.setPricingEngine(engine);

        double price1 = option1.NPV();
        double price2 = option2.NPV();

        if (MathUtils.close(price1, price2)) {
            fail("past fixings had no effect on arithmetic average-price option"
                    + "\n  without fixings: " + price1
                    + "\n  with fixings:    " + price2);
        }

        // Test past-fixings-as-a-vector interface

        List<Double> allPastFixings = List.of(spot.value() * 0.8,
                spot.value() * 0.8);

        DiscreteAveragingAsianOption option1a = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, fixingDates1, payoff, exercise);

        DiscreteAveragingAsianOption option2a = new DiscreteAveragingAsianOption(
                Average.Type.Arithmetic, fixingDates2, payoff, exercise,
                allPastFixings);

        option1a.setPricingEngine(engine);
        option2a.setPricingEngine(engine);

        double price1a = option1a.NPV();
        double price2a = option2a.NPV();

        if (Math.abs(price1 - price1a) > 1e-8) {
            fail("Unseasoned option prices do not match in old and new interface"
                    + "\n  Old Interface: " + price1
                    + "\n  New Interface: " + price1a);
        }

        if (Math.abs(price2 - price2a) > 1e-8) {
            fail("Seasoned option prices do not match in old and new interface"
                    + "\n  Old Interface: " + price2
                    + "\n  New Interface: " + price2a);
        }

        // MC arithmetic average-strike

        engine = new MakeMCDiscreteArithmeticASEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047)
                .build();

        option1.setPricingEngine(engine);
        option2.setPricingEngine(engine);

        price1 = option1.NPV();
        price2 = option2.NPV();

        if (MathUtils.close(price1, price2)) {
            fail("past fixings had no effect on arithmetic average-strike option"
                    + "\n  without fixings: " + price1
                    + "\n  with fixings:    " + price2);
        }

        // analytic geometric average-price

        double runningProduct = 1.0;
        pastFixings = 0;

        DiscreteAveragingAsianOption option3 = new DiscreteAveragingAsianOption(
                Average.Type.Geometric, runningProduct, pastFixings,
                fixingDates1, payoff, exercise);

        pastFixings = 2;
        runningProduct = spot.value() * spot.value();

        DiscreteAveragingAsianOption option4 = new DiscreteAveragingAsianOption(
                Average.Type.Geometric, runningProduct, pastFixings,
                fixingDates2, payoff, exercise);

        engine = new AnalyticDiscreteGeometricAveragePriceAsianEngine(
                stochProcess);

        option3.setPricingEngine(engine);
        option4.setPricingEngine(engine);

        double price3 = option3.NPV();
        double price4 = option4.NPV();

        if (MathUtils.close(price3, price4)) {
            fail("past fixings had no effect on geometric average-price option"
                    + "\n  without fixings: " + price3
                    + "\n  with fixings:    " + price4);
        }

        // MC geometric average-price

        engine = new MakeMCDiscreteGeometricAPEngine(
                stochProcess, RngType.LOW_DISCREPANCY)
                .withSamples(2047)
                .build();

        option3.setPricingEngine(engine);
        option4.setPricingEngine(engine);

        price3 = option3.NPV();
        price4 = option4.NPV();

        if (MathUtils.close(price3, price4)) {
            fail("past fixings had no effect on geometric average-price option"
                    + "\n  without fixings: " + price3
                    + "\n  with fixings:    " + price4);
        }
    }

    /** QuantLib {@code timeToDays}: round a time in years to days. */
    private static int timeToDays(double t) {
        return (int) Math.round(t * 360.0);
    }

    /**
     * Integrate the exact Levy variance and compare it with the engine price
     * of the corresponding at-the-money call.
     */
    private static void checkLevyVariancePrice(
            Date today,
            SimpleQuote q,
            SimpleQuote vol,
            PricingEngine engine,
            GaussLegendreIntegration integrate,
            int remaining,
            int elapsed,
            double b,
            double sigma) {
        double tau = remaining / 360.0;
        double t = tau + elapsed / 360.0;
        double x = b * tau;
        double w = sigma * sigma * tau;
        double meanFactor = 100.0 * tau / t;
        q.setValue(0.05 - b);
        vol.setValue(sigma);
        double mean = meanFactor / 2.0 * integrate.value(
                u -> Math.exp(x * (u + 1.0) / 2.0));
        double strike = (t - tau) / t * 97.0 + mean;
        ContinuousAveragingAsianOption option =
                new ContinuousAveragingAsianOption(
                        Average.Type.Arithmetic, today.substract(elapsed),
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        new EuropeanExercise(today.add(remaining)));
        option.setPricingEngine(engine);

        // Integrate the covariance over the normalized triangle
        // 0 <= s <= t <= 1.
        double variance = 2.0 * meanFactor * meanFactor
                * integrate.value(u -> {
                    double tt = (u + 1.0) / 2.0;
                    return tt / 4.0 * integrate.value(v -> {
                        double s = tt * (v + 1.0) / 2.0;
                        return Math.exp(x * (s + tt)) * Math.expm1(w * s);
                    });
                });
        double logVariance = Math.log1p(variance / (mean * mean));
        double expected = BlackFormula.blackFormula(
                Option.Type.Call, mean, mean, Math.sqrt(logVariance),
                Math.exp(-0.05 * tau), 0.0);

        double calculated = option.NPV();
        if (!Double.isFinite(calculated)) {
            fail("remaining=" + remaining + ", elapsed=" + elapsed
                    + ", carry=" + b + ", vol=" + sigma
                    + ": calculated is not finite");
        }
        // Allow for quadrature and pricing roundoff across platforms.
        checkSmall("remaining=" + remaining + ", elapsed=" + elapsed
                + ", carry=" + b + ", vol=" + sigma,
                calculated - expected, 2.0e-11);
    }

    /** QuantLib {@code DiscreteAverageDataTermStructure} for the TW tests. */
    private record DiscreteAverageDataTermStructure(
            Option.Type type,
            double underlying,
            double strike,
            double b,
            double riskFreeRate,
            double first,
            double expiry,
            int fixings,
            double volatility,
            String slope,
            double result) {
    }

    /** Data from Haug, "Option Pricing Formulas", Table 4-28, p.201. */
    private static final List<DiscreteAverageDataTermStructure> casesTW =
            List.of(
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 19.5152),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 19.5063),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 19.5885),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 0.0090),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 0.0001),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 80.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 0.0823),

                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 10.1437),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 9.8313),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 10.7062),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 0.3906),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 0.0782),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 90.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 0.9531),

                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 3.2700),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 2.2819),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 4.3370),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 3.2700),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 2.2819),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 100.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 4.3370),

                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 0.5515),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 0.1314),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 1.2429),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 10.3046),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 9.8845),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 110.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 10.9960),

                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 0.0479),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 0.0016),
                    new DiscreteAverageDataTermStructure(Option.Type.Call,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 0.2547),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "flat", 19.5541),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "up", 19.5078),
                    new DiscreteAverageDataTermStructure(Option.Type.Put,
                            100.0, 120.0, 0.0, 0.05, 1.0 / 52.0, 0.5,
                            26, 0.2, "down", 19.7609)
            );

    /** QuantLib {@code VecerData}. */
    private record VecerData(
            double spot,
            double riskFreeRate,
            double volatility,
            double strike,
            int length,
            double result,
            double tolerance) {
    }

    private static final List<VecerData> casesVecer = List.of(
            new VecerData(1.9, 0.05, 0.5, 2.0, 1, 0.193174, 1.0e-5),
            new VecerData(2.0, 0.05, 0.5, 2.0, 1, 0.246416, 1.0e-5),
            new VecerData(2.1, 0.05, 0.5, 2.0, 1, 0.306220, 1.0e-4),
            new VecerData(2.0, 0.02, 0.1, 2.0, 1, 0.055986, 2.0e-4),
            new VecerData(2.0, 0.18, 0.3, 2.0, 1, 0.218388, 1.0e-4),
            new VecerData(2.0, 0.0125, 0.25, 2.0, 2, 0.172269, 1.0e-4),
            new VecerData(2.0, 0.05, 0.5, 2.0, 2, 0.350095, 2.0e-4)
    );

    /** QuantLib {@code ContinuousAverageData} for the Levy test. */
    private record ContinuousAverageData(
            Option.Type type,
            double spot,
            double currentAverage,
            double strike,
            double dividendYield,
            double riskFreeRate,
            double volatility,
            int length,
            int elapsed,
            double result) {
    }

    /** Data from Haug, "Option Pricing Formulas", p.99-100. */
    private static final List<ContinuousAverageData> casesLevy = List.of(
            new ContinuousAverageData(Option.Type.Call, 6.80, 6.80, 6.90,
                    0.09, 0.07, 0.14, 180, 0, 0.0944),
            new ContinuousAverageData(Option.Type.Put, 6.80, 6.80, 6.90,
                    0.09, 0.07, 0.14, 180, 0, 0.2237),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.15, 270, 0, 7.0544),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.15, 270, 90, 5.6731),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.15, 270, 180, 5.0806),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.35, 270, 0, 10.1213),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.35, 270, 90, 6.9705),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 95.0,
                    0.05, 0.1, 0.35, 270, 180, 5.1411),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.15, 270, 0, 3.7845),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.15, 270, 90, 1.9964),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.15, 270, 180, 0.6722),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.35, 270, 0, 7.5038),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.35, 270, 90, 4.0687),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 100.0,
                    0.05, 0.1, 0.35, 270, 180, 1.4222),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.15, 270, 0, 1.6729),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.15, 270, 90, 0.3565),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.15, 270, 180, 0.0004),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.35, 270, 0, 5.4071),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.35, 270, 90, 2.1359),
            new ContinuousAverageData(Option.Type.Call, 100.0, 100.0, 105.0,
                    0.05, 0.1, 0.35, 270, 180, 0.1552)
    );

    /** QuantLib {@code DiscreteAverageData} from the Asian test suite. */
    private record DiscreteAverageData(
            Option.Type type,
            double underlying,
            double strike,
            double dividendYield,
            double riskFreeRate,
            double first,
            double length,
            int fixings,
            double volatility,
            boolean controlVariate,
            double result) {
    }

    /** Heston discrete arithmetic average-price data set. */
    private static final List<DiscreteAverageData> casesHestonArithmetic =
            List.of(
                    new DiscreteAverageData(Option.Type.Call, 120.0, 100.0,
                            0.0, 0.05, 1.0 / 12.0, 11.0 / 12.0, 12, 0.1,
                            false, 22.50));

    /** Data from "Asian Option", Levy, 1997 (arithmetic average-price). */
    private static final List<DiscreteAverageData> cases4 = List.of(
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 2, 0.13, true, 1.3942835683),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 4, 0.13, true, 1.5852442983),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 8, 0.13, true, 1.66970673),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 12, 0.13, true, 1.6980019214),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 26, 0.13, true, 1.7255070456),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 52, 0.13, true, 1.7401553533),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 100, 0.13, true, 1.7478303712),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 250, 0.13, true, 1.7490291943),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 500, 0.13, true, 1.7515113291),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 1000, 0.13, true, 1.7537344885),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 2, 0.13, true, 1.8496053697),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 4, 0.13, true, 2.0111495205),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 8, 0.13, true, 2.0852138818),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 12, 0.13, true, 2.1105094397),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 26, 0.13, true, 2.1346526695),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 52, 0.13, true, 2.147489651),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 100, 0.13, true, 2.154728109),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 250, 0.13, true, 2.1564276565),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 500, 0.13, true, 2.1594238588),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 1000, 0.13, true, 2.1595367326),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 2, 0.13, true, 2.63315092584),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 4, 0.13, true, 2.76723962361),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 8, 0.13, true, 2.83124836881),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 12, 0.13, true, 2.84290301412),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 26, 0.13, true, 2.88179560417),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 52, 0.13, true, 2.88447044543),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 100, 0.13, true, 2.89985329603),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 250, 0.13, true, 2.90047296063),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 500, 0.13, true, 2.89813412160),
            new DiscreteAverageData(Option.Type.Put, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 1000, 0.13, true, 2.89703362437)
    );

    /** Data from "Asian Option", Levy, 1997 (arithmetic average-strike). */
    private static final List<DiscreteAverageData> cases5 = List.of(
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 2, 0.13, true, 1.51917595129),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 4, 0.13, true, 1.67940165674),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 8, 0.13, true, 1.75371215251),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 12, 0.13, true, 1.77595318693),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 26, 0.13, true, 1.81430536630),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 52, 0.13, true, 1.82269246898),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 100, 0.13, true, 1.83822402464),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 250, 0.13, true, 1.83875059026),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 500, 0.13, true, 1.83750703638),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    0.0, 11.0 / 12.0, 1000, 0.13, true, 1.83887181884),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 2, 0.13, true, 1.51154400089),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 4, 0.13, true, 1.67103508506),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 8, 0.13, true, 1.74529684070),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 12, 0.13, true, 1.76667074564),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 26, 0.13, true, 1.80528400613),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 52, 0.13, true, 1.81400883891),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 100, 0.13, true, 1.82922901451),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 250, 0.13, true, 1.82937111773),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 500, 0.13, true, 1.82826193186),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    1.0 / 12.0, 11.0 / 12.0, 1000, 0.13, true, 1.82967846654),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 2, 0.13, true, 1.49648170891),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 4, 0.13, true, 1.65443100462),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 8, 0.13, true, 1.72817806731),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 12, 0.13, true, 1.74877367895),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 26, 0.13, true, 1.78733801988),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 52, 0.13, true, 1.79624826757),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 100, 0.13, true, 1.81114186876),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 250, 0.13, true, 1.81101152587),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 500, 0.13, true, 1.81002311939),
            new DiscreteAverageData(Option.Type.Call, 90.0, 87.0, 0.06, 0.025,
                    3.0 / 12.0, 11.0 / 12.0, 1000, 0.13, true, 1.81145760308)
    );

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
        fail(failureMessage(greekName, averageType, runningAccumulator,
                pastFixings, fixingDates, payoff, exercise, s, q, r, today, v,
                expected, calculated, tolerance));
    }

    private static String failureMessage(String greekName,
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
        return exercise.type() + " Asian option with "
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
                + "    tolerance:        " + tolerance;
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
