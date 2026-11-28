package pricingengines;

import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Payoff;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.TimeGrid;
import jquant.indexes.IndexManager;
import jquant.instruments.ImpliedVolatilityHelper;
import jquant.math.Array;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.impl.ConstraintImpl;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.PseudoRandom;
import jquant.math.randomnumbers.SobolBrownianBridgeRsg;
import jquant.math.statistics.GeneralStatistics;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.SampleMultiPath;
import jquant.models.BlackCalibrationHelper;
import jquant.models.CalibrationHelper;
import jquant.models.equity.HestonModel;
import jquant.models.equity.HestonModelHelper;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.patterns.Observer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.AnalyticBSMHullWhiteEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.AnalyticH1HWEngine;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.pricingengines.vanilla.AnalyticHestonHullWhiteEngine;
import jquant.pricingengines.vanilla.FdHestonHullWhiteVanillaEngine;
import jquant.pricingengines.vanilla.FdHestonVanillaEngine;
import jquant.pricingengines.vanilla.MakeMCHestonHullWhiteEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.processes.HestonProcess;
import jquant.processes.HullWhiteForwardProcess;
import jquant.processes.HullWhiteProcess;
import jquant.processes.HybridHestonHullWhiteProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.Compounding.Continuous;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.time.Frequency.ANNUAL;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/hybridhestonhullwhiteprocess.cpp} tests
 * (suite {@code HybridHestonHullWhiteProcessTests}).
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end. Official
 * {@code BOOST_FAIL} aborts immediately.
 */
public class HybridHestonHullWhiteProcessTest {

    private SavedSettings savedSettings;
    private Set<Observer> evalObserversAtStart;

    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        // Snapshot process-wide evaluation-date observers so AfterEach can
        // unregister only the ones this case created. Official C++ destroys
        // test-function locals (Observer dtors unregister) at case end.
        evalObserversAtStart = snapshotEvalDateObservers();
    }

    @AfterEach
    void restoreSettings() {
        // Official BOOST fixture order: locals are destroyed first, then
        // SavedSettings restores the evaluation date. clearHistories() plus
        // restore() do not detach IndexManager / Settings notifiers.
        // Only copy the observers this case added: a long Surefire JVM can
        // already hold a large leftover set from earlier classes, and
        // new ArrayList<>(entireSet) was the fresh2 OOM site.
        final Set<Observer> added = snapshotEvalDateObservers();
        if (evalObserversAtStart != null) {
            added.removeAll(evalObserversAtStart);
        }
        for (Observer o : added) {
            o.unregisterWithAll();
        }
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    @SuppressWarnings("unchecked")
    private static Set<Observer> snapshotEvalDateObservers() {
        final Object observable = Settings.instance.evaluationDate();
        Class<?> c = observable.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField("observers");
                f.setAccessible(true);
                final Object v = f.get(observable);
                if (v instanceof Set) {
                    return new HashSet<>((Set<Observer>) v);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            c = c.getSuperclass();
        }
        return Set.of();
    }

    private void checkBoostErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    // ------------------------------------------------------------------
    // Official test-local data types and helpers
    // ------------------------------------------------------------------

    private static class HestonModelData {
        final String name;
        final double v0, kappa, theta, sigma, rho, r, q;

        HestonModelData(String name, double v0, double kappa, double theta,
                        double sigma, double rho, double r, double q) {
            this.name = name;
            this.v0 = v0;
            this.kappa = kappa;
            this.theta = theta;
            this.sigma = sigma;
            this.rho = rho;
            this.r = r;
            this.q = q;
        }
    }

    private static final HestonModelData[] hestonModels = {
            // ADI finite difference schemes for option pricing in the
            // Heston model with correlation, K.J. in t'Hout and S. Foulon,
            new HestonModelData("'t Hout case 1", 0.04, 1.5, 0.04, 0.3, -0.9, 0.025, 0.0),
            new HestonModelData("'t Hout case 2", 0.12, 3.0, 0.12, 0.04, 0.6, 0.01, 0.04),
            new HestonModelData("'t Hout case 3", 0.0707, 0.6067, 0.0707, 0.2928, -0.7571, 0.03, 0.0),
            new HestonModelData("'t Hout case 4", 0.06, 2.5, 0.06, 0.5, -0.1, 0.0507, 0.0469),
            // Efficient numerical methods for pricing American options under
            // stochastic volatility, Samuli Ikonen and Jari Toivanen,
            new HestonModelData("Ikonen-Toivanen", 0.0625, 5, 0.16, 0.9, 0.1, 0.1, 0.0),
            // Not-so-complex logarithms in the Heston model,
            // Christian Kahl and Peter Jaeckel
            new HestonModelData("Kahl-Jaeckel", 0.16, 1.0, 0.16, 2.0, -0.8, 0.0, 0.0),
            // self defined test cases
            new HestonModelData("Equity case", 0.07, 2.0, 0.04, 0.55, -0.8, 0.03, 0.035),
            new HestonModelData("high correlation", 0.07, 1.0, 0.04, 0.55, 0.995, 0.02, 0.04),
            new HestonModelData("low Vol-Of-Vol", 0.07, 1.0, 0.04, 0.001, -0.75, 0.04, 0.03),
            new HestonModelData("kappaEqSigRho", 0.07, 0.4, 0.04, 0.5, 0.8, 0.03, 0.03)
    };

    private static class HullWhiteModelData {
        final String name;
        final double a, sigma;

        HullWhiteModelData(String name, double a, double sigma) {
            this.name = name;
            this.a = a;
            this.sigma = sigma;
        }
    }

    private static final HullWhiteModelData[] hullWhiteModels = {
            new HullWhiteModelData("EUR-2003", 0.00883, 0.00631)
    };

    private static class SchemeData {
        final String name;
        final FdmSchemeDesc schemeDesc;

        SchemeData(String name, FdmSchemeDesc schemeDesc) {
            this.name = name;
            this.schemeDesc = schemeDesc;
        }
    }

    private static final SchemeData[] schemes = {
            new SchemeData("HV2", FdmSchemeDesc.Hundsdorfer()),
            new SchemeData("HV1", FdmSchemeDesc.ModifiedHundsdorfer()),
            new SchemeData("CS", FdmSchemeDesc.CraigSneyd()),
            new SchemeData("MCS", FdmSchemeDesc.ModifiedCraigSneyd()),
            new SchemeData("DS", FdmSchemeDesc.Douglas())
    };

    private static class VanillaOptionData {
        final double strike;
        final double maturity;
        final Option.Type optionType;

        VanillaOptionData(double strike, double maturity, Option.Type optionType) {
            this.strike = strike;
            this.maturity = maturity;
            this.optionType = optionType;
        }
    }

    private static HestonProcess makeHestonProcess(HestonModelData params) {
        Handle<Quote> spot = new Handle<>(new SimpleQuote(100), true);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(params.r, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(params.q, dayCounter), true);

        return new HestonProcess(rTS, qTS, spot, params.v0, params.kappa,
                params.theta, params.sigma, params.rho);
    }

    private static EuropeanOption makeVanillaOption(VanillaOptionData params) {
        Date maturity = Settings.instance.evaluationDate().Date()
                .add(new Period((int) (params.maturity * 365), TimeUnit.DAYS));
        Exercise exercise = new EuropeanExercise(maturity);
        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(params.optionType, params.strike);

        return new EuropeanOption(payoff, exercise);
    }

    /**
     * Official {@code flatRate(Rate, DayCounter)}: the reference date of a
     * {@code FlatForward} built with the null date resolves to the evaluation
     * date.
     */
    private static YieldTermStructure flatRate(double forward, DayCounter dc) {
        Date today = Settings.instance.evaluationDate().Date();
        return flatRate(today, forward, dc);
    }

    /** Official {@code flatRate(const Date&, Rate, const DayCounter&)}. */
    private static YieldTermStructure flatRate(Date today, double forward,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(new SimpleQuote(forward), true),
                dc, Continuous, ANNUAL);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote forward,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(forward, true), dc,
                Continuous, ANNUAL);
    }

    /** Official {@code flatVol(const Date&, Volatility, const DayCounter&)}. */
    private static BlackVolTermStructure flatVol(Date today, double volatility,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(new SimpleQuote(volatility), true), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }

    // ------------------------------------------------------------------
    // Official test cases
    // ------------------------------------------------------------------

    @Test
    public void testBsmHullWhiteEngine() {
        System.out.println("Testing European option pricing for a BSM process"
                + " with one-factor Hull-White model...");

        DayCounter dc = new Actual365Fixed();

        Date today = Date.todaysDate();
        Date maturity = today.add(new Period(20, TimeUnit.YEARS));

        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);
        SimpleQuote qRate = new SimpleQuote(0.04);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, qRate, dc), true);
        SimpleQuote rRate = new SimpleQuote(0.0525);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, rRate, dc), true);
        SimpleQuote vol = new SimpleQuote(0.25);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        HullWhite hullWhiteModel = new HullWhite(rTS, 0.00883, 0.00526);

        BlackScholesMertonProcess stochProcess =
                new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        Exercise exercise = new EuropeanExercise(maturity);

        double fwd = spot.currentLink().value()
                * qTS.currentLink().discount(maturity)
                / rTS.currentLink().discount(maturity);
        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, fwd);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        double tol = 1e-8;
        double[] corr = {-0.75, -0.25, 0.0, 0.25, 0.75};
        double[] expectedVol = {0.217064577, 0.243995801,
                0.256402830, 0.268236596, 0.290461343};

        for (int i = 0; i < corr.length; ++i) {
            PricingEngine bsmhwEngine = new AnalyticBSMHullWhiteEngine(
                    corr[i], stochProcess, hullWhiteModel);

            option.setPricingEngine(bsmhwEngine);
            double npv = option.NPV();

            Handle<BlackVolTermStructure> compVolTS =
                    new Handle<>(flatVol(today, expectedVol[i], dc), true);

            BlackScholesMertonProcess bsProcess =
                    new BlackScholesMertonProcess(spot, qTS, rTS, compVolTS);
            PricingEngine bsEngine = new AnalyticEuropeanEngine(bsProcess);

            EuropeanOption comp = new EuropeanOption(payoff, exercise);
            comp.setPricingEngine(bsEngine);

            double impliedVol = comp.impliedVolatility(npv, bsProcess, 1e-10, 100);

            if (Math.abs(impliedVol - expectedVol[i]) > tol) {
                fail("Failed to reproduce implied volatility"
                        + "\n    calculated: " + impliedVol
                        + "\n    expected  : " + expectedVol[i]);
            }
            if (Math.abs((comp.NPV() - npv) / npv) > tol) {
                fail("Failed to reproduce NPV"
                        + "\n    calculated: " + npv
                        + "\n    expected  : " + comp.NPV());
            }
            if (Math.abs(comp.delta() - option.delta()) > tol) {
                fail("Failed to reproduce NPV"
                        + "\n    calculated: " + npv
                        + "\n    expected  : " + comp.NPV());
            }
            if (Math.abs((comp.gamma() - option.gamma()) / npv) > tol) {
                fail("Failed to reproduce NPV"
                        + "\n    calculated: " + npv
                        + "\n    expected  : " + comp.NPV());
            }
            if (Math.abs((comp.theta() - option.theta()) / npv) > tol) {
                fail("Failed to reproduce NPV"
                        + "\n    calculated: " + npv
                        + "\n    expected  : " + comp.NPV());
            }
            if (Math.abs((comp.vega() - option.vega()) / npv) > tol) {
                fail("Failed to reproduce NPV"
                        + "\n    calculated: " + npv
                        + "\n    expected  : " + comp.NPV());
            }
        }
    }

    @Test
    public void testCompareBsmHWandHestonHW() {
        System.out.println("Comparing European option pricing for a BSM process"
                + " with one-factor Hull-White model...");

        DayCounter dc = new Actual365Fixed();

        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);
        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> divRates = new ArrayList<>();

        for (int i = 0; i <= 40; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.YEARS)));
            rates.add(0.01 + 0.0002 * Math.exp(Math.sin(i / 4.0)));
            divRates.add(0.02 + 0.0001 * Math.exp(Math.sin(i / 5.0)));
        }

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(new ZeroCurve(dates, divRates, dc), true);

        SimpleQuote vol = new SimpleQuote(0.25);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        BlackScholesMertonProcess bsmProcess =
                new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        HestonProcess hestonProcess = new HestonProcess(rTS, qTS, spot,
                vol.value() * vol.value(), 1.0,
                vol.value() * vol.value(), 1e-4, 0.0);

        HestonModel hestonModel = new HestonModel(hestonProcess);

        HullWhite hullWhiteModel = new HullWhite(rTS, 0.01, 0.01);

        PricingEngine bsmhwEngine =
                new AnalyticBSMHullWhiteEngine(0.0, bsmProcess, hullWhiteModel);

        PricingEngine hestonHwEngine =
                new AnalyticHestonHullWhiteEngine(hestonModel, hullWhiteModel, 128);

        double tol = 1e-5;
        double[] strike = {0.25, 0.5, 0.75, 0.8, 0.9,
                1.0, 1.1, 1.2, 1.5, 2.0, 4.0};
        int[] maturity = {1, 2, 3, 5, 10, 15, 20, 25, 30};
        Option.Type[] types = {Option.Type.Put, Option.Type.Call};

        for (Option.Type type : types) {
            for (double j : strike) {
                for (int l : maturity) {
                    Date maturityDate = today.add(new Period(l, TimeUnit.YEARS));

                    Exercise exercise = new EuropeanExercise(maturityDate);

                    double fwd = j * spot.currentLink().value()
                            * qTS.currentLink().discount(maturityDate)
                            / rTS.currentLink().discount(maturityDate);

                    StrikedTypePayoff payoff =
                            new PlainVanillaPayoff(type, fwd);

                    EuropeanOption option = new EuropeanOption(payoff, exercise);

                    option.setPricingEngine(bsmhwEngine);
                    double calculated = option.NPV();

                    option.setPricingEngine(hestonHwEngine);
                    double expected = option.NPV();

                    if (Math.abs(calculated - expected) > calculated * tol
                            && Math.abs(calculated - expected) > tol) {
                        boostErrors.add("Failed to reproduce npvs"
                                + "\n    calculated: " + calculated
                                + "\n    expected  : " + expected
                                + "\n    strike    : " + j
                                + "\n    maturity  : " + l
                                + "\n    type      : "
                                + ((type == Option.Type.Put) ? "Put" : "Call"));
                    }
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testZeroBondPricing() {
        System.out.println("Testing Monte-Carlo zero bond pricing...");

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        // construct a strange yield curve to check drifts and discounting
        // of the joint stochastic process

        List<Date> dates = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        List<Double> rates = new ArrayList<>();

        dates.add(today);
        rates.add(0.02);
        times.add(0.0);
        for (int i = 120; i < 240; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.MONTHS)));
            rates.add(0.02 + 0.0002 * Math.exp(Math.sin(i / 8.0)));
            times.add(dc.yearFraction(today, dates.get(dates.size() - 1)));
        }

        Date maturity = dates.get(dates.size() - 1)
                .add(new Period(10, TimeUnit.YEARS));
        dates.add(maturity);
        rates.add(0.04);
        times.add(dc.yearFraction(today, dates.get(dates.size() - 1)));

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);

        Handle<YieldTermStructure> ts =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> ds =
                new Handle<>(flatRate(today, 0.0, dc), true);

        HestonProcess hestonProcess = new HestonProcess(
                ts, ds, s0, 0.02, 1.0, 0.2, 0.5, -0.8);
        HullWhiteForwardProcess hwProcess =
                new HullWhiteForwardProcess(ts, 0.05, 0.05);
        hwProcess.setForwardMeasureTime(dc.yearFraction(today, maturity));
        HullWhite hwModel = new HullWhite(ts, 0.05, 0.05);

        HybridHestonHullWhiteProcess jointProcess =
                new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, -0.4);

        TimeGrid grid = new TimeGrid(times, 0, times.size() - 1);

        int factors = jointProcess.factors();
        int steps = grid.size() - 1;
        InverseCumulativeRsg rsg = new InverseCumulativeRsg(
                new SobolBrownianBridgeRsg(factors, steps), x -> x);
        MultiPathGenerator generator =
                new MultiPathGenerator(jointProcess, grid, rsg, false);

        int m = 90;
        GeneralStatistics[] zeroStat = new GeneralStatistics[m];
        GeneralStatistics[] optionStat = new GeneralStatistics[m];
        for (int i = 0; i < m; ++i) {
            zeroStat[i] = new GeneralStatistics();
            optionStat[i] = new GeneralStatistics();
        }

        int nrTrails = 8191;
        int optionTenor = 24;
        double strike = 0.5;

        for (int i = 0; i < nrTrails; ++i) {
            SampleMultiPath path = generator.next();

            for (int j = 1; j < m; ++j) {
                double t = grid.get(j);            // zero end and option maturity
                double T = grid.get(j + optionTenor); // maturity of zero bond
                // of option

                Array states = new Array(3);
                Array optionStates = new Array(3);
                for (int k = 0; k < jointProcess.size(); ++k) {
                    states.set(k, path.value.get(k).value(j));
                    optionStates.set(k, path.value.get(k).value(j + optionTenor));
                }

                double zeroBond = 1.0 / jointProcess.numeraire(t, states);
                double zeroOption = zeroBond
                        * Math.max(0.0, hwModel.discountBond(t, T, states.get(2))
                        - strike);

                zeroStat[j].add(zeroBond, 1.0);
                optionStat[j].add(zeroOption, 1.0);
            }
        }

        for (int j = 1; j < m; ++j) {
            double t = grid.get(j);
            double calculated = zeroStat[j].mean();
            double expected = ts.currentLink().discount(t);

            if (Math.abs(calculated - expected) > 0.03) {
                boostErrors.add("Failed to reproduce expected zero bond prices"
                        + "\n   t:          " + t
                        + "\n   calculated: " + calculated
                        + "\n   expected:   " + expected);
            }

            double T = grid.get(j + optionTenor);

            calculated = optionStat[j].mean();
            expected = hwModel.discountBondOption(Option.Type.Call, strike, t, T);

            if (Math.abs(calculated - expected) > 0.0035) {
                boostErrors.add("Failed to reproduce expected zero bond option prices"
                        + "\n   t:          " + t
                        + "\n   T:          " + T
                        + "\n   calculated: " + calculated
                        + "\n   expected:   " + expected);
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testMcVanillaPricing() {
        System.out.println("Testing Monte-Carlo vanilla option pricing...");

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        // construct a strange yield curve to check drifts and discounting
        // of the joint stochastic process

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> divRates = new ArrayList<>();

        for (int i = 0; i <= 40; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.YEARS)));
            rates.add(0.03 + 0.0003 * Math.exp(Math.sin(i / 4.0)));
            divRates.add(0.02 + 0.0001 * Math.exp(Math.sin(i / 5.0)));
        }

        Date maturity = today.add(new Period(20, TimeUnit.YEARS));

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(new ZeroCurve(dates, divRates, dc), true);
        SimpleQuote vol = new SimpleQuote(0.25);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        BlackScholesMertonProcess bsmProcess =
                new BlackScholesMertonProcess(s0, qTS, rTS, volTS);
        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.0625, 0.5, 0.0625, 1e-5, 0.3);
        HullWhiteForwardProcess hwProcess =
                new HullWhiteForwardProcess(rTS, 0.01, 0.01);
        hwProcess.setForwardMeasureTime(dc.yearFraction(today, maturity));

        double tol = 0.05;
        double[] corr = {-0.9, -0.5, 0.0, 0.5, 0.9};
        double[] strike = {100};

        for (double i : corr) {
            for (double j : strike) {
                HybridHestonHullWhiteProcess jointProcess =
                        new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, i);

                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Put, j);
                Exercise exercise = new EuropeanExercise(maturity);

                EuropeanOption optionHestonHW = new EuropeanOption(payoff, exercise);
                PricingEngine engine = new MakeMCHestonHullWhiteEngine(jointProcess)
                        .withSteps(1)
                        .withAntitheticVariate()
                        .withControlVariate()
                        .withAbsoluteTolerance(tol)
                        .withSeed(42)
                        .build();

                optionHestonHW.setPricingEngine(engine);

                HullWhite hwModel = new HullWhite(rTS,
                        hwProcess.a(), hwProcess.sigma());

                EuropeanOption optionBsmHW = new EuropeanOption(payoff, exercise);
                optionBsmHW.setPricingEngine(new AnalyticBSMHullWhiteEngine(
                        i, bsmProcess, hwModel));

                double calculated = optionHestonHW.NPV();
                double error = optionHestonHW.errorEstimate();
                double expected = optionBsmHW.NPV();

                if ((i != 0.0 && Math.abs(calculated - expected) > 3 * error)
                        || (i == 0.0 && Math.abs(calculated - expected) > 1e-4)) {
                    boostErrors.add("Failed to reproduce BSM-HW vanilla prices"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   error:      " + error
                            + "\n   expected:   " + expected);
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testMcPureHestonPricing() {
        System.out.println("Testing Monte-Carlo Heston option pricing...");

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        // construct a strange yield curve to check drifts and discounting
        // of the joint stochastic process

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> divRates = new ArrayList<>();

        for (int i = 0; i <= 100; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.MONTHS)));
            rates.add(0.02 + 0.0002 * Math.exp(Math.sin(i / 10.0)));
            divRates.add(0.02 + 0.0001 * Math.exp(Math.sin(i / 20.0)));
        }

        Date maturity = today.add(new Period(2, TimeUnit.YEARS));

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(new ZeroCurve(dates, divRates, dc), true);

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.08, 1.5, 0.0625, 0.5, -0.8);
        HullWhiteForwardProcess hwProcess =
                new HullWhiteForwardProcess(rTS, 0.1, 1e-8);
        hwProcess.setForwardMeasureTime(dc.yearFraction(
                today, maturity.add(new Period(1, TimeUnit.YEARS))));

        double tol = 0.001;
        double[] corr = {-0.45, 0.45, 0.25};
        double[] strike = {100, 75, 50, 150};

        for (double i : corr) {
            for (double j : strike) {
                HybridHestonHullWhiteProcess jointProcess =
                        new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, i,
                                HybridHestonHullWhiteProcess.Discretization.Euler);

                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Put, j);
                Exercise exercise = new EuropeanExercise(maturity);

                EuropeanOption optionHestonHW = new EuropeanOption(payoff, exercise);
                EuropeanOption optionPureHeston = new EuropeanOption(payoff, exercise);
                optionPureHeston.setPricingEngine(
                        new AnalyticHestonEngine(new HestonModel(hestonProcess)));

                double expected = optionPureHeston.NPV();

                optionHestonHW.setPricingEngine(
                        new MakeMCHestonHullWhiteEngine(jointProcess)
                                .withSteps(2)
                                .withAntitheticVariate()
                                .withControlVariate()
                                .withAbsoluteTolerance(tol)
                                .withSeed(42)
                                .build());

                double calculated = optionHestonHW.NPV();
                double error = optionHestonHW.errorEstimate();

                if (Math.abs(calculated - expected) > 3 * error
                        && Math.abs(calculated - expected) > tol) {
                    boostErrors.add("Failed to reproduce pure heston vanilla prices"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   error:      " + error
                            + "\n   expected:   " + expected);
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testAnalyticHestonHullWhitePricing() {
        System.out.println("Testing analytic Heston Hull-White option pricing...");

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        // construct a strange yield curve to check drifts and discounting
        // of the joint stochastic process

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> divRates = new ArrayList<>();

        for (int i = 0; i <= 40; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.YEARS)));
            rates.add(0.03 + 0.0001 * Math.exp(Math.sin(i / 4.0)));
            divRates.add(0.02 + 0.0002 * Math.exp(Math.sin(i / 3.0)));
        }

        Date maturity = today.add(new Period(5, TimeUnit.YEARS));
        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(new ZeroCurve(dates, divRates, dc), true);

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.08, 1.5, 0.0625, 0.5, -0.8);
        HestonModel hestonModel = new HestonModel(hestonProcess);

        HullWhiteForwardProcess hwFwdProcess =
                new HullWhiteForwardProcess(rTS, 0.01, 0.01);
        hwFwdProcess.setForwardMeasureTime(dc.yearFraction(today, maturity));
        HullWhite hullWhiteModel = new HullWhite(
                rTS, hwFwdProcess.a(), hwFwdProcess.sigma());

        double tol = 0.002;
        double[] strike = {80, 120};
        Option.Type[] types = {Option.Type.Put, Option.Type.Call};

        for (Option.Type type : types) {
            for (double j : strike) {
                HybridHestonHullWhiteProcess jointProcess =
                        new HybridHestonHullWhiteProcess(hestonProcess, hwFwdProcess,
                                0.0, HybridHestonHullWhiteProcess.Discretization.Euler);

                StrikedTypePayoff payoff = new PlainVanillaPayoff(type, j);
                Exercise exercise = new EuropeanExercise(maturity);

                EuropeanOption optionHestonHW = new EuropeanOption(payoff, exercise);
                optionHestonHW.setPricingEngine(
                        new MakeMCHestonHullWhiteEngine(jointProcess)
                                .withSteps(1)
                                .withAntitheticVariate()
                                .withControlVariate()
                                .withAbsoluteTolerance(tol)
                                .withSeed(42)
                                .build());

                EuropeanOption optionPureHeston = new EuropeanOption(payoff, exercise);
                optionPureHeston.setPricingEngine(
                        new AnalyticHestonHullWhiteEngine(hestonModel,
                                hullWhiteModel, 128));

                double calculated = optionHestonHW.NPV();
                double error = optionHestonHW.errorEstimate();
                double expected = optionPureHeston.NPV();

                if (Math.abs(calculated - expected) > 3 * error
                        && Math.abs(calculated - expected) > tol) {
                    boostErrors.add("Failed to reproduce hw heston vanilla prices"
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   error:      " + error
                            + "\n   expected:   " + expected);
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testCallableEquityPricing() {
        System.out.println("Testing the pricing of a callable equity product...");

        /*
           For the definition of the example product see
           Alexander Giese, On the Pricing of Auto-Callable Equity
           Structures in the Presence of Stochastic Volatility and
           Stochastic Interest Rates .
           http://workshop.mathfinance.de/2006/papers/giese/slides.pdf
        */

        int maturity = 7;
        DayCounter dc = new Actual365Fixed();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);
        SimpleQuote qRate = new SimpleQuote(0.04);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, qRate, dc), true);
        SimpleQuote rRate = new SimpleQuote(0.04);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, rRate, dc), true);

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, spot, 0.0625, 1.0, 0.24 * 0.24, 1e-4, 0.0);
        HullWhiteForwardProcess hwProcess =
                new HullWhiteForwardProcess(rTS, 0.00883, 0.00526);
        hwProcess.setForwardMeasureTime(dc.yearFraction(
                today, today.add(new Period(maturity + 1, TimeUnit.YEARS))));

        HybridHestonHullWhiteProcess jointProcess =
                new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, -0.4);

        Schedule schedule = new Schedule(today,
                today.add(new Period(maturity, TimeUnit.YEARS)),
                new Period(1, TimeUnit.YEARS), new Target(),
                BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        List<Double> times = new ArrayList<>();
        for (Date d : schedule.dates()) {
            times.add(dc.yearFraction(today, d));
        }

        for (int i = 0; i <= maturity; ++i)
            times.set(i, (double) i);

        TimeGrid grid = new TimeGrid(times);

        double[] redemption = new double[maturity];
        for (int i = 0; i < maturity; ++i) {
            redemption[i] = 1.07 + 0.03 * i;
        }

        long seed = 42;
        InverseCumulativeRsg rsg = PseudoRandom.make_sequence_generator(
                jointProcess.factors() * (grid.size() - 1), seed);

        MultiPathGenerator generator =
                new MultiPathGenerator(jointProcess, grid, rsg, false);
        GeneralStatistics stat = new GeneralStatistics();

        double antitheticPayoff = 0;
        int nrTrails = 40000;
        for (int i = 0; i < nrTrails; ++i) {
            boolean antithetic = (i % 2) != 0;

            SampleMultiPath path = antithetic ? generator.antithetic()
                    : generator.next();

            double payoff = 0;
            for (int j = 1; j <= maturity; ++j) {
                if (path.value.get(0).value(j) > spot.currentLink().value()) {
                    Array states = new Array(3);
                    for (int k = 0; k < 3; ++k) {
                        states.set(k, path.value.get(k).value(j));
                    }
                    payoff = redemption[j - 1]
                            / jointProcess.numeraire(grid.get(j), states);
                    break;
                } else if (j == maturity) {
                    Array states = new Array(3);
                    for (int k = 0; k < 3; ++k) {
                        states.set(k, path.value.get(k).value(j));
                    }
                    payoff = 1.0 / jointProcess.numeraire(grid.get(j), states);
                }
            }

            if (antithetic) {
                stat.add(0.5 * (antitheticPayoff + payoff), 1.0);
            } else {
                antitheticPayoff = payoff;
            }
        }

        double expected = 0.938;
        double calculated = stat.mean();
        double error = stat.errorEstimate();

        if (Math.abs(expected - calculated) > 3 * error) {
            boostErrors.add("Failed to reproduce auto-callable equity structure price"
                    + "\n   calculated: " + calculated
                    + "\n   error:      " + error
                    + "\n   expected:   " + expected);
        }

        checkBoostErrors();
    }

    @Test
    public void testDiscretizationError() {
        System.out.println("Testing the discretization error of the "
                + "Heston Hull-White process...");

        DayCounter dc = new Actual360();
        Date today = Date.todaysDate();

        Settings.instance.evaluationDate().equal(today);

        // construct a strange yield curve to check drifts and discounting
        // of the joint stochastic process

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> divRates = new ArrayList<>();

        for (int i = 0; i <= 31; ++i) {
            dates.add(today.add(new Period(i, TimeUnit.YEARS)));
            rates.add(0.04 + 0.0001 * Math.exp(Math.sin((double) i)));
            divRates.add(0.04 + 0.0001 * Math.exp(Math.sin((double) i)));
        }

        Date maturity = today.add(new Period(10, TimeUnit.YEARS));
        double v = 0.25;

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);
        SimpleQuote vol = new SimpleQuote(v);
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(new ZeroCurve(dates, rates, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(new ZeroCurve(dates, divRates, dc), true);

        BlackScholesMertonProcess bsmProcess =
                new BlackScholesMertonProcess(s0, qTS, rTS, volTS);

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, v * v, 1, v * v, 1e-6, -0.4);

        HullWhiteForwardProcess hwProcess =
                new HullWhiteForwardProcess(rTS, 0.01, 0.01);
        hwProcess.setForwardMeasureTime(20.1472222222222222);

        double tol = 0.05;
        double[] corr = {-0.85, 0.5};
        double[] strike = {50, 100, 125};

        for (double i : corr) {
            for (double j : strike) {
                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Put, j);
                Exercise exercise = new EuropeanExercise(maturity);

                EuropeanOption optionBsmHW = new EuropeanOption(payoff, exercise);
                HullWhite hwModel = new HullWhite(
                        rTS, hwProcess.a(), hwProcess.sigma());
                optionBsmHW.setPricingEngine(
                        new AnalyticBSMHullWhiteEngine(i, bsmProcess, hwModel));

                double expected = optionBsmHW.NPV();

                EuropeanOption optionHestonHW = new EuropeanOption(payoff, exercise);
                HybridHestonHullWhiteProcess jointProcess =
                        new HybridHestonHullWhiteProcess(hestonProcess, hwProcess, i);
                optionHestonHW.setPricingEngine(
                        new MakeMCHestonHullWhiteEngine(jointProcess)
                                .withSteps(1)
                                .withAntitheticVariate()
                                .withAbsoluteTolerance(tol)
                                .withSeed(42)
                                .build());

                double calculated = optionHestonHW.NPV();
                double error = optionHestonHW.errorEstimate();

                if (Math.abs(calculated - expected) > 3 * error
                        && Math.abs(calculated - expected) > 1e-5) {
                    boostErrors.add("Failed to reproduce discretization error"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   error:      " + error
                            + "\n   expected:   " + expected);
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonHullWhiteEngine() {
        System.out.println("Testing the FDM Heston Hull-White engine...");

        Date today = new Date(28, Month.MARCH, 2004);
        Settings.instance.evaluationDate().equal(today);
        Date exerciseDate = new Date(28, Month.MARCH, 2012);
        DayCounter dc = new Actual365Fixed();

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.02, dc), true);

        double vol = 0.30;
        Handle<BlackVolTermStructure> volTS =
                new Handle<>(flatVol(today, vol, dc), true);

        double v0 = vol * vol;
        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, v0, 1.0, v0, 0.000001, 0.0);

        BlackScholesMertonProcess stochProcess =
                new BlackScholesMertonProcess(s0, qTS, rTS, volTS);

        HullWhiteProcess hwProcess =
                new HullWhiteProcess(rTS, 0.00883, 0.01);
        HullWhite hwModel =
                new HullWhite(rTS, hwProcess.a(), hwProcess.sigma());

        Exercise exercise = new EuropeanExercise(exerciseDate);
        double[] corr = {-0.85, 0.5};
        double[] strike = {75, 120, 160};

        for (double i : corr) {
            for (double j : strike) {
                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, j);
                EuropeanOption option = new EuropeanOption(payoff, exercise);

                option.setPricingEngine(new FdHestonHullWhiteVanillaEngine(
                        new HestonModel(hestonProcess), hwProcess, i,
                        50, 200, 10, 15));
                double calculated = option.NPV();
                double calculatedDelta = option.delta();
                double calculatedGamma = option.gamma();

                option.setPricingEngine(new AnalyticBSMHullWhiteEngine(
                        i, stochProcess, hwModel));
                double expected = option.NPV();
                double expectedDelta = option.delta();
                double expectedGamma = option.gamma();

                double npvTol = 0.01;
                if (Math.abs(calculated - expected) > npvTol) {
                    boostErrors.add("Failed to reproduce analytic npv values"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   expected:   " + expected);
                }
                double deltaTol = 0.001;
                if (Math.abs(calculatedDelta - expectedDelta) > deltaTol) {
                    boostErrors.add("Failed to reproduce analytic delta values"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   expected:   " + expected);
                }
                double gammaTol = 0.001;
                if (Math.abs(calculatedGamma - expectedGamma) > gammaTol) {
                    boostErrors.add("Failed to reproduce analytic gamma values"
                            + "\n   corr:       " + i
                            + "\n   strike:     " + j
                            + "\n   calculated: " + calculated
                            + "\n   expected:   " + expected);
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testBsmHullWhitePricing() {
        System.out.println("Testing convergence speed of Heston-Hull-White engine...");

        Date today = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(today);

        double maturity = 5.0;
        double equityIrCorr = -0.4;
        double[] strikes = {75, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130, 140, 150};
        int[] listOfTimeStepsPerYear = {20};

        HestonModelData hestonModelData = new HestonModelData(
                "BSM-HW Model", 0.09, 1.0, 0.09, QL_EPSILON, 0.0, 0.04, 0.03);
        HullWhiteModelData hwModelData = hullWhiteModels[0];
        boolean[] controlVariate = {true, false};

        HestonProcess hp = makeHestonProcess(hestonModelData);
        HestonModel hestonModel = new HestonModel(hp);

        HullWhiteProcess hwProcess = new HullWhiteProcess(
                hp.riskFreeRate(), hwModelData.a, hwModelData.sigma);
        HullWhite hullWhiteModel = new HullWhite(
                hp.riskFreeRate(), hwProcess.a(), hwProcess.sigma());

        BlackScholesMertonProcess bsmProcess = new BlackScholesMertonProcess(
                hp.s0(), hp.dividendYield(), hp.riskFreeRate(),
                new Handle<>(flatVol(today, Math.sqrt(hestonModelData.theta),
                        hp.riskFreeRate().currentLink().dayCounter()), true));

        PricingEngine bsmhwEngine = new AnalyticBSMHullWhiteEngine(
                equityIrCorr, bsmProcess, hullWhiteModel);

        double[] tolWithCV = {2e-4, 2e-4, 2e-4, 2e-4, 0.01};
        double[] tolWithOutCV = {5e-3, 5e-3, 5e-3, 5e-3, 0.02};
        List<Double> strikesList = new ArrayList<>();
        for (double strike : strikes)
            strikesList.add(strike);

        for (int l = 0; l < schemes.length; ++l) {
            SchemeData scheme = schemes[l];
            for (boolean i : controlVariate) {
                for (int u : listOfTimeStepsPerYear) {
                    int tSteps = (int) (maturity * u);

                    FdHestonHullWhiteVanillaEngine fdEngine =
                            new FdHestonHullWhiteVanillaEngine(hestonModel,
                                    hwProcess, equityIrCorr, tSteps,
                                    400, 2, 10, 0, i, scheme.schemeDesc);
                    fdEngine.enableMultipleStrikesCaching(strikesList);

                    double avgPriceDiff = 0.0;
                    for (double strike : strikes) {
                        VanillaOptionData optionData = new VanillaOptionData(
                                strike, maturity, Option.Type.Call);
                        EuropeanOption option = makeVanillaOption(optionData);
                        option.setPricingEngine(bsmhwEngine);
                        double expected = option.NPV();

                        option.setPricingEngine(fdEngine);
                        double calculated = option.NPV();
                        avgPriceDiff
                                += Math.abs(expected - calculated) / strikesList.size();
                    }

                    if (i && tolWithCV[l] < avgPriceDiff) {
                        boostErrors.add("Failed to reproduce BSM-Hull-White prices"
                                + "\n   scheme       : " + scheme.name
                                + "\n   model        : " + hestonModelData.name
                                + "\n   CV           : on");
                    }

                    if (!i && tolWithOutCV[l] < avgPriceDiff) {
                        boostErrors.add("Failed to reproduce BSM-Hull-White prices"
                                + "\n   scheme       : " + scheme.name
                                + "\n   model        : " + hestonModelData.name
                                + "\n   CV           : off");
                    }
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testSpatialDiscretizatinError() {
        System.out.println("Testing spatial convergence speed of Heston engine...");

        Date today = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(today);

        double maturity = 1.0;
        double[] strikes = {75, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130, 140, 150};
        int[] listOfTimeStepsPerYear = {40};

        double[] tol = {0.02, 0.02, 0.02, 0.02, 0.05};
        List<Double> strikesList = new ArrayList<>();
        for (double strike : strikes)
            strikesList.add(strike);

        for (int u : listOfTimeStepsPerYear) {
            for (int i = 0; i < schemes.length; ++i) {
                for (HestonModelData j : hestonModels) {
                    double avgPriceDiff = 0;
                    HestonProcess hestonProcess = makeHestonProcess(j);
                    HestonModel hestonModel = new HestonModel(hestonProcess);

                    PricingEngine analyticEngine =
                            new AnalyticHestonEngine(hestonModel, 172);

                    int tSteps = (int) (maturity * u);

                    FdHestonVanillaEngine fdEngine = new FdHestonVanillaEngine(
                            hestonModel, tSteps, 200, 40, 0,
                            schemes[i].schemeDesc, null);
                    fdEngine.enableMultipleStrikesCaching(strikesList);

                    for (double strike : strikes) {
                        VanillaOptionData optionData = new VanillaOptionData(
                                strike, maturity, Option.Type.Call);
                        EuropeanOption option = makeVanillaOption(optionData);
                        option.setPricingEngine(analyticEngine);
                        double expected = option.NPV();

                        option.setPricingEngine(fdEngine);
                        double calculated = option.NPV();

                        avgPriceDiff
                                += Math.abs(expected - calculated) / strikesList.size();
                    }

                    if (avgPriceDiff > tol[i]) {
                        boostErrors.add("\nFailed to reproduce Heston prices"
                                + "\n scheme    : " + schemes[i].name
                                + "\n model     : " + j.name
                                + "\n error     : " + avgPriceDiff
                                + "\n tolerance : " + tol[i]);
                    }
                }
            }
        }

        checkBoostErrors();
    }

    /** Official test-local {@code HestonHullWhiteCorrelationConstraint}. */
    private static class HestonHullWhiteCorrelationConstraint extends Constraint {
        private static class Impl extends ConstraintImpl {
            private final double equityShortRateCorr_;

            Impl(double equityShortRateCorr) {
                equityShortRateCorr_ = equityShortRateCorr;
            }

            @Override
            public boolean test(Array params) {
                double rho = params.get(3);

                return rho * rho + equityShortRateCorr_ * equityShortRateCorr_ <= 1.0;
            }
        }

        HestonHullWhiteCorrelationConstraint(double equityShortRateCorr) {
            super(new Impl(equityShortRateCorr));
        }
    }

    @Test
    public void testHestonHullWhiteCalibration() {
        System.out.println("Testing the Heston Hull-White calibration...");

        // Calibration of a hybrid Heston-Hull-White model using
        // the finite difference HestonHullWhite pricing engine
        //
        // Input surface is based on a Heston-Hull-White model with
        // Hull-White: a = 0.00883, sigma = 0.00631
        // Heston    : nu = 0.12, kappa = 2.0,
        //             theta = 0.09, sigma = 0.5, rho=-0.75
        // Equity Short rate correlation: -0.5

        DayCounter dc = new Actual365Fixed();
        Calendar calendar = new Target();
        Date today = new Date(28, Month.MARCH, 2004);
        Settings.instance.evaluationDate().equal(today);

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.05, dc), true);

        // assuming, that the Hull-White process is already calibrated
        // on a given set of pure interest rate calibration instruments.
        HullWhiteProcess hwProcess =
                new HullWhiteProcess(rTS, 0.00883, 0.00631);
        HullWhite hullWhiteModel =
                new HullWhite(rTS, hwProcess.a(), hwProcess.sigma());

        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.02, dc), true);
        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        // starting point of the pure Heston calibration
        double start_v0 = 0.2 * 0.2;
        double start_theta = start_v0;
        double start_kappa = 0.5;
        double start_sigma = 0.25;
        double start_rho = -0.5;

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, start_v0, start_kappa,
                start_theta, start_sigma, start_rho);
        HestonModel analyticHestonModel = new HestonModel(hestonProcess);
        PricingEngine analyticHestonEngine =
                new AnalyticHestonEngine(analyticHestonModel, 164);
        HestonModel fdmHestonModel = new HestonModel(hestonProcess);

        double equityShortRateCorr = -0.5;

        double[] strikes = {50, 75, 90, 100, 110, 125, 150, 200};
        double[] maturities = {1 / 12., 3 / 12., 0.5, 1.0, 2.0, 3.0, 5.0, 7.5, 10};

        double[] vol = {
                0.482627, 0.407617, 0.366682, 0.340110, 0.314266, 0.280241, 0.252471, 0.325552,
                0.464811, 0.393336, 0.354664, 0.329758, 0.305668, 0.273563, 0.244024, 0.244886,
                0.441864, 0.375618, 0.340464, 0.318249, 0.297127, 0.268839, 0.237972, 0.225553,
                0.407506, 0.351125, 0.322571, 0.305173, 0.289034, 0.267361, 0.239315, 0.213761,
                0.366761, 0.326166, 0.306764, 0.295279, 0.284765, 0.270592, 0.250702, 0.222928,
                0.345671, 0.314748, 0.300259, 0.291744, 0.283971, 0.273475, 0.258503, 0.235683,
                0.324512, 0.303631, 0.293981, 0.288338, 0.283193, 0.276248, 0.266271, 0.250506,
                0.311278, 0.296340, 0.289481, 0.285482, 0.281840, 0.276924, 0.269856, 0.258609,
                0.303219, 0.291534, 0.286187, 0.283073, 0.280239, 0.276414, 0.270926, 0.262173
        };

        List<CalibrationHelper> options = new ArrayList<>();

        for (int i = 0; i < maturities.length; ++i) {
            Period maturity = new Period(
                    (int) Math.round(maturities[i] * 12.0), TimeUnit.MONTHS);
            Exercise exercise = new EuropeanExercise(today.add(maturity));

            for (int j = 0; j < strikes.length; ++j) {
                StrikedTypePayoff payoff = new PlainVanillaPayoff(
                        strikes[j] * rTS.currentLink().discount(maturities[i])
                                >= s0.currentLink().value()
                                * qTS.currentLink().discount(maturities[i])
                                ? Option.Type.Call
                                : Option.Type.Put,
                        strikes[j]);
                RelinkableHandle<Quote> v = new RelinkableHandle<>(
                        new SimpleQuote(vol[i * strikes.length + j]), true);

                HestonModelHelper helper = new HestonModelHelper(
                        maturity, calendar, s0,
                        strikes[j], v, rTS, qTS,
                        BlackCalibrationHelper.CalibrationErrorType.PriceError);
                options.add(helper);
                double marketValue = helper.marketValue();

                // Improve the quality of the starting point
                // for the full Heston-Hull-White calibration
                SimpleQuote volQuote = new SimpleQuote();
                // Official C++ destroys these function-local Observers at the
                // end of each inner-loop iteration. Java Settings/IndexManager
                // keep strong Observer refs, so detach them before the later
                // FD Heston-Hull-White calibrate() allocates 3-D operators.
                BlackVolTermStructure seedVol = flatVol(today,
                        v.currentLink().value(), dc);
                GeneralizedBlackScholesProcess seedProcess =
                        new GeneralizedBlackScholesProcess(
                                s0, qTS, rTS,
                                new Handle<>(seedVol, true),
                                new EulerDiscretization(), false);
                GeneralizedBlackScholesProcess bsProcess =
                        ImpliedVolatilityHelper.clone(seedProcess, volQuote);

                EuropeanOption dummyOption = new EuropeanOption(payoff, exercise);

                AnalyticBSMHullWhiteEngine bshwEngine = new AnalyticBSMHullWhiteEngine(
                        equityShortRateCorr, bsProcess, hullWhiteModel);

                double vt = ImpliedVolatilityHelper.calculate(
                        dummyOption, bshwEngine, volQuote,
                        marketValue, 1e-8, 100, 0.0001, 10);

                v.linkTo(new SimpleQuote(vt), true);

                helper.setPricingEngine(analyticHestonEngine);

                dummyOption.setPricingEngine(null);
                dummyOption.unregisterWithAll();
                bshwEngine.unregisterWithAll();
                bsProcess.unregisterWithAll();
                bsProcess.blackVolatility().currentLink().unregisterWithAll();
                seedProcess.unregisterWithAll();
                seedVol.unregisterWithAll();
            }
        }

        HestonHullWhiteCorrelationConstraint corrConstraint =
                new HestonHullWhiteCorrelationConstraint(equityShortRateCorr);
        LevenbergMarquardt om = new LevenbergMarquardt(1e-6, 1e-8, 1e-8);
        analyticHestonModel.calibrate(options, om,
                new EndCriteria(400, 40, 1.0e-8, 1.0e-4, 1.0e-8),
                corrConstraint, new ArrayList<>(), new ArrayList<>());

        // Official first-batch helpers leave scope before the FD calibrate.
        for (CalibrationHelper helper : options) {
            if (helper instanceof BlackCalibrationHelper) {
                ((BlackCalibrationHelper) helper).setPricingEngine(null);
            }
            if (helper instanceof Observer) {
                ((Observer) helper).unregisterWithAll();
            }
        }
        if (analyticHestonEngine instanceof Observer) {
            ((Observer) analyticHestonEngine).unregisterWithAll();
        }
        options.clear();
        options = new ArrayList<>();
        fdmHestonModel.setParams(analyticHestonModel.params());

        List<FdHestonHullWhiteVanillaEngine> fdEngines = new ArrayList<>();
        for (int i = 0; i < maturities.length; ++i) {
            int tGrid = (int) Math.max(5.0, maturities[i] * 5.0);
            FdHestonHullWhiteVanillaEngine engine =
                    new FdHestonHullWhiteVanillaEngine(fdmHestonModel, hwProcess,
                            equityShortRateCorr,
                            tGrid, 45, 11, 5, 0, true);
            fdEngines.add(engine);

            engine.enableMultipleStrikesCaching(
                    new ArrayList<>(Arrays.asList(
                            Arrays.stream(strikes).boxed().toArray(Double[]::new))));

            Period maturity = new Period(
                    (int) Math.round(maturities[i] * 12.0), TimeUnit.MONTHS);

            for (int j = 0; j < strikes.length; ++j) {
                // multiple strikes engine works best if the first option
                // per maturity has the average strike (because the first option
                // is priced first during the calibration and the first pricing
                // is used to calculate the prices for all strikes
                int js = (j + (strikes.length - 1) / 2) % strikes.length;

                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, strikes[js]);
                Handle<Quote> v = new Handle<>(new SimpleQuote(
                        vol[i * strikes.length + js]), true);
                HestonModelHelper helper = new HestonModelHelper(
                        maturity, calendar, s0,
                        strikes[js], v, rTS, qTS,
                        BlackCalibrationHelper.CalibrationErrorType.PriceError);
                options.add(helper);

                helper.setPricingEngine(engine);
            }
        }

        LevenbergMarquardt vm = new LevenbergMarquardt(1e-6, 1e-2, 1e-2);
        fdmHestonModel.calibrate(options, vm,
                new EndCriteria(400, 40, 1.0e-8, 1.0e-4, 1.0e-8),
                corrConstraint, new ArrayList<>(), new ArrayList<>());

        double relTol = 0.01;
        double expected_v0 = 0.12;
        double expected_kappa = 2.0;
        double expected_theta = 0.09;
        double expected_sigma = 0.5;
        double expected_rho = -0.75;

        if (Math.abs(fdmHestonModel.v0() - expected_v0) / expected_v0 > relTol) {
            boostErrors.add("Failed to reproduce Heston-Hull-White model"
                    + "\n   v0 calculated: " + fdmHestonModel.v0()
                    + "\n   v0 expected  : " + expected_v0
                    + "\n   relatove tol : " + relTol);
        }
        if (Math.abs(fdmHestonModel.theta() - expected_theta) / expected_theta > relTol) {
            boostErrors.add("Failed to reproduce Heston-Hull-White model"
                    + "\n   theta calculated: " + fdmHestonModel.theta()
                    + "\n   theta expected  : " + expected_theta
                    + "\n   relatove tol    : " + relTol);
        }
        if (Math.abs(fdmHestonModel.kappa() - expected_kappa) / expected_kappa > relTol) {
            boostErrors.add("Failed to reproduce Heston-Hull-White model"
                    + "\n   kappa calculated: " + fdmHestonModel.kappa()
                    + "\n   kappa expected  : " + expected_kappa
                    + "\n   relatove tol    : " + relTol);
        }
        if (Math.abs(fdmHestonModel.sigma() - expected_sigma) / expected_sigma > relTol) {
            boostErrors.add("Failed to reproduce Heston-Hull-White model"
                    + "\n   sigma calculated: " + fdmHestonModel.sigma()
                    + "\n   sigma expected  : " + expected_sigma
                    + "\n   relatove tol    : " + relTol);
        }
        if (Math.abs(fdmHestonModel.rho() - expected_rho) / expected_rho > relTol) {
            boostErrors.add("Failed to reproduce Heston-Hull-White model"
                    + "\n   rho calculated: " + fdmHestonModel.rho()
                    + "\n   rho expected  : " + expected_rho
                    + "\n   relatove tol  : " + relTol);
        }

        // Official function-local engines are destroyed at the end of the
        // test case; detach their model observers explicitly.
        for (FdHestonHullWhiteVanillaEngine engine : fdEngines)
            engine.unregisterWithAll();

        checkBoostErrors();
    }

    @Test
    public void testH1HWPricingEngine() {
        System.out.println("Testing the H1-HW approximation engine...");

        /*
         * Example taken from Lech Aleksander Grzelak,
         * Equity and Foreign Exchange Hybrid Models for Pricing Long-Maturity
         * Financial Derivatives,
         * http://repository.tudelft.nl/assets/uuid:a8e1a007-bd89-481a-aee3-0e22f15ade6b/PhDThesis_main.pdf
         */

        Date today = new Date(15, Month.JULY, 2012);
        Settings.instance.evaluationDate().equal(today);
        Date exerciseDate = new Date(13, Month.JULY, 2022);
        DayCounter dc = new Actual365Fixed();

        Exercise exercise = new EuropeanExercise(exerciseDate);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double r = 0.02;
        double q = 0.00;
        double v0 = 0.05;
        double theta = 0.05;
        double kappa_v = 0.3;
        double[] sigma_v = {0.3, 0.6};
        double rho_sv = -0.30;
        double rho_sr = 0.6;
        double kappa_r = 0.01;
        double sigma_r = 0.01;

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, r, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, q, dc), true);

        Handle<BlackVolTermStructure> flatVolTS =
                new Handle<>(flatVol(today, 0.20, dc), true);
        GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(s0, qTS, rTS, flatVolTS,
                        new EulerDiscretization(), false);

        HullWhiteProcess hwProcess =
                new HullWhiteProcess(rTS, kappa_r, sigma_r);
        HullWhite hullWhiteModel = new HullWhite(rTS, kappa_r, sigma_r);

        double tol = 0.0001;
        double[] strikes = {40, 80, 100, 120, 180};
        double[][] expected = {
                {0.267503, 0.235742, 0.228223, 0.223461, 0.217855},
                {0.263626, 0.211625, 0.199907, 0.193502, 0.190025}
        };

        for (int j = 0; j < sigma_v.length; ++j) {
            HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, s0, v0, kappa_v, theta,
                    sigma_v[j], rho_sv);
            HestonModel hestonModel = new HestonModel(hestonProcess);

            for (int i = 0; i < strikes.length; ++i) {
                StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, strikes[i]);

                EuropeanOption option = new EuropeanOption(payoff, exercise);

                PricingEngine analyticH1HWEngine = new AnalyticH1HWEngine(
                        hestonModel, hullWhiteModel, rho_sr, 144);
                option.setPricingEngine(analyticH1HWEngine);
                double impliedH1HW = option.impliedVolatility(option.NPV(), bsProcess);

                if (Math.abs(expected[j][i] - impliedH1HW) > tol) {
                    boostErrors.add("Failed to reproduce H1HW implied volatility"
                            + "\n   expected       : " + expected[j][i]
                            + "\n   calculated     : " + impliedH1HW
                            + "\n   tol            : " + tol
                            + "\n   strike         : " + strikes[i]
                            + "\n   sigma          : " + sigma_v[j]);
                }
            }
        }

        checkBoostErrors();
    }
}
