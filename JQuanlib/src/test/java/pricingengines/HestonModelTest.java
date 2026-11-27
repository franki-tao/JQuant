package pricingengines;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.TimeGrid;
import jquant.indexes.IndexManager;
import jquant.math.Array;
import jquant.math.Function;
import jquant.math.Point;
import jquant.math.distributions.NormalDistribution;
import jquant.math.integrals.FourierIntegration;
import jquant.math.optimization.BoundaryConstraint;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.PositiveConstraint;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import jquant.math.randomnumbers.PseudoRandom;
import jquant.math.randomnumbers.RngType;
import jquant.math.statistics.GeneralStatistics;
import jquant.methods.finitedifferences.meshers.Concentrating1dMesher;
import jquant.methods.finitedifferences.operators.NumericalDifferentiation;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.SampleMultiPath;
import jquant.models.BlackCalibrationHelper;
import jquant.models.CalibrationHelper;
import jquant.models.ConstantParameter;
import jquant.models.PiecewiseConstantParameter;
import jquant.models.equity.HestonModel;
import jquant.models.equity.HestonModelHelper;
import jquant.models.equity.PiecewiseTimeDependentHestonModel;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.barrier.FdHestonBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.pricingengines.vanilla.AnalyticPTDHestonEngine;
import jquant.pricingengines.vanilla.AnalyticPDFHestonEngine;
import jquant.pricingengines.vanilla.COSHestonEngine;
import jquant.pricingengines.vanilla.ExponentialFittingHestonEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.pricingengines.vanilla.FdHestonVanillaEngine;
import jquant.pricingengines.vanilla.HestonExpansionEngine;
import jquant.pricingengines.vanilla.MakeFdHestonVanillaEngine;
import jquant.pricingengines.vanilla.MakeMCEuropeanHestonEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.volatility.equityfx.HestonBlackVolSurface;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/hestonmodel.cpp} Heston model tests.
 *
 * <p>Ports the official cases {@code testBlackCalibration},
 * {@code testDAXCalibration}, {@code testAnalyticVsBlack},
 * {@code testAnalyticVsCached}, {@code testMcVsCached},
 * {@code testKahlJaeckelCase}, {@code testFdBarrierVsCached},
 * {@code testFdVanillaVsCached},
 * {@code testFdVanillaWithDividendsVsCached}, {@code testFdAmerican} and
 * {@code testMultipleStrikesEngine}. Official {@code BOOST_ERROR} records a
 * failure but continues the test case; the messages are accumulated and the
 * test fails at the end. Official {@code BOOST_FAIL} aborts immediately.
 *
 * <p>The class also contains all remaining official Heston model cases, from
 * {@code testDifferentIntegrals} through {@code testAnalyticPDFHestonEngine}.
 * Official {@code QL_CHECK_SMALL} checks are absolute checks; they are
 * accumulated like {@code BOOST_ERROR} rather than throwing, so a failing
 * check does not hide later failures in the same case.
 */
public class HestonModelTest {

    private SavedSettings savedSettings;

    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static class CalibrationMarketData {
        Handle<Quote> s0;
        Handle<YieldTermStructure> riskFreeTS, dividendYield;
        List<CalibrationHelper> options;
    }

    // Official getDAXCalibrationMarketData(): this example is taken from
    // A. Sepp, "Pricing European-Style Options under Jump Diffusion
    // Processes with Stochastic Volatility: Applications of Fourier
    // Transform".
    private CalibrationMarketData getDAXCalibrationMarketData() {
        Date settlementDate = Settings.instance.evaluationDate().Date();

        DayCounter dayCounter = new Actual365Fixed();
        Calendar calendar = new Target();

        int[] t = {13, 41, 75, 165, 256, 345, 524, 703};
        double[] r = {0.0357, 0.0349, 0.0341, 0.0355,
                0.0359, 0.0368, 0.0386, 0.0401};

        List<Date> dates = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        dates.add(settlementDate);
        rates.add(0.0357);
        for (int i = 0; i < 8; ++i) {
            dates.add(settlementDate.add(t[i]));
            rates.add(r[i]);
        }
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(new ZeroCurve(dates, rates, dayCounter), true);

        Handle<YieldTermStructure> dividendYield = new Handle<>(
                new FlatForward(settlementDate, 0.0, dayCounter,
                        Compounding.Continuous, Frequency.ANNUAL), true);

        double[] v =
                {0.6625, 0.4875, 0.4204, 0.3667, 0.3431, 0.3267, 0.3121, 0.3121,
                        0.6007, 0.4543, 0.3967, 0.3511, 0.3279, 0.3154, 0.2984, 0.2921,
                        0.5084, 0.4221, 0.3718, 0.3327, 0.3155, 0.3027, 0.2919, 0.2889,
                        0.4541, 0.3869, 0.3492, 0.3149, 0.2963, 0.2926, 0.2819, 0.2800,
                        0.4060, 0.3607, 0.3330, 0.2999, 0.2887, 0.2811, 0.2751, 0.2775,
                        0.3726, 0.3396, 0.3108, 0.2781, 0.2788, 0.2722, 0.2661, 0.2686,
                        0.3550, 0.3277, 0.3012, 0.2781, 0.2781, 0.2661, 0.2661, 0.2681,
                        0.3428, 0.3209, 0.2958, 0.2740, 0.2688, 0.2627, 0.2580, 0.2620,
                        0.3302, 0.3062, 0.2799, 0.2631, 0.2573, 0.2533, 0.2504, 0.2544,
                        0.3343, 0.2959, 0.2705, 0.2540, 0.2504, 0.2464, 0.2448, 0.2462,
                        0.3460, 0.2845, 0.2624, 0.2463, 0.2425, 0.2385, 0.2373, 0.2422,
                        0.3857, 0.2860, 0.2578, 0.2399, 0.2357, 0.2327, 0.2312, 0.2351,
                        0.3976, 0.2860, 0.2607, 0.2356, 0.2297, 0.2268, 0.2241, 0.2320};

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(4468.17), true);
        double[] strike = {3400, 3600, 3800, 4000, 4200, 4400,
                4500, 4600, 4800, 5000, 5200, 5400, 5600};

        List<CalibrationHelper> options = new ArrayList<>();

        for (int s = 0; s < 13; ++s) {
            for (int m = 0; m < 8; ++m) {
                Handle<Quote> vol = new Handle<>(new SimpleQuote(v[s * 8 + m]), true);

                Period maturity = new Period((t[m] + 3) / 7, TimeUnit.WEEKS); // round to weeks
                options.add(new HestonModelHelper(maturity, calendar,
                        s0, strike[s], vol,
                        riskFreeTS, dividendYield,
                        BlackCalibrationHelper.CalibrationErrorType.ImpliedVolError));
            }
        }

        CalibrationMarketData marketData = new CalibrationMarketData();
        marketData.s0 = s0;
        marketData.riskFreeTS = riskFreeTS;
        marketData.dividendYield = dividendYield;
        marketData.options = options;

        return marketData;
    }

    @Test
    public void testBlackCalibration() {
        System.out.println(
                "Testing Heston model calibration using a flat volatility surface...");

        /* calibrate a Heston model to a constant volatility surface without
           smile. expected result is a vanishing volatility of the volatility.
           In addition theta and v0 should be equal to the constant variance */

        DayCounter dayCounter = new Actual360();
        Calendar calendar = new NullCalendar();

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.04, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.50, dayCounter), true);

        List<Period> optionMaturities = List.of(
                new Period(1, TimeUnit.MONTHS), new Period(2, TimeUnit.MONTHS),
                new Period(3, TimeUnit.MONTHS), new Period(6, TimeUnit.MONTHS),
                new Period(9, TimeUnit.MONTHS), new Period(1, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS));

        List<CalibrationHelper> options = new ArrayList<>();
        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);
        Handle<Quote> vol = new Handle<>(new SimpleQuote(0.1), true);
        double volatility = vol.currentLink().value();

        for (Period optionMaturity : optionMaturities) {
            for (double moneyness = -1.0; moneyness < 2.0; moneyness += 1.0) {
                double tau = dayCounter.yearFraction(
                        riskFreeTS.currentLink().referenceDate(),
                        calendar.advance(riskFreeTS.currentLink().referenceDate(),
                                optionMaturity));
                double fwdPrice = s0.currentLink().value()
                        * dividendTS.currentLink().discount(tau)
                        / riskFreeTS.currentLink().discount(tau);
                double strikePrice = fwdPrice
                        * Math.exp(-moneyness * volatility * Math.sqrt(tau));

                options.add(new HestonModelHelper(
                        optionMaturity, calendar, s0, strikePrice, vol,
                        riskFreeTS, dividendTS));
            }
        }

        for (double sigma = 0.1; sigma < 0.7; sigma += 0.2) {
            double v0 = 0.01;
            double kappa = 0.2;
            double theta = 0.02;
            double rho = -0.75;

            HestonProcess process = new HestonProcess(
                    riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);

            HestonModel model = new HestonModel(process);
            PricingEngine engine = new AnalyticHestonEngine(model, 96);

            for (CalibrationHelper option : options)
                ((BlackCalibrationHelper) option).setPricingEngine(engine);

            LevenbergMarquardt om = new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
            model.calibrate(options, om, new EndCriteria(400, 40,
                    1.0e-8, 1.0e-8, 1.0e-8));

            double tolerance = 3.0e-3;

            if (model.sigma() > tolerance) {
                boostErrors.add("Failed to reproduce expected sigma"
                        + "\n    calculated: " + model.sigma()
                        + "\n    expected:   " + 0.0
                        + "\n    tolerance:  " + tolerance);
            }

            if (Math.abs(model.kappa()
                    * (model.theta() - volatility * volatility)) > tolerance) {
                boostErrors.add("Failed to reproduce expected theta"
                        + "\n    calculated: " + model.theta()
                        + "\n    expected:   " + volatility * volatility);
            }

            if (Math.abs(model.v0() - volatility * volatility) > tolerance) {
                boostErrors.add("Failed to reproduce expected v0"
                        + "\n    calculated: " + model.v0()
                        + "\n    expected:   " + volatility * volatility);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testDAXCalibration() {
        System.out.println(
                "Testing Heston model calibration using DAX volatility data...");

        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);

        CalibrationMarketData marketData = getDAXCalibrationMarketData();

        Handle<YieldTermStructure> riskFreeTS = marketData.riskFreeTS;
        Handle<YieldTermStructure> dividendTS = marketData.dividendYield;
        Handle<Quote> s0 = marketData.s0;

        List<CalibrationHelper> options = marketData.options;

        double v0 = 0.1;
        double kappa = 1.0;
        double theta = 0.1;
        double sigma = 0.5;
        double rho = -0.5;

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);

        HestonModel model = new HestonModel(process);

        PricingEngine[] engines = {
                new AnalyticHestonEngine(model, 64),
                new COSHestonEngine(model, 12, 75),
                new ExponentialFittingHestonEngine(model)
        };

        Array params = model.params();
        for (PricingEngine engine : engines) {
            model.setParams(params);
            for (CalibrationHelper option : options)
                ((BlackCalibrationHelper) option).setPricingEngine(engine);

            LevenbergMarquardt om = new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
            model.calibrate(options, om,
                    new EndCriteria(400, 40, 1.0e-8, 1.0e-8, 1.0e-8));

            double sse = 0;
            for (int i = 0; i < 13 * 8; ++i) {
                double diff = options.get(i).calibrationError() * 100.0;
                sse += diff * diff;
            }
            double expected = 177.2; // see article by A. Sepp.
            if (Math.abs(sse - expected) > 1.0) {
                fail("Failed to reproduce calibration error"
                        + "\n    calculated: " + sse
                        + "\n    expected:   " + expected);
            }
        }
    }

    @Test
    public void testAnalyticVsBlack() {
        System.out.println(
                "Testing analytic Heston engine against Black formula...");

        Date settlementDate = Settings.instance.evaluationDate().Date();

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = settlementDate.add(new Period(6, TimeUnit.MONTHS));

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 30);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.1, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.04, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(32.0), true);

        double v0 = 0.05;
        double kappa = 5.0;
        double theta = 0.05;
        double sigma = 1.0e-4;
        double rho = 0.0;

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);

        EuropeanOption option = new EuropeanOption(payoff, exercise);
        PricingEngine engine = new AnalyticHestonEngine(
                new HestonModel(process), 144);

        option.setPricingEngine(engine);
        double calculated = option.NPV();

        double yearFraction = dayCounter.yearFraction(settlementDate, exerciseDate);
        double forwardPrice = 32 * Math.exp((0.1 - 0.04) * yearFraction);
        double expected = BlackFormula.blackFormula(payoff.optionType(),
                payoff.strike(), forwardPrice, Math.sqrt(0.05 * yearFraction),
                1.0, 0.0) * Math.exp(-0.1 * yearFraction);
        double error = Math.abs(calculated - expected);
        double tolerance = 2.0e-7;
        if (error > tolerance) {
            fail("failed to reproduce Black price with AnalyticHestonEngine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        engine = new FdHestonVanillaEngine(
                new HestonModel(process), 200, 200, 100);
        option.setPricingEngine(engine);

        calculated = option.NPV();
        error = Math.abs(calculated - expected);
        tolerance = 1.0e-3;
        if (error > tolerance) {
            fail("failed to reproduce Black price with FdHestonVanillaEngine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testAnalyticVsCached() {
        System.out.println(
                "Testing analytic Heston engine against cached values...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);
        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2005);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 1.05);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.0225, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);
        double v0 = 0.1;
        double kappa = 3.16;
        double theta = 0.09;
        double sigma = 0.4;
        double rho = -0.2;

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        AnalyticHestonEngine engine = new AnalyticHestonEngine(
                new HestonModel(process), 64);

        option.setPricingEngine(engine);

        double expected1 = 0.0404774515;
        double calculated1 = option.NPV();
        double tolerance = 1.0e-8;

        if (Math.abs(calculated1 - expected1) > tolerance) {
            boostErrors.add("Failed to reproduce cached analytic price"
                    + "\n    calculated: " + calculated1
                    + "\n    expected:   " + expected1);
        }

        // reference values from www.wilmott.com, technical forum
        // search for "Heston or VG price check"

        double[] K = {0.9, 1.0, 1.1};
        double[] expected2 = {0.1330371, 0.0641016, 0.0270645};
        double[] calculated2 = new double[6];

        for (int i = 0; i < 6; ++i) {
            Date exerciseDate2 = new Date(8 + i / 3, Month.SEPTEMBER, 2005);

            StrikedTypePayoff payoff2 =
                    new PlainVanillaPayoff(Option.Type.Call, K[i % 3]);
            EuropeanExercise exercise2 = new EuropeanExercise(exerciseDate2);

            Handle<YieldTermStructure> riskFreeTS2 =
                    new Handle<>(flatRate(0.05, dayCounter), true);
            Handle<YieldTermStructure> dividendTS2 =
                    new Handle<>(flatRate(0.02, dayCounter), true);

            double s = riskFreeTS2.currentLink().discount(0.7)
                    / dividendTS2.currentLink().discount(0.7);
            Handle<Quote> s02 = new Handle<>(new SimpleQuote(s), true);

            HestonProcess process2 = new HestonProcess(
                    riskFreeTS2, dividendTS2, s02, 0.09, 1.2, 0.08, 1.8, -0.45);

            EuropeanOption option2 = new EuropeanOption(payoff2, exercise2);

            PricingEngine engine2 = new AnalyticHestonEngine(
                    new HestonModel(process2));

            option2.setPricingEngine(engine2);
            calculated2[i] = option2.NPV();
        }

        // we are after the value for T=0.7
        double t1 = dayCounter.yearFraction(settlementDate,
                new Date(8, Month.SEPTEMBER, 2005));
        double t2 = dayCounter.yearFraction(settlementDate,
                new Date(9, Month.SEPTEMBER, 2005));

        for (int i = 0; i < 3; ++i) {
            double interpolated =
                    calculated2[i] + (calculated2[i + 3] - calculated2[i])
                            / (t2 - t1) * (0.7 - t1);

            if (Math.abs(interpolated - expected2[i]) > 100 * tolerance) {
                boostErrors.add("Failed to reproduce cached analytic prices:"
                        + "\n    calculated: " + interpolated
                        + "\n    expected:   " + expected2[i]);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testMcVsCached() {
        System.out.println(
                "Testing Monte Carlo Heston engine against cached values...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2005);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 1.05);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.7, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.4, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.05), true);

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.3, 1.16, 0.2, 0.8, 0.8,
                HestonProcess.Discretization.QuadraticExponentialMartingale);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        PricingEngine engine = new MakeMCEuropeanHestonEngine(process)
                .withStepsPerYear(11)
                .withAntitheticVariate()
                .withSamples(50000)
                .withSeed(1234)
                .build();

        option.setPricingEngine(engine);

        double expected = 0.0632851308977151;
        double calculated = option.NPV();
        double errorEstimate = option.errorEstimate();
        double tolerance = 7.5e-4;

        if (Math.abs(calculated - expected) > 2.34 * errorEstimate) {
            boostErrors.add("Failed to reproduce cached price"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + " +/- " + errorEstimate);
        }

        if (errorEstimate > tolerance) {
            boostErrors.add("failed to reproduce error estimate"
                    + "\n    calculated: " + errorEstimate
                    + "\n    expected:   " + tolerance);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testKahlJaeckelCase() {
        System.out.println(
                "Testing MC and FD Heston engines for the Kahl-Jaeckel example...");

        /* Example taken from Wilmott mag (Sept. 2005).
           "Not-so-complex logarithms in the Heston model",
           Example was also discussed within the Wilmott thread
           "QuantLib code is very high quatlity"
        */

        Date settlementDate = new Date(30, Month.MARCH, 2007);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(30, Month.MARCH, 2017);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 200);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.0, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.0, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100), true);

        double v0 = 0.16;
        double theta = v0;
        double kappa = 1.0;
        double sigma = 2.0;
        double rho = -0.8;

        HestonProcessDiscretizationDesc[] descriptions = {
                new HestonProcessDiscretizationDesc(
                        HestonProcess.Discretization.NonCentralChiSquareVariance,
                        10, "NonCentralChiSquareVariance"),
                new HestonProcessDiscretizationDesc(
                        HestonProcess.Discretization.QuadraticExponentialMartingale,
                        100, "QuadraticExponentialMartingale")
        };

        double tolerance = 0.2;
        double expected = 4.95212;

        for (HestonProcessDiscretizationDesc description : descriptions) {
            HestonProcess process = new HestonProcess(
                    riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho,
                    description.discretization);

            PricingEngine engine = new MakeMCEuropeanHestonEngine(process)
                    .withSteps(description.nSteps)
                    .withAntitheticVariate()
                    .withAbsoluteTolerance(tolerance)
                    .withSeed(1234)
                    .build();
            option.setPricingEngine(engine);

            double calculated = option.NPV();
            double errorEstimate = option.errorEstimate();

            if (Math.abs(calculated - expected) > 2.34 * errorEstimate) {
                boostErrors.add("Failed to reproduce cached price with MC engine"
                        + "\n    discretization: " + description.name
                        + "\n    expected:       " + expected
                        + "\n    calculated:     " + calculated
                        + " +/- " + errorEstimate);
            }

            if (errorEstimate > tolerance) {
                boostErrors.add(
                        "failed to reproduce error estimate with MC engine"
                                + "\n    discretization: " + description.name
                                + "\n    calculated    : " + errorEstimate
                                + "\n    expected      :   " + tolerance);
            }
        }

        HestonProcessDiscretizationDesc[] qmcDescriptions = {
                new HestonProcessDiscretizationDesc(
                        HestonProcess.Discretization.BroadieKayaExactSchemeLaguerre,
                        1, "BroadieKayaExactSchemeLaguerre"),
                new HestonProcessDiscretizationDesc(
                        HestonProcess.Discretization.BroadieKayaExactSchemeTrapezoidal,
                        1, "BroadieKayaExactSchemeTrapezoidal"),
                new HestonProcessDiscretizationDesc(
                        HestonProcess.Discretization.BroadieKayaExactSchemeLobatto,
                        1, "BroadieKayaExactSchemeLobatto"),
        };

        for (HestonProcessDiscretizationDesc description : qmcDescriptions) {
            option.setPricingEngine(
                    new MakeMCEuropeanHestonEngine(
                            new HestonProcess(
                                    riskFreeTS, dividendTS, s0, v0, kappa,
                                    theta, sigma, rho,
                                    description.discretization),
                            RngType.LOW_DISCREPANCY)
                            .withSteps(description.nSteps)
                            .withSamples(1023)
                            .build());

            double calculated = option.NPV();
            if (Math.abs(calculated - expected) > 0.5 * tolerance) {
                boostErrors.add(
                        "Failed to reproduce cached price with MC engine"
                                + "\n    discretization: " + description.name
                                + "\n    calculated:     " + calculated
                                + "\n    expected:       " + expected
                                + "\n    tolerance:      " + 0.5 * tolerance);
            }
        }

        HestonModel hestonModel = new HestonModel(
                new HestonProcess(
                        riskFreeTS, dividendTS, s0, v0,
                        kappa, theta, sigma, rho));

        option.setPricingEngine(
                new FdHestonVanillaEngine(hestonModel, 200, 401, 101));

        double calculated = option.NPV();
        double error = Math.abs(calculated - expected);
        if (error > 5.0e-2) {
            fail("failed to reproduce cached price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        option.setPricingEngine(
                new AnalyticHestonEngine(hestonModel, 1e-6, 1000));

        calculated = option.NPV();
        error = Math.abs(calculated - expected);

        if (error > 0.00002) {
            fail("failed to reproduce cached price with "
                    + "GaussLobatto engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        option.setPricingEngine(
                new COSHestonEngine(hestonModel, 16, 400));
        calculated = option.NPV();
        error = Math.abs(calculated - expected);

        if (error > 0.00002) {
            fail("failed to reproduce cached price with "
                    + "Cosine engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        option.setPricingEngine(
                new ExponentialFittingHestonEngine(hestonModel));
        calculated = option.NPV();
        error = Math.abs(calculated - expected);

        if (error > 0.00002) {
            fail("failed to reproduce cached price with "
                    + "exponential fitting Heston engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testFdBarrierVsCached() {
        System.out.println(
                "Testing FD barrier Heston engine against cached values...");

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, 0.08, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, 0.04, dc), true);

        Date exDate = today.add(180);
        EuropeanExercise exercise = new EuropeanExercise(exDate);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 90.0);

        HestonProcess process = new HestonProcess(
                rTS, qTS, s0, 0.25 * 0.25, 1.0, 0.25 * 0.25, 0.001, 0.0);

        PricingEngine engine = new FdHestonBarrierEngine(
                new HestonModel(process), 200, 400, 100);

        BarrierOption option = new BarrierOption(
                Barrier.Type.DownOut, 95.0, 3.0, payoff, exercise);
        option.setPricingEngine(engine);

        double calculated = option.NPV();
        double expected = 9.0246;
        double error = Math.abs(calculated - expected);
        if (error > 1.0e-3) {
            fail("failed to reproduce cached price with FD Barrier engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }

        option = new BarrierOption(
                Barrier.Type.DownIn, 95.0, 3.0, payoff, exercise);
        option.setPricingEngine(engine);

        calculated = option.NPV();
        expected = 7.7627;
        error = Math.abs(calculated - expected);
        if (error > 1.0e-3) {
            fail("failed to reproduce cached price with FD Barrier engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testFdVanillaVsCached() {
        System.out.println(
                "Testing FD vanilla Heston engine against cached values...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2005);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 1.05);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.7, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.4, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.05), true);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.3, 1.16, 0.2, 0.8, 0.8);

        option.setPricingEngine(
                new MakeFdHestonVanillaEngine(new HestonModel(process))
                        .withTGrid(100)
                        .withXGrid(200)
                        .withVGrid(100)
                        .build());

        double expected = 0.06325;
        double calculated = option.NPV();
        double error = Math.abs(calculated - expected);
        double tolerance = 1.0e-4;

        if (error > tolerance) {
            fail("failed to reproduce cached price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testFdVanillaWithDividendsVsCached() {
        System.out.println(
                "Testing FD vanilla Heston engine for discrete dividends...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 95.0);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.05, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.0, dayCounter), true);

        Date exerciseDate = new Date(28, Month.MARCH, 2006);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        List<Date> dividendDates = new ArrayList<>();
        List<Double> dividendAmounts = new ArrayList<>();
        for (Date d = settlementDate.add(new Period(3, TimeUnit.MONTHS));
             d.serialNumber() < exercise.lastDate().serialNumber();
             d = d.add(new Period(6, TimeUnit.MONTHS))) {
            dividendDates.add(d);
            dividendAmounts.add(1.0);
        }
        double[] dividends = new double[dividendAmounts.size()];
        for (int i = 0; i < dividends.length; ++i)
            dividends[i] = dividendAmounts.get(i);

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.04, 1.0, 0.04, 0.001, 0.0);

        EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(
                new MakeFdHestonVanillaEngine(new HestonModel(process))
                        .withCashDividends(dividendDates, dividends)
                        .withTGrid(200)
                        .withXGrid(400)
                        .withVGrid(100)
                        .build());

        double calculated = option.NPV();
        // Value calculated with an independent FD framework, validated with
        // an independent MC framework
        double expected = 12.946;
        double error = Math.abs(calculated - expected);
        double tolerance = 5.0e-3;

        if (error > tolerance) {
            fail("failed to reproduce discrete dividend price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testFdAmerican() {
        System.out.println(
                "Testing FD vanilla Heston engine for american exercise...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.05, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.03, dayCounter), true);
        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.04, 1.0, 0.04, 0.001, 0.0);
        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 95.0);
        Date exerciseDate = new Date(28, Month.MARCH, 2006);
        AmericanExercise exercise =
                new AmericanExercise(settlementDate, exerciseDate);
        EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(
                new MakeFdHestonVanillaEngine(new HestonModel(process))
                        .withTGrid(200)
                        .withXGrid(400)
                        .withVGrid(100)
                        .build());
        double calculated = option.NPV();

        Handle<BlackVolTermStructure> volTS = new Handle<>(
                flatVol(settlementDate, 0.2, dayCounter), true);
        BlackScholesMertonProcess refProcess =
                new BlackScholesMertonProcess(
                        s0, dividendTS, riskFreeTS, volTS);
        PricingEngine refEngine =
                new FdBlackScholesVanillaEngine(refProcess, 200, 400);
        option.setPricingEngine(refEngine);
        double expected = option.NPV();

        double error = Math.abs(calculated - expected);
        double tolerance = 1.0e-3;

        if (error > tolerance) {
            fail("failed to reproduce american option price with FD engine"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    error:      " + error);
        }
    }

    @Test
    public void testMultipleStrikesEngine() {
        System.out.println(
                "Testing multiple-strikes FD Heston engine...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2006);

        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.06, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.05), true);

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.16, 2.5, 0.09, 0.8, -0.8);
        HestonModel model = new HestonModel(process);

        List<Double> strikes = List.of(1.0, 0.5, 0.75, 1.5, 2.0);

        FdHestonVanillaEngine singleStrikeEngine =
                new FdHestonVanillaEngine(model, 20, 400, 50);
        FdHestonVanillaEngine multiStrikeEngine =
                new FdHestonVanillaEngine(model, 20, 400, 50);
        multiStrikeEngine.enableMultipleStrikesCaching(strikes);

        double relTol = 5e-3;
        for (double strike : strikes) {
            StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Put, strike);

            EuropeanOption aOption = new EuropeanOption(payoff, exercise);
            aOption.setPricingEngine(multiStrikeEngine);

            double npvCalculated = aOption.NPV();
            double deltaCalculated = aOption.delta();
            double gammaCalculated = aOption.gamma();
            double thetaCalculated = aOption.theta();

            aOption.setPricingEngine(singleStrikeEngine);
            double npvExpected = aOption.NPV();
            double deltaExpected = aOption.delta();
            double gammaExpected = aOption.gamma();
            double thetaExpected = aOption.theta();

            if (Math.abs(npvCalculated - npvExpected) / npvExpected > relTol) {
                fail("failed to reproduce price with FD multi strike engine"
                        + "\n    calculated: " + npvCalculated
                        + "\n    expected:   " + npvExpected
                        + "\n    error:      " + relTol);
            }
            if (Math.abs(deltaCalculated - deltaExpected) / deltaExpected > relTol) {
                fail("failed to reproduce delta with FD multi strike engine"
                        + "\n    calculated: " + deltaCalculated
                        + "\n    expected:   " + deltaExpected
                        + "\n    error:      " + relTol);
            }
            if (Math.abs(gammaCalculated - gammaExpected) / gammaExpected > relTol) {
                fail("failed to reproduce gamma with FD multi strike engine"
                        + "\n    calculated: " + gammaCalculated
                        + "\n    expected:   " + gammaExpected
                        + "\n    error:      " + relTol);
            }
            if (Math.abs(thetaCalculated - thetaExpected) / thetaExpected > relTol) {
                fail("failed to reproduce theta with FD multi strike engine"
                        + "\n    calculated: " + thetaCalculated
                        + "\n    expected:   " + thetaExpected
                        + "\n    error:      " + relTol);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers for the remaining official test cases.
    // ------------------------------------------------------------------

    private static class HestonParameter {
        final double v0, kappa, theta, sigma, rho;

        HestonParameter(double v0, double kappa, double theta,
                        double sigma, double rho) {
            this.v0 = v0;
            this.kappa = kappa;
            this.theta = theta;
            this.sigma = sigma;
            this.rho = rho;
        }
    }

    /**
     * Official {@code LogCharacteristicFunction}: the n-th root of the
     * characteristic function, used to recover cumulants by numerical
     * differentiation.
     */
    private static class LogCharacteristicFunction implements Function {
        private final double t_;
        private final Complex alpha_;
        private final COSHestonEngine engine_;

        LogCharacteristicFunction(int n, double t, COSHestonEngine engine) {
            t_ = t;
            engine_ = engine;
            // Official starts with alpha_ = (0, 1) and multiplies by i for
            // i = 1 .. n-1, i.e. alpha_ = i^n.
            Complex alpha = Complex.I;
            for (int i = 1; i < n; ++i)
                alpha = alpha.multiply(Complex.I);
            alpha_ = alpha;
        }

        @Override
        public double value(double u) {
            return engine_.chF(u, t_).log().divide(alpha_).getReal();
        }
    }

    /**
     * Official {@code HestonIntegrationMaxBoundTestFct}: the integration
     * upper bound is supplied as a callable, and the number of calls is
     * counted.
     */
    private static class HestonIntegrationMaxBoundTestFct
            implements java.util.function.DoubleSupplier {
        private final double maxBound_;
        private int callCounter_ = 0;

        HestonIntegrationMaxBoundTestFct(double maxBound) {
            maxBound_ = maxBound;
        }

        @Override
        public double getAsDouble() {
            ++callCounter_;
            return maxBound_;
        }

        int getCallCounter() {
            return callCounter_;
        }
    }

    /**
     * Official {@code reportOnIntegrationMethodTest}. A null
     * {@code valuations} (-1) means "do not check the number of
     * evaluations", matching {@code Null<Size>()}.
     */
    private void reportOnIntegrationMethodTest(
            EuropeanOption option, HestonModel model,
            FourierIntegration integration,
            AnalyticHestonEngine.ComplexLogFormula formula,
            boolean isAdaptive, double expected, double tol,
            int valuations, String method) {

        if (integration.isAdaptiveIntegration() != isAdaptive) {
            boostErrors.add(method + " is not an adaptive integration routine");
        }

        AnalyticHestonEngine engine = new AnalyticHestonEngine(
                model, formula, integration, 1e-9);

        option.setPricingEngine(engine);
        double calculated = option.NPV();

        double error = Math.abs(calculated - expected);

        if (Double.isNaN(error) || error > tol) {
            boostErrors.add("failed to reproduce simple Heston Pricing with "
                    + "\n    integration method: " + method
                    + "\n    expected          : " + expected
                    + "\n    calculated        : " + calculated
                    + "\n    error             : " + error);
        }

        if (valuations != -1 && valuations != engine.numberOfEvaluations()) {
            boostErrors.add(
                    "nubmer of function evaluations does not match "
                            + "\n    integration method      : " + method
                            + "\n    expected function calls : " + valuations
                            + "\n    number of function calls: "
                            + engine.numberOfEvaluations());
        }
    }

    @Test
    public void testDifferentIntegrals() {
        System.out.println(
                "Testing different numerical Heston integration algorithms...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.05, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.03, dayCounter), true);

        double[] strikes = {0.5, 0.7, 1.0, 1.25, 1.5, 2.0};
        int[] maturities = {1, 2, 3, 12, 60, 120, 360};
        Option.Type[] types = {Option.Type.Put, Option.Type.Call};

        HestonParameter equityfx = new HestonParameter(0.07, 2.0, 0.04, 0.55, -0.8);
        HestonParameter highCorr = new HestonParameter(0.07, 1.0, 0.04, 0.55, 0.995);
        HestonParameter lowVolOfVol = new HestonParameter(0.07, 1.0, 0.04, 0.025, -0.75);
        HestonParameter highVolOfVol = new HestonParameter(0.07, 1.0, 0.04, 5.0, -0.75);
        HestonParameter kappaEqSigRho = new HestonParameter(0.07, 0.4, 0.04, 0.5, 0.8);

        HestonParameter[] params = {
                equityfx, highCorr, lowVolOfVol, highVolOfVol, kappaEqSigRho
        };
        double[] tol = {1e-3, 1e-3, 0.2, 0.01, 1e-3};

        for (int p = 0; p < params.length; ++p) {
            HestonParameter parameter = params[p];
            Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);

            HestonProcess process = new HestonProcess(
                    riskFreeTS, dividendTS, s0, parameter.v0,
                    parameter.kappa, parameter.theta,
                    parameter.sigma, parameter.rho);

            HestonModel model = new HestonModel(process);

            AnalyticHestonEngine lobattoEngine =
                    new AnalyticHestonEngine(model, 1e-10, 1000000);
            AnalyticHestonEngine laguerreEngine =
                    new AnalyticHestonEngine(model, 128);
            AnalyticHestonEngine legendreEngine = new AnalyticHestonEngine(
                    model, AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                    FourierIntegration.gaussLegendre(512));
            AnalyticHestonEngine chebyshevEngine = new AnalyticHestonEngine(
                    model, AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                    FourierIntegration.gaussChebyshev(512));
            AnalyticHestonEngine chebyshev2ndEngine = new AnalyticHestonEngine(
                    model, AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                    FourierIntegration.gaussChebyshev2nd(512));

            double maxLegendreDiff = 0.0;
            double maxChebyshevDiff = 0.0;
            double maxChebyshev2ndDiff = 0.0;
            double maxLaguerreDiff = 0.0;

            for (int maturity : maturities) {
                EuropeanExercise exercise = new EuropeanExercise(
                        settlementDate.add(new Period(maturity, TimeUnit.MONTHS)));

                for (double strike : strikes) {
                    for (Option.Type type : types) {
                        StrikedTypePayoff payoff =
                                new PlainVanillaPayoff(type, strike);
                        EuropeanOption option =
                                new EuropeanOption(payoff, exercise);

                        option.setPricingEngine(lobattoEngine);
                        double lobattoNPV = option.NPV();

                        option.setPricingEngine(laguerreEngine);
                        double laguerre = option.NPV();

                        option.setPricingEngine(legendreEngine);
                        double legendre = option.NPV();

                        option.setPricingEngine(chebyshevEngine);
                        double chebyshev = option.NPV();

                        option.setPricingEngine(chebyshev2ndEngine);
                        double chebyshev2nd = option.NPV();

                        maxLaguerreDiff = Math.max(maxLaguerreDiff,
                                Math.abs(lobattoNPV - laguerre));
                        maxLegendreDiff = Math.max(maxLegendreDiff,
                                Math.abs(lobattoNPV - legendre));
                        maxChebyshevDiff = Math.max(maxChebyshevDiff,
                                Math.abs(lobattoNPV - chebyshev));
                        maxChebyshev2ndDiff = Math.max(maxChebyshev2ndDiff,
                                Math.abs(lobattoNPV - chebyshev2nd));
                    }
                }
            }

            double maxDiff = Math.max(
                    Math.max(maxLaguerreDiff, maxLegendreDiff),
                    Math.max(maxChebyshevDiff, maxChebyshev2ndDiff));

            double tr = tol[p];
            if (maxDiff > tr) {
                boostErrors.add(
                        "Failed to reproduce Heston pricing values "
                                + "within given tolerance"
                                + "\n    maxDifference: " + maxDiff
                                + "\n    tolerance:     " + tr);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAnalyticPiecewiseTimeDependent() {
        System.out.println(
                "Testing analytic piecewise time dependent Heston prices...");

        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);
        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2005);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 1.0);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        List<Date> dates = List.of(settlementDate,
                new Date(1, Month.JANUARY, 2007));
        List<Double> irates = List.of(0.0, 0.2);
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(new ZeroCurve(dates, irates, dayCounter), true);

        List<Double> qrates = List.of(0.0, 0.3);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(new ZeroCurve(dates, qrates, dayCounter), true);

        double v0 = 0.1;
        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);

        ConstantParameter theta = new ConstantParameter(
                0.09, new PositiveConstraint());
        ConstantParameter kappa = new ConstantParameter(
                3.16, new PositiveConstraint());
        ConstantParameter sigma = new ConstantParameter(
                4.40, new PositiveConstraint());
        ConstantParameter rho = new ConstantParameter(
                -0.8, new BoundaryConstraint(-1.0, 1.0));

        PiecewiseTimeDependentHestonModel model =
                new PiecewiseTimeDependentHestonModel(
                        riskFreeTS, dividendTS, s0, v0,
                        theta, kappa, sigma, rho, new TimeGrid(20.0, 2));

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        HestonProcess hestonProcess = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0,
                kappa.value(0.0), theta.value(0.0),
                sigma.value(0.0), rho.value(0.0));
        HestonModel hestonModel = new HestonModel(hestonProcess);
        option.setPricingEngine(new AnalyticHestonEngine(hestonModel));

        double expected = option.NPV();

        option.setPricingEngine(new AnalyticPTDHestonEngine(model, 192));

        double calculatedGatheral = option.NPV();
        if (Math.abs(calculatedGatheral - expected) > 1e-7) {
            boostErrors.add(
                    "failed to reproduce Heston prices with Gatheral ChF"
                            + "\n    calculated: " + calculatedGatheral
                            + "\n    expected:   " + expected);
        }

        option.setPricingEngine(new AnalyticPTDHestonEngine(
                model,
                AnalyticPTDHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                FourierIntegration.gaussLobatto(
                        1e-12, NULL_REAL, 100000)));
        double calculatedAndersenPiterbarg = option.NPV();

        if (Math.abs(calculatedAndersenPiterbarg - expected) > 1e-9) {
            boostErrors.add(
                    "failed to reproduce Heston prices Andersen-Piterbarg"
                            + "\n    calculated: " + calculatedAndersenPiterbarg
                            + "\n    expected:   " + expected);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testDAXCalibrationOfTimeDependentModel() {
        System.out.println(
                "Testing time-dependent Heston model calibration...");

        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);

        CalibrationMarketData marketData = getDAXCalibrationMarketData();

        Handle<YieldTermStructure> riskFreeTS = marketData.riskFreeTS;
        Handle<YieldTermStructure> dividendTS = marketData.dividendYield;
        Handle<Quote> s0 = marketData.s0;

        List<CalibrationHelper> options = marketData.options;

        List<Double> modelTimes = List.of(0.25, 10.0);
        TimeGrid modelGrid = new TimeGrid(modelTimes);

        double v0 = 0.1;
        ConstantParameter sigma = new ConstantParameter(
                0.5, new PositiveConstraint());
        ConstantParameter theta = new ConstantParameter(
                0.1, new PositiveConstraint());
        ConstantParameter rho = new ConstantParameter(
                -0.5, new BoundaryConstraint(-1.0, 1.0));

        List<Double> pTimes = List.of(0.25);
        PiecewiseConstantParameter kappa = new PiecewiseConstantParameter(
                pTimes, new PositiveConstraint());

        for (int i = 0; i < pTimes.size() + 1; ++i) {
            kappa.setParam(i, 10.0);
        }

        PiecewiseTimeDependentHestonModel model =
                new PiecewiseTimeDependentHestonModel(
                        riskFreeTS, dividendTS, s0, v0,
                        theta, kappa, sigma, rho, modelGrid);

        PricingEngine[] engines = {
                new AnalyticPTDHestonEngine(model),
                new AnalyticPTDHestonEngine(
                        model,
                        AnalyticPTDHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.gaussLaguerre(64)),
                new AnalyticPTDHestonEngine(
                        model,
                        AnalyticPTDHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.discreteTrapezoid(72))
        };

        for (PricingEngine engine : engines) {
            for (CalibrationHelper option : options)
                ((BlackCalibrationHelper) option).setPricingEngine(engine);

            LevenbergMarquardt om = new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
            model.calibrate(options, om,
                    new EndCriteria(400, 40, 1.0e-8, 1.0e-8, 1.0e-8));

            double sse = 0;
            for (int i = 0; i < 13 * 8; ++i) {
                double diff = options.get(i).calibrationError() * 100.0;
                sse += diff * diff;
            }

            double expected = 74.4;
            if (Math.abs(sse - expected) > 1.0) {
                boostErrors.add(
                        "Failed to reproduce calibration error"
                                + "\n    calculated: " + sse
                                + "\n    expected:   " + expected);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAlanLewisReferencePrices() {
        System.out.println("Testing Alan Lewis reference prices...");

        /*
         * testing Alan Lewis reference prices posted in
         * http://wilmott.com/messageview.cfm?catid=34&threadid=90957
         */

        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);

        Date maturityDate = new Date(5, Month.JULY, 2003);
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.01, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.04;
        double rho = -0.5;
        double sigma = 1.0;
        double kappa = 4.0;
        double theta = 0.25;

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);
        HestonModel model = new HestonModel(process);

        PricingEngine laguerreEngine =
                new AnalyticHestonEngine(model, 128);

        PricingEngine gaussLobattoEngine =
                new AnalyticHestonEngine(model, QL_EPSILON, 100000);

        PricingEngine cosEngine =
                new COSHestonEngine(model, 20, 400);

        PricingEngine exponentialFittingEngine =
                new ExponentialFittingHestonEngine(model);

        PricingEngine andersenPiterbargEngine = new AnalyticHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                FourierIntegration.gaussLobatto(
                        NULL_REAL, 1e-14, 1000000),
                QL_EPSILON);

        PricingEngine angledContourEngine = new AnalyticHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                FourierIntegration.gaussLobatto(
                        NULL_REAL, 1e-14, 1000000),
                QL_EPSILON);

        PricingEngine optimalCvEngine = new AnalyticHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.OptimalCV,
                FourierIntegration.gaussLobatto(
                        NULL_REAL, 1e-14, 1000000),
                QL_EPSILON);

        double[] strikes = {80, 90, 100, 110, 120};
        Option.Type[] types = {Option.Type.Put, Option.Type.Call};
        PricingEngine[] engines = {
                laguerreEngine, gaussLobattoEngine,
                cosEngine, andersenPiterbargEngine, exponentialFittingEngine,
                angledContourEngine, optimalCvEngine
        };

        double[][] expectedResults = {
                {7.958878113256768285213263077598987193482161301733,
                        26.774758743998854221382195325726949201687074848341},
                {12.017966707346304987709573290236471654992071308187,
                        20.933349000596710388139445766564068085476194042256},
                {17.055270961270109413522653999411000974895436309183,
                        16.070154917028834278213466703938231827658768230714},
                {23.017825898442800538908781834822560777763225722188,
                        12.132211516709844867860534767549426052805766831181},
                {29.811026202682471843340682293165857439167301370697,
                        9.024913483457835636553375454092357136489051667150}
        };

        double tol = 1e-12; // 3e-15 works on linux/ia32,
        // but keep some buffer for other platforms

        for (int i = 0; i < strikes.length; ++i) {
            double strike = strikes[i];

            for (int j = 0; j < types.length; ++j) {
                Option.Type type = types[j];

                for (int k = 0; k < engines.length; ++k) {
                    PricingEngine engine = engines[k];

                    StrikedTypePayoff payoff =
                            new PlainVanillaPayoff(type, strike);

                    EuropeanOption option =
                            new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    double expected = expectedResults[i][j];
                    double calculated = option.NPV();
                    double relError = Math.abs(calculated - expected) / expected;

                    if (relError > tol || Double.isNaN(calculated)) {
                        boostErrors.add(
                                "failed to reproduce Alan Lewis Reference prices "
                                        + "\n    strike     : " + strike
                                        + "\n    option type: " + type
                                        + "\n    engine type: " + k
                                        + "\n    rel. error : " + relError);
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testExpansionOnAlanLewisReference() {
        System.out.println(
                "Testing expansion on Alan Lewis reference prices...");

        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);

        Date maturityDate = new Date(5, Month.JULY, 2003);
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.01, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.04;
        double rho = -0.5;
        double sigma = 1.0;
        double kappa = 4.0;
        double theta = 0.25;

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho);
        HestonModel model = new HestonModel(process);

        PricingEngine lpp2Engine = new HestonExpansionEngine(
                model, HestonExpansionEngine.HestonExpansionFormula.LPP2);
        // don't test Forde as it does not behave well on this example
        PricingEngine lpp3Engine = new HestonExpansionEngine(
                model, HestonExpansionEngine.HestonExpansionFormula.LPP3);

        double[] strikes = {80, 90, 100, 110, 120};
        Option.Type[] types = {Option.Type.Put, Option.Type.Call};
        PricingEngine[] engines = {lpp2Engine, lpp3Engine};

        double[][] expectedResults = {
                {7.958878113256768285213263077598987193482161301733,
                        26.774758743998854221382195325726949201687074848341},
                {12.017966707346304987709573290236471654992071308187,
                        20.933349000596710388139445766564068085476194042256},
                {17.055270961270109413522653999411000974895436309183,
                        16.070154917028834278213466703938231827658768230714},
                {23.017825898442800538908781834822560777763225722188,
                        12.132211516709844867860534767549426052805766831181},
                {29.811026202682471843340682293165857439167301370697,
                        9.024913483457835636553375454092357136489051667150}
        };

        double[] tol = {1.003e-2, 3.645e-3};

        for (int i = 0; i < strikes.length; ++i) {
            double strike = strikes[i];

            for (int j = 0; j < types.length; ++j) {
                Option.Type type = types[j];

                for (int k = 0; k < engines.length; ++k) {
                    PricingEngine engine = engines[k];

                    StrikedTypePayoff payoff =
                            new PlainVanillaPayoff(type, strike);

                    EuropeanOption option =
                            new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    double expected = expectedResults[i][j];
                    double calculated = option.NPV();
                    double relError = Math.abs(calculated - expected) / expected;

                    if (relError > tol[k]) {
                        boostErrors.add(
                                "failed to reproduce Alan Lewis Reference prices "
                                        + "\n    strike     : " + strike
                                        + "\n    option type: " + type
                                        + "\n    engine type: " + k
                                        + "\n    rel. error : " + relError);
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testExpansionOnFordeReference() {
        System.out.println(
                "Testing expansion on Forde reference prices...");

        double forward = 100.0;
        double v0 = 0.04;
        double rho = -0.4;
        double sigma = 0.2;
        double kappa = 1.15;
        double theta = 0.04;

        double[] terms = {0.1, 1.0, 5.0, 10.0};
        double[] strikes = {60, 80, 90, 100, 110, 120, 140};

        double[][] referenceVols = {
                {0.27284673574924445, 0.22360758200372477, 0.21023988547031242,
                        0.1990674789471587, 0.19118230678920461,
                        0.18721342919371017, 0.1899869903378507},
                {0.25200775151345, 0.2127275920953156, 0.20286528150874591,
                        0.19479398358151515, 0.18872591728967686,
                        0.18470857955411824, 0.18204457060905446},
                {0.21637821506229973, 0.20077227130455172, 0.19721753043236154,
                        0.1942233023784151, 0.191693211401571,
                        0.18955229722896752, 0.18491727548069495},
                {0.20672925973965342, 0.198583062164427, 0.19668274423922746,
                        0.1950420231354201, 0.193610364344706,
                        0.1923502827886502, 0.18934360917857015}
        };

        double[][] tol = {
                {0.06, 0.03, 0.03, 0.02},
                {0.15, 0.08, 0.04, 0.02},
                {0.06, 0.08, 1.0, 1.0} // forde breaks down for long maturities
        };
        double[][] tolAtm = {
                {4e-6, 7e-4, 2e-3, 9e-4},
                {7e-6, 4e-4, 9e-4, 4e-4},
                {4e-4, 3e-2, 0.28, 1.0}
        };

        for (int j = 0; j < terms.length; ++j) {
            double term = terms[j];
            HestonExpansionEngine.HestonExpansion lpp2 =
                    new HestonExpansionEngine.LPP2HestonExpansion(
                            kappa, theta, sigma, v0, rho, term);
            HestonExpansionEngine.HestonExpansion lpp3 =
                    new HestonExpansionEngine.LPP3HestonExpansion(
                            kappa, theta, sigma, v0, rho, term);
            HestonExpansionEngine.HestonExpansion forde =
                    new HestonExpansionEngine.FordeHestonExpansion(
                            kappa, theta, sigma, v0, rho, term);
            HestonExpansionEngine.HestonExpansion[] expansions =
                    {lpp2, lpp3, forde};

            for (int i = 0; i < strikes.length; ++i) {
                double strike = strikes[i];
                for (int k = 0; k < expansions.length; ++k) {
                    HestonExpansionEngine.HestonExpansion expansion =
                            expansions[k];

                    double expected = referenceVols[j][i];
                    double calculated =
                            expansion.impliedVolatility(strike, forward);
                    double relError = Math.abs(calculated - expected) / expected;
                    double refTol = strike == forward
                            ? tolAtm[k][j] : tol[k][j];
                    if (relError > refTol) {
                        boostErrors.add(
                                "failed to reproduce Forde reference vols "
                                        + "\n    strike        : " + strike
                                        + "\n    expansion type: " + k
                                        + "\n    rel. error    : " + relError);
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAllIntegrationMethods() {
        System.out.println(
                "Testing semi-analytic Heston pricing with all "
                        + "integration methods...");

        Date settlementDate = new Date(7, Month.FEBRUARY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.05, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.075, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.75;
        double sigma = 0.4;
        double kappa = 4.0;
        double theta = 0.05;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        StrikedTypePayoff payoff = new PlainVanillaPayoff(
                Option.Type.Put, s0.currentLink().value());

        Date maturityDate = settlementDate.add(
                new Period(1, TimeUnit.YEARS));
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        double tol = 1e-8;
        double expected = 10.147041515497;

        // Gauss-Laguerre with Gatheral logarithm integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLaguerre(),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, tol, 256,
                "Gauss-Laguerre with Gatheral logarithm");

        // Gauss-Laguerre with branch correction integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLaguerre(),
                AnalyticHestonEngine.ComplexLogFormula.BranchCorrection,
                false, expected, tol, 256,
                "Gauss-Laguerre with branch correction");

        // Gauss-Laguerre with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLaguerre(),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, tol, 128,
                "Gauss-Laguerre with Andersen Piterbarg control variate");

        // Gauss-Legendre with Gatheral logarithm integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLegendre(),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, tol, 256,
                "Gauss-Legendre with Gatheral logarithm");

        // Gauss-Legendre with branch correction integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLegendre(),
                AnalyticHestonEngine.ComplexLogFormula.BranchCorrection,
                false, expected, tol, 256,
                "Gauss-Legendre with branch correction");

        // Gauss-Legendre with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLegendre(256),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, 1e-4, 256,
                "Gauss-Legendre with Andersen Piterbarg control variate");

        // Gauss-Chebyshev with Gatheral logarithm integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev(512),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, 1e-4, 1024,
                "Gauss-Chebyshev with Gatheral logarithm");

        // Gauss-Chebyshev with branch correction integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev(512),
                AnalyticHestonEngine.ComplexLogFormula.BranchCorrection,
                false, expected, 1e-4, 1024,
                "Gauss-Chebyshev with branch correction");

        // Gauss-Chebyshev with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev(512),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, 1e-4, 512,
                "Gauss-Laguerre with Andersen Piterbarg control variate");

        // Gauss-Chebyshev2nd with Gatheral logarithm integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev2nd(512),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, 2e-4, 1024,
                "Gauss-Chebyshev2nd with Gatheral logarithm");

        // Gauss-Chebyshev with branch correction integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev2nd(512),
                AnalyticHestonEngine.ComplexLogFormula.BranchCorrection,
                false, expected, 2e-4, 1024,
                "Gauss-Chebyshev2nd with branch correction");

        // Gauss-Chebyshev with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussChebyshev2nd(512),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, 2e-4, 512,
                "Gauss-Chebyshev2nd with Andersen Piterbarg control variate");

        // Discrete Simpson rule with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.discreteSimpson(512),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, tol, 1024,
                "Discrete Simpson rule with Gatheral logarithm");

        // Discrete Simpson rule with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.discreteSimpson(64),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, tol, 64,
                "Discrete Simpson rule with Andersen Piterbarg control variate");

        // Discrete Trapezoid rule with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.discreteTrapezoid(512),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                false, expected, 2e-4, 1024,
                "Discrete Trapezoid rule with Gatheral logarithm");

        // Discrete Trapezoid rule with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.discreteTrapezoid(64),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                false, expected, tol, 64,
                "Discrete Trapezoid rule with Andersen Piterbarg control variate");

        // Gauss-Lobatto with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLobatto(tol, NULL_REAL),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                true, expected, tol, -1,
                "Gauss-Lobatto with Gatheral logarithm");

        // Gauss-Lobatto with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLobatto(tol, NULL_REAL),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                true, expected, tol, -1,
                "Gauss-Lobatto with Andersen Piterbarg control variate");

        // Gauss-Konrod with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussKronrod(tol),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                true, expected, tol, -1,
                "Gauss-Konrod with Gatheral logarithm");

        // Gauss-Konrod with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussKronrod(tol),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                true, expected, tol, -1,
                "Gauss-Konrod with Andersen Piterbarg control variate");

        // Simpson with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.simpson(tol),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                true, expected, 1e-6, -1,
                "Simpson with Gatheral logarithm");

        // Simpson with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.simpson(tol),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                true, expected, 1e-6, -1,
                "Simpson with Andersen Piterbarg control variate");

        // Trapezoid with Gatheral logarithm
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.trapezoid(tol),
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                true, expected, 1e-6, -1,
                "Trapezoid with Gatheral logarithm");

        // Trapezoid with Andersen-Piterbarg integration method
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.trapezoid(tol),
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                true, expected, 1e-6, -1,
                "Trapezoid with Andersen Piterbarg control variate");

        // Angled contour shift integral
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLaguerre(),
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                false, expected, tol, 128,
                "Angled contour shift integral");

        // Angled contour shift integral w/o control variate
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.gaussLaguerre(192),
                AnalyticHestonEngine.ComplexLogFormula.AngledContourNoCV,
                false, expected, tol, 192,
                "Angled contour shift integral without control variate");

        // Angled contour shift integral with expSinh
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.expSinh(),
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                true, expected, 1e-8, -1,
                "exp-sinh integration with angled contour shift integral");

        // Angled contour shift integral with tanhSinh
        reportOnIntegrationMethodTest(option, model,
                FourierIntegration.tanhSinh(),
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                true, expected, 1e-8, -1,
                "tanh-sinh integration with angled contour shift integral");

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testCosHestonCumulants() {
        System.out.println("Testing Heston COS cumulants...");

        Date settlementDate = new Date(7, Month.FEBRUARY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.15, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.075, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.75;
        double sigma = 0.4;
        double kappa = 4.0;
        double theta = 0.25;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        COSHestonEngine cosEngine = new COSHestonEngine(model);

        double tol = 1e-7;

        for (double t = 0.01; t < 41.0; t += t) {
            double nc1 = new NumericalDifferentiation(
                    new LogCharacteristicFunction(1, t, cosEngine),
                    1, 1e-5, 5,
                    NumericalDifferentiation.Scheme.Central).value(0.0);

            double c1 = cosEngine.c1(t);

            if (Math.abs(nc1 - c1) > tol) {
                boostErrors.add(
                        " failed to reproduce first cumulant"
                                + "\n    expected:   " + nc1
                                + "\n    calculated: " + c1
                                + "\n    difference: " + Math.abs(nc1 - c1));
            }

            double nc2 = new NumericalDifferentiation(
                    new LogCharacteristicFunction(2, t, cosEngine),
                    2, 1e-2, 5,
                    NumericalDifferentiation.Scheme.Central).value(0.0);

            double c2 = cosEngine.c2(t);

            if (Math.abs(nc2 - c2) > tol) {
                boostErrors.add(
                        " failed to reproduce second cumulant"
                                + "\n    expected:   " + nc2
                                + "\n    calculated: " + c2
                                + "\n    difference: " + Math.abs(nc2 - c2));
            }

            double nc3 = new NumericalDifferentiation(
                    new LogCharacteristicFunction(3, t, cosEngine),
                    3, 5e-3, 7,
                    NumericalDifferentiation.Scheme.Central).value(0.0);

            double c3 = cosEngine.c3(t);

            if (Math.abs(nc3 - c3) > tol) {
                boostErrors.add(
                        " failed to reproduce third cumulant"
                                + "\n    expected:   " + nc3
                                + "\n    calculated: " + c3
                                + "\n    difference: " + Math.abs(nc3 - c3));
            }

            double nc4 = new NumericalDifferentiation(
                    new LogCharacteristicFunction(4, t, cosEngine),
                    4, 5e-2, 9,
                    NumericalDifferentiation.Scheme.Central).value(0.0);

            double c4 = cosEngine.c4(t);

            if (Math.abs(nc4 - c4) > 10 * tol) {
                boostErrors.add(
                        " failed to reproduce 4th cumulant"
                                + "\n    expected:   " + nc4
                                + "\n    calculated: " + c4
                                + "\n    difference: " + Math.abs(nc4 - c4));
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testCosHestonEngine() {
        System.out.println("Testing Heston pricing via COS method...");

        Date settlementDate = new Date(7, Month.FEBRUARY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.15, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.07, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.75;
        double sigma = 1.8;
        double kappa = 4.0;
        double theta = 0.22;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        Date maturityDate = settlementDate.add(
                new Period(1, TimeUnit.YEARS));
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        PricingEngine cosEngine = new COSHestonEngine(model, 25, 600);

        StrikedTypePayoff[] payoffs = {
                new PlainVanillaPayoff(Option.Type.Call,
                        s0.currentLink().value() + 20),
                new PlainVanillaPayoff(Option.Type.Call,
                        s0.currentLink().value() + 150),
                new PlainVanillaPayoff(Option.Type.Put,
                        s0.currentLink().value() - 20),
                new PlainVanillaPayoff(Option.Type.Put,
                        s0.currentLink().value() - 90)
        };

        double[] expected = {
                9.364410588426075, 0.01036797658132471,
                5.319092971836708, 0.01032681906278383};

        double tol = 1e-10;

        for (int i = 0; i < payoffs.length; ++i) {
            EuropeanOption option = new EuropeanOption(payoffs[i], exercise);
            option.setPricingEngine(cosEngine);
            double calculated = option.NPV();

            double error = Math.abs(expected[i] - calculated);

            if (error > tol) {
                boostErrors.add(
                        " failed to reproduce prices with COSHestonEngine"
                                + "\n    expected:   " + expected[i]
                                + "\n    calculated: " + calculated
                                + "\n    difference: " + error);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testCosHestonEngineTruncation() {
        System.out.println(
                "Testing Heston pricing via COS method outside "
                        + "truncation bounds...");

        Date todaysDate = new Date(22, Month.AUGUST, 2022);
        Date maturity = new Date(23, Month.AUGUST, 2022);
        Settings.instance.evaluationDate().equal(todaysDate);

        Option.Type type = Option.Type.Call;
        double underlying = 100;
        double strike = 200;
        double dividendYield = 0;
        double riskFreeRate = 0;
        DayCounter dayCounter = new Actual365Fixed();

        EuropeanExercise europeanExercise =
                new EuropeanExercise(maturity);
        Handle<Quote> underlyingH =
                new Handle<>(new SimpleQuote(underlying), true);
        Handle<YieldTermStructure> riskFreeTS = new Handle<>(
                new FlatForward(todaysDate, riskFreeRate, dayCounter), true);
        Handle<YieldTermStructure> dividendTS = new Handle<>(
                new FlatForward(todaysDate, dividendYield, dayCounter), true);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(type, strike);
        EuropeanOption europeanOption =
                new EuropeanOption(payoff, europeanExercise);

        HestonProcess hestonProcess = new HestonProcess(
                riskFreeTS, dividendTS, underlyingH,
                .007, .8, .007, .1, -.2);
        HestonModel hestonModel = new HestonModel(hestonProcess);

        europeanOption.setPricingEngine(new COSHestonEngine(hestonModel));

        double tol = 1e-7;
        double error = Math.abs(europeanOption.NPV() - 0.0);

        if (error > tol) {
            boostErrors.add(
                    " failed to reproduce prices with COSHestonEngine"
                            + "\n    expected:   " + 0.0
                            + "\n    calculated: " + europeanOption.NPV()
                            + "\n    difference: " + error);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testCharacteristicFct() {
        System.out.println("Testing Heston characteristic function...");

        Date settlementDate = new Date(30, Month.MARCH, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.35, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.17, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.85;
        double sigma = 0.8;
        double kappa = 2.0;
        double theta = 0.15;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        double[] u = {1.0, 0.45, 3, 4};
        double[] t = {0.01, 23.2, 3.2};

        COSHestonEngine cosEngine = new COSHestonEngine(model);
        AnalyticHestonEngine analyticEngine = new AnalyticHestonEngine(model);

        double tol = 100 * QL_EPSILON;
        for (double i : u) {
            for (double j : t) {
                Complex c = cosEngine.chF(i, j);
                Complex a = analyticEngine.chF(new Complex(i, 0.0), j);

                double error = a.subtract(c).abs();
                if (error > tol) {
                    boostErrors.add(
                            " failed to reproduce prices with characteristic Fct"
                                    + "\n    Cos Engine:      " + c
                                    + "\n    analytic engine: " + a
                                    + "\n    difference:      " + error);
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAndersenPiterbargPricing() {
        System.out.println(
                "Testing Andersen-Piterbarg method to "
                        + "price under the Heston model...");

        Date settlementDate = new Date(30, Month.MARCH, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.10, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.06, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = 0.80;
        double sigma = 0.75;
        double kappa = 1.0;
        double theta = 0.1;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        AnalyticHestonEngine andersenPiterbargLaguerreEngine =
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.gaussLaguerre());

        AnalyticHestonEngine andersenPiterbargLobattoEngine =
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.gaussLobatto(
                                NULL_REAL, 1e-9, 10000), 1e-9);

        AnalyticHestonEngine andersenPiterbargSimpsonEngine =
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.discreteSimpson(256), 1e-8);

        AnalyticHestonEngine andersenPiterbargTrapezoidEngine =
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.discreteTrapezoid(164), 1e-8);

        AnalyticHestonEngine andersenPiterbargTrapezoidEngine2 =
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.trapezoid(1e-8, 256), 1e-8);

        ExponentialFittingHestonEngine
                andersenPiterbargExponentialFittingEngine =
                new ExponentialFittingHestonEngine(model);

        PricingEngine[] engines = {
                andersenPiterbargLaguerreEngine,
                andersenPiterbargLobattoEngine,
                andersenPiterbargSimpsonEngine,
                andersenPiterbargTrapezoidEngine,
                andersenPiterbargTrapezoidEngine2,
                andersenPiterbargExponentialFittingEngine
        };

        String[] algos = {
                "Gauss-Laguerre", "Gauss-Lobatto",
                "Discrete Simpson", "Discrete Trapezoid", "Trapezoid",
                "Exponential Fitting"
        };

        PricingEngine analyticEngine =
                new AnalyticHestonEngine(model, 192);

        Date[] maturityDates = {
                settlementDate.add(new Period(1, TimeUnit.DAYS)),
                settlementDate.add(new Period(1, TimeUnit.WEEKS)),
                settlementDate.add(new Period(1, TimeUnit.YEARS)),
                settlementDate.add(new Period(10, TimeUnit.YEARS))
        };

        Option.Type[] optionTypes = {Option.Type.Call, Option.Type.Put};
        double[] strikes = {50, 75, 90, 100, 110, 130, 150, 200};

        double tol = 1e-8;

        for (Date maturityDate : maturityDates) {
            EuropeanExercise exercise = new EuropeanExercise(maturityDate);

            for (Option.Type optionType : optionTypes) {
                for (double strike : strikes) {
                    EuropeanOption option = new EuropeanOption(
                            new PlainVanillaPayoff(optionType, strike),
                            exercise);

                    option.setPricingEngine(analyticEngine);
                    double expected = option.NPV();

                    for (int k = 0; k < engines.length; ++k) {
                        option.setPricingEngine(engines[k]);
                        double calculated = option.NPV();

                        double error = Math.abs(calculated - expected);

                        if (error > tol) {
                            boostErrors.add(
                                    " failed to reproduce prices with Andersen-"
                                            + "Piterbarg control variate"
                                            + "\n    algorithm      : " + algos[k]
                                            + "\n    strike         : " + strike
                                            + "\n    control variate: " + calculated
                                            + "\n    classic engine : " + expected
                                            + "\n    difference:      " + error);
                        }
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAndersenPiterbargControlVariateIntegrand() {
        System.out.println(
                "Testing Andersen-Piterbarg Integrand "
                        + "with control variate...");

        Date settlementDate = new Date(17, Month.APRIL, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);
        Date maturityDate = settlementDate.add(
                new Period(2, TimeUnit.YEARS));

        DayCounter dayCounter = new Actual365Fixed();
        double r = 0.075;
        double q = 0.05;
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(r, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(q, dayCounter), true);

        double maturity = dayCounter.yearFraction(
                settlementDate, maturityDate);
        double df = rTS.currentLink().discount(maturity);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        double fwd = s0.currentLink().value()
                * qTS.currentLink().discount(maturity) / df;

        double strike = 150;
        double sx = Math.log(strike);
        double dd = Math.log(s0.currentLink().value()
                * qTS.currentLink().discount(maturity) / df);

        double v0 = 0.08;
        double rho = -0.8;
        double sigma = 0.5;
        double kappa = 4.0;
        double theta = 0.05;

        HestonModel hestonModel = new HestonModel(new HestonProcess(
                rTS, qTS, s0, v0, kappa, theta, sigma, rho));

        COSHestonEngine cosEngine = new COSHestonEngine(hestonModel);

        AnalyticHestonEngine engine = new AnalyticHestonEngine(
                hestonModel,
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                FourierIntegration.gaussLaguerre());

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, strike),
                new EuropeanExercise(maturityDate));
        option.setPricingEngine(engine);

        double refNPV = option.NPV();

        double implStdDev = BlackFormula.blackFormulaImpliedStdDev(
                Option.Type.Call, strike, fwd, refNPV, df,
                0.0, Double.NaN, 1e-6, 100);

        double var = cosEngine.var(maturity);
        double stdDev = Math.sqrt(var);

        double d = (Math.log(s0.currentLink().value() / strike)
                + (r - q) * maturity + 0.5 * var) / stdDev;

        double skew = cosEngine.skew(maturity);
        double kurt = cosEngine.kurtosis(maturity);

        NormalDistribution n = new NormalDistribution();

        double q3 = 1 / 6. * s0.currentLink().value() * stdDev
                * (2 * stdDev - d) * n.value(d);
        double q4 = 1 / 24. * s0.currentLink().value() * stdDev
                * (d * d - 3 * d * stdDev - 1) * n.value(d);
        double q5 = 1 / 72. * s0.currentLink().value() * stdDev
                * (d * d * d * d - 5 * d * d * d * stdDev
                - 6 * d * d + 15 * d * stdDev + 3) * n.value(d);

        double bsNPV = BlackFormula.blackFormula(
                Option.Type.Call, strike, fwd, stdDev, df, 0.0);

        // different variance values for the control variate
        double[] variances = {
                v0 * maturity,
                ((1 - Math.exp(-kappa * maturity)) * (v0 - theta)
                        / (kappa * maturity) + theta) * maturity,
                // second moment as control variate
                var,
                // third and fourth moment pricing based on
                // Corrado C. and T. Su, (1996-b),
                // "Skewness and Kurtosis in S&P 500 IndexReturns Implied by
                // Option Prices", Journal of Financial Research 19 (2),
                // 175-192.
                square(BlackFormula.blackFormulaImpliedStdDev(
                        Option.Type.Call, strike, fwd,
                        bsNPV + skew * q3, df, 0.0,
                        Double.NaN, 1e-6, 100)),
                square(BlackFormula.blackFormulaImpliedStdDev(
                        Option.Type.Call, strike, fwd,
                        bsNPV + skew * q3 + kurt * q4, df, 0.0,
                        Double.NaN, 1e-6, 100)),
                // Moment matching based on
                // Rubinstein M., (1998), "Edgeworth Binomial Trees",
                // Journal of Derivatives 5 (3), 20-27.
                square(BlackFormula.blackFormulaImpliedStdDev(
                        Option.Type.Call, strike, fwd,
                        bsNPV + skew * q3 + kurt * q4
                                + skew * skew * q5, df, 0.0,
                        Double.NaN, 1e-6, 100)),
                // implied vol as control variate
                square(implStdDev),
                // remaining function becomes zero for u -> 0
                -8.0 * Math.log(engine.chF(
                        new Complex(0, -0.5), maturity).getReal())
        };

        for (int i = 0; i < variances.length; ++i) {
            double sigmaBS = Math.sqrt(variances[i] / maturity);

            for (double u = 0.001; u < 15; u *= 1.05) {
                Complex z = new Complex(u, -0.5);

                Complex phiBS = z.multiply(z)
                        .add(Complex.I.multiply(z))
                        .multiply(-0.5 * sigmaBS * sigmaBS * maturity)
                        .exp();

                Complex ex = new Complex(0.0, u * (dd - sx)).exp();

                Complex chf = engine.chF(z, maturity);

                double orig = ex.multiply(chf)
                        .divide(u * u + 0.25).negate().getReal();
                double cv = ex.multiply(phiBS.subtract(chf))
                        .divide(u * u + 0.25).getReal();

                if (Math.abs(cv) > 0.03) {
                    boostErrors.add(
                            " Control variate function is greater "
                                    + "than original function"
                                    + "\n    control variate method  : " + i
                                    + "\n    z value                 : " + u
                                    + "\n    control variate function: " + cv
                                    + "\n    original function       : " + orig);
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAndersenPiterbargConvergence() {
        System.out.println(
                "Testing Andersen-Piterbarg pricing convergence...");

        Date settlementDate = new Date(5, Month.JULY, 2002);
        Settings.instance.evaluationDate().equal(settlementDate);
        Date maturityDate = new Date(5, Month.JULY, 2003);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.01, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.04;
        double rho = -0.5;
        double sigma = 1.0;
        double kappa = 4.0;
        double theta = 0.25;

        HestonModel hestonModel = new HestonModel(new HestonProcess(
                rTS, qTS, s0, v0, kappa, theta, sigma, rho));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call,
                        s0.currentLink().value()),
                new EuropeanExercise(maturityDate));

        // Alan Lewis reference prices posted in
        // http://wilmott.com/messageview.cfm?catid=34&threadid=90957
        double reference =
                16.070154917028834278213466703938231827658768230714;

        double[] diffs = {
                0.0892433814611486298, 0.00013096156482816923,
                1.34107015270501506e-07, 1.22913235145460931e-10,
                1.24344978758017533e-13};

        for (int n = 10; n <= 50; n += 10) {
            option.setPricingEngine(new AnalyticHestonEngine(
                    hestonModel,
                    AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                    FourierIntegration.discreteTrapezoid(n), 1e-13));

            double calculatedDiff = Math.abs(option.NPV() - reference);
            if (calculatedDiff > 1.25 * diffs[n / 10 - 1]) {
                boostErrors.add(
                        "failed to prove convergence for trapezoid rule "
                                + "\n  calculated difference: " + calculatedDiff
                                + "\n  expected difference:   "
                                + diffs[n / 10 - 1]);
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testPiecewiseTimeDependentChFvsHestonChF() {
        System.out.println(
                "Testing piecewise time dependent "
                        + "ChF vs Heston ChF...");

        Date settlementDate = new Date(5, Month.JULY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);
        Date maturityDate = new Date(5, Month.JULY, 2018);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.01, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.02, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.04;
        double rho = -0.5;
        double sigma = 1.0;
        double kappa = 4.0;
        double theta = 0.25;

        ConstantParameter thetaP =
                new ConstantParameter(theta, new PositiveConstraint());
        ConstantParameter kappaP =
                new ConstantParameter(kappa, new PositiveConstraint());
        ConstantParameter sigmaP =
                new ConstantParameter(sigma, new PositiveConstraint());
        ConstantParameter rhoP =
                new ConstantParameter(rho, new BoundaryConstraint(-1.0, 1.0));

        AnalyticHestonEngine analyticEngine = new AnalyticHestonEngine(
                new HestonModel(new HestonProcess(
                        rTS, qTS, s0, v0, kappa, theta, sigma, rho)));

        AnalyticPTDHestonEngine ptdHestonEngine =
                new AnalyticPTDHestonEngine(
                        new PiecewiseTimeDependentHestonModel(
                                rTS, qTS, s0, v0, thetaP, kappaP, sigmaP, rhoP,
                                new TimeGrid(dayCounter.yearFraction(
                                        settlementDate, maturityDate), 10)));

        double tol = 100 * QL_EPSILON;
        for (double r = 0.1; r < 4; r += 0.25) {
            for (double phi = 0; phi < 360; phi += 60) {
                for (double t = 0.1; t <= 1.0; t += 0.3) {
                    Complex z = new Complex(0.0, phi).exp().multiply(r);

                    Complex a = analyticEngine.chF(z, t);
                    Complex b = ptdHestonEngine.chF(z, t);

                    if (a.subtract(b).abs() > tol) {
                        boostErrors.add(
                                "failed to compare characteristic function "
                                        + "\n  time dependent model: " + b
                                        + "\n  Heston model        : " + a
                                        + "\n  Difference          : "
                                        + a.subtract(b).abs());
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testPiecewiseTimeDependentComparison() {
        System.out.println(
                "Testing piecewise time dependent "
                        + "ChF vs Heston ChF...");

        Date settlementDate = new Date(5, Month.JULY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dc = new Actual365Fixed();
        Date maturityDate = new Date(5, Month.JULY, 2018);
        double maturity = dc.yearFraction(settlementDate, maturityDate);

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.05, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.08, dc), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        List<Double> modelTimes = List.of(0.25, 0.75, 10.0);
        TimeGrid modelGrid = new TimeGrid(modelTimes);

        double v0 = 0.1;
        ConstantParameter theta =
                new ConstantParameter(0.1, new PositiveConstraint());
        ConstantParameter kappa =
                new ConstantParameter(1.0, new PositiveConstraint());
        ConstantParameter rho =
                new ConstantParameter(-0.75, new BoundaryConstraint(-1.0, 1.0));

        List<Double> pTimes = List.of(0.25, 0.75);
        PiecewiseConstantParameter sigma =
                new PiecewiseConstantParameter(pTimes, new PositiveConstraint());

        sigma.setParam(0, 0.30);
        sigma.setParam(1, 0.15);
        sigma.setParam(2, 1.25);

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call,
                        s0.currentLink().value()),
                new EuropeanExercise(maturityDate));

        PiecewiseTimeDependentHestonModel ptdModel =
                new PiecewiseTimeDependentHestonModel(
                        rTS, qTS, s0, v0, theta, kappa, sigma, rho, modelGrid);

        AnalyticPTDHestonEngine ptdHestonEngine =
                new AnalyticPTDHestonEngine(ptdModel);

        option.setPricingEngine(ptdHestonEngine);
        double calculatedGatheral = option.NPV();

        AnalyticPTDHestonEngine ptdAPEngine =
                new AnalyticPTDHestonEngine(
                        ptdModel,
                        AnalyticPTDHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.discreteTrapezoid(128), 1e-12);
        option.setPricingEngine(ptdAPEngine);
        double calculatedAndersenPiterbarg = option.NPV();

        if (Math.abs(calculatedGatheral - calculatedAndersenPiterbarg) > 1e-10) {
            boostErrors.add(
                    "failed to reproduce npv for time dependent Heston model "
                            + "\n  Gatheral ChF         : " + calculatedGatheral
                            + "\n  AndersenPiterbarg ChF: "
                            + calculatedAndersenPiterbarg
                            + "\n  Difference          : "
                            + Math.abs(calculatedGatheral
                            - calculatedAndersenPiterbarg));
        }

        HestonProcess firstPartProcess = new HestonProcess(
                rTS, qTS, s0, v0, 1.0, 0.1, 0.30, -0.75,
                HestonProcess.Discretization.QuadraticExponentialMartingale);

        MultiPathGenerator firstPathGen = new MultiPathGenerator(
                firstPartProcess,
                new TimeGrid(pTimes.get(0), 6),
                PseudoRandom.make_sequence_generator(12, 1234),
                false);

        MersenneTwisterUniformRng urng = new MersenneTwisterUniformRng(5678);

        GeneralStatistics stat = new GeneralStatistics();
        double df = rTS.currentLink().discount(maturityDate);

        int nSims = 10000;
        for (int i = 0; i < nSims; ++i) {
            double priceS = 0.0;

            for (int j = 0; j < 2; ++j) {
                SampleMultiPath path1 = (j & 1) != 0
                        ? firstPathGen.antithetic() : firstPathGen.next();
                double spot1 = path1.value.get(0).back();
                double v1 = path1.value.get(1).back();

                MultiPathGenerator secondPathGen = new MultiPathGenerator(
                        new HestonProcess(
                                rTS, qTS,
                                new Handle<>(new SimpleQuote(spot1), true),
                                v1, 1.0, 0.1, 0.15, -0.75,
                                HestonProcess.Discretization
                                        .QuadraticExponentialMartingale),
                        new TimeGrid(pTimes.get(1) - pTimes.get(0), 12),
                        PseudoRandom.make_sequence_generator(
                                24, urng.nextInt32()),
                        false);

                SampleMultiPath path2 = secondPathGen.next();
                double spot2 = path2.value.get(0).back();
                double v2 = path2.value.get(1).back();

                MultiPathGenerator thirdPathGen = new MultiPathGenerator(
                        new HestonProcess(
                                rTS, qTS,
                                new Handle<>(new SimpleQuote(spot2), true),
                                v2, 1.0, 0.1, 1.25, -0.75,
                                HestonProcess.Discretization
                                        .QuadraticExponentialMartingale),
                        new TimeGrid(maturity - pTimes.get(1), 6),
                        PseudoRandom.make_sequence_generator(
                                12, urng.nextInt32()),
                        false);
                SampleMultiPath path3 = thirdPathGen.next();
                double spot3 = path3.value.get(0).back();

                priceS += 0.5 * option.payoff().value(spot3);
            }

            stat.add(priceS * df, 1.0);
        }

        double calculatedMC = stat.mean();
        double errorEstimate = stat.errorEstimate();

        if (Math.abs(calculatedMC - calculatedGatheral)
                > 3.0 * errorEstimate) {
            boostErrors.add(
                    "failed to reproduce npv for time dependent Heston model"
                            + "\n  Gatheral ChF     : " + calculatedGatheral
                            + "\n  Monte-Carlo      : " + calculatedMC
                            + "\n  Monte-Carlo error: " + errorEstimate
                            + "\n  Difference       : "
                            + Math.abs(calculatedGatheral - calculatedMC));
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testPiecewiseTimeDependentChFAsymtotic() {
        System.out.println(
                "Testing piecewise time dependent "
                        + "ChF vs Heston ChF...");

        Date settlementDate = new Date(5, Month.JULY, 2017);
        Settings.instance.evaluationDate().equal(settlementDate);
        Date maturityDate = settlementDate.add(
                new Period(13, TimeUnit.MONTHS));

        DayCounter dc = new Actual365Fixed();
        double maturity = dc.yearFraction(settlementDate, maturityDate);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.0, dc), true);

        List<Double> modelTimes = List.of(0.01, 0.5, 2.0);

        TimeGrid modelGrid = new TimeGrid(modelTimes);

        double v0 = 0.1;
        List<Double> pTimes = List.of(0.01, 0.5);

        PiecewiseConstantParameter sigma =
                new PiecewiseConstantParameter(pTimes, new PositiveConstraint());
        PiecewiseConstantParameter theta =
                new PiecewiseConstantParameter(pTimes, new PositiveConstraint());
        PiecewiseConstantParameter kappa =
                new PiecewiseConstantParameter(pTimes, new PositiveConstraint());
        PiecewiseConstantParameter rho = new PiecewiseConstantParameter(
                pTimes, new BoundaryConstraint(-1.0, 1.0));

        double[] sigmas = {0.01, 0.2, 0.6};
        double[] thetas = {0.16, 0.06, 0.36};
        double[] kappas = {1.0, 0.3, 4.0};
        double[] rhos = {0.5, -0.75, -0.25};

        for (int i = 0; i < 3; ++i) {
            sigma.setParam(i, sigmas[i]);
            theta.setParam(i, thetas[i]);
            kappa.setParam(i, kappas[i]);
            rho.setParam(i, rhos[i]);
        }

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        PiecewiseTimeDependentHestonModel ptdModel =
                new PiecewiseTimeDependentHestonModel(
                        rTS, rTS, s0, v0, theta, kappa, sigma, rho, modelGrid);

        double eps = 1e-8;

        AnalyticPTDHestonEngine ptdHestonEngine =
                new AnalyticPTDHestonEngine(
                        ptdModel,
                        AnalyticPTDHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                        FourierIntegration.discreteTrapezoid(128), eps);

        Complex D_u_inf = new Complex(
                Math.sqrt(1 - rhos[0] * rhos[0]), rhos[0])
                .divide(sigmas[0]).negate();

        Complex dd = new Complex(
                kappas[0],
                (2 * kappas[0] * rhos[0] - sigmas[0])
                        / (2 * Math.sqrt(1 - rhos[0] * rhos[0])))
                .divide(sigmas[0] * sigmas[0]);

        Complex C_u_inf = Complex.ZERO;
        Complex cc = Complex.ZERO;
        Complex clog = Complex.ZERO;

        for (int i = 0; i < 3; ++i) {
            double kappaI = kappas[i];
            double thetaI = thetas[i];
            double sigmaI = sigmas[i];
            double rhoI = rhos[i];
            double tau = Math.min(maturity, modelGrid.get(i + 1))
                    - modelGrid.get(i);

            C_u_inf = C_u_inf.add(
                    new Complex(Math.sqrt(1 - rhoI * rhoI), rhoI)
                            .multiply(-kappaI * thetaI * tau / sigmaI));

            cc = cc.add(
                    new Complex(2 * kappaI,
                            (2 * kappaI * rhoI - sigmaI)
                                    / Math.sqrt(1 - rhoI * rhoI))
                            .multiply(kappaI * tau * thetaI
                                    / (2 * sigmaI * sigmaI)));

            Complex Di = (i < 2)
                    ? new Complex(Math.sqrt(
                            1 - rhos[i + 1] * rhos[i + 1]), rhos[i + 1])
                    .multiply(sigmaI / sigmas[i + 1])
                    : Complex.ZERO;

            clog = clog.add(
                    Complex.ONE.subtract(
                            Di.subtract(
                                    new Complex(
                                            Math.sqrt(1 - rhoI * rhoI), rhoI))
                                    .divide(Di.add(
                                            new Complex(
                                                    Math.sqrt(1 - rhoI * rhoI),
                                                    -rhoI))))
                            .log()
                            .multiply(2 * kappaI * thetaI
                                    / (sigmaI * sigmaI)));
        }

        double epsilon = eps * Math.PI / s0.currentLink().value();

        double uM = FourierIntegration.andersenPiterbargIntegrationLimit(
                C_u_inf.add(D_u_inf.multiply(v0)).negate().getReal(),
                epsilon, v0, maturity);

        double expectedUM = 18.6918883427;
        if (Math.abs(uM - expectedUM) > 1e-5) {
            boostErrors.add(
                    "failed to reproduce Andersen-Piterbarg "
                            + "Integration bounds for piecewise constant "
                            + "time dependent Heston Model"
                            + "\n  calculated : " + uM
                            + "\n  expected   : " + expectedUM
                            + "\n  diff       : " + Math.abs(uM - expectedUM)
                            + "\n  tolerance  : " + 1e-5);
        }

        double u = 1e8;
        Complex expectedlnChF =
                ptdHestonEngine.lnChF(new Complex(u, 0.0), maturity);
        Complex calculatedAsympotic = D_u_inf.multiply(u).add(dd)
                .multiply(v0)
                .add(C_u_inf.multiply(u))
                .add(cc)
                .add(clog);

        if (expectedlnChF.subtract(calculatedAsympotic).abs() > 0.01) {
            boostErrors.add(
                    "failed to reproduce asymptotic of characteristic function"
                            + "\n  ln(ChF)   : " + expectedlnChF
                            + "\n  asymptotic: " + calculatedAsympotic
                            + "\n  diff      : "
                            + expectedlnChF.subtract(calculatedAsympotic).abs()
                            + "\n  tolerance : " + 0.01);
        }

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call,
                        s0.currentLink().value()),
                new EuropeanExercise(maturityDate));
        option.setPricingEngine(ptdHestonEngine);

        double expectedNPV = 17.43851162589377;
        double calculatedNPV = option.NPV();
        double diffNPV = Math.abs(expectedNPV - calculatedNPV);
        if (diffNPV > 1e-9) {
            boostErrors.add(
                    "failed to reproduce high precision prices for "
                            + "piecewise constant time dependent Heston model"
                            + "\n  expeceted : " + expectedNPV
                            + "\n  calclated : " + calculatedNPV
                            + "\n  diff      : " + diffNPV
                            + "\n  tolerance : " + 1e-9);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testSmallSigmaExpansion() {
        System.out.println(
                "Testing small sigma expansion of "
                        + "the characteristic function...");

        Date settlementDate = new Date(20, Month.MARCH, 2020);
        Settings.instance.evaluationDate().equal(settlementDate);
        Date maturityDate = settlementDate.add(
                new Period(2, TimeUnit.YEARS));

        DayCounter dc = new Actual365Fixed();
        double t = dc.yearFraction(settlementDate, maturityDate);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.0, dc), true);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100), true);

        double theta = 0.1 * 0.1;
        double v0 = theta + 0.02;
        double kappa = 1.25;
        double sigma = 1e-9;
        double rho = -0.9;

        HestonModel hestonModel = new HestonModel(new HestonProcess(
                rTS, rTS, spot, v0, kappa, theta, sigma, rho));

        AnalyticHestonEngine engine = new AnalyticHestonEngine(hestonModel);

        Complex expectedChF = new Complex(
                0.990463578538352651, 2.60693475987521132e-12);

        Complex calculatedChF = engine.chF(
                new Complex(0.55, -0.5), t);

        double diffChF = expectedChF.subtract(calculatedChF).abs();
        double tolChF = 1e-12;
        if (diffChF > tolChF) {
            boostErrors.add(
                    "failed to reproduce normalized characteristic function "
                            + "value for small sigma"
                            + "\n  expeceted : " + expectedChF
                            + "\n  calclated : " + calculatedChF
                            + "\n  diff      : " + diffChF
                            + "\n  tolerance : " + tolChF);
        }

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 120.0),
                new EuropeanExercise(maturityDate));

        option.setPricingEngine(new AnalyticHestonEngine(
                hestonModel,
                AnalyticHestonEngine.ComplexLogFormula.AndersenPiterbarg,
                FourierIntegration.gaussLaguerre(192)));

        double calculatedNPV = option.NPV();

        double stdDev = Math.sqrt(
                ((1 - Math.exp(-kappa * t)) * (v0 - theta)
                        / (kappa * t) + theta) * t);

        double expectedNPV = BlackFormula.blackFormula(
                Option.Type.Call, 120.0, 100.0, stdDev, 1.0, 0.0);

        double diffNPV = Math.abs(calculatedNPV - expectedNPV);
        double tolNPV = 50 * sigma;

        if (diffNPV > tolNPV) {
            boostErrors.add(
                    "failed to reproduce Black Scholes prices "
                            + "for Heston model with very small sigma"
                            + "\n  expeceted : " + expectedNPV
                            + "\n  calclated : " + calculatedNPV
                            + "\n  diff      : " + diffNPV
                            + "\n  tolerance : " + tolNPV);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testSmallSigmaExpansion4ExpFitting() {
        System.out.println(
                "Testing small sigma expansion for the "
                        + "exponential fitting Heston engine...");

        Date todaysDate = new Date(13, Month.MARCH, 2020);
        Settings.instance.evaluationDate().equal(todaysDate);

        DayCounter dc = new Actual365Fixed();
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.05, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.075, dc), true);

        Handle<Quote> spot = new Handle<>(new SimpleQuote(100.0), true);

        // special case: reduce sigma
        Date maturityDate = new Date(14, Month.MARCH, 2021);
        double maturity = dc.yearFraction(todaysDate, maturityDate);
        double fwd = spot.currentLink().value()
                * qTS.currentLink().discount(maturity)
                / rTS.currentLink().discount(maturity);

        double v0 = 0.04;
        double rho = -0.5;
        double kappa = 4.0;
        double theta = 0.04;

        double moneyness = 0.1;
        double strike = Math.exp(-moneyness * Math.sqrt(theta * maturity))
                * fwd;

        double expected = BlackFormula.blackFormula(
                Option.Type.Call, strike, fwd,
                Math.sqrt(v0 * maturity),
                rTS.currentLink().discount(maturity), 0.0);

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, strike),
                new EuropeanExercise(maturityDate));

        for (double sigma = 1e-4; sigma > 1e-12; sigma *= 0.1) {
            option.setPricingEngine(new ExponentialFittingHestonEngine(
                    new HestonModel(new HestonProcess(
                            rTS, qTS, spot, v0, kappa, theta, sigma, rho))));
            double calculated = option.NPV();

            double diff = Math.abs(expected - calculated);

            if (diff > 0.01 * sigma) {
                boostErrors.add(
                        "failed to reproduce Black Scholes prices "
                                + "for Heston model with very small sigma"
                                + "\n  expeceted : " + expected
                                + "\n  calclated : " + calculated
                                + "\n  sigma     : " + sigma
                                + "\n  diff      : " + diff
                                + "\n  tolerance : " + 10 * sigma);
            }
        }

        // generic cases
        double[] kappas = {0.5, 1.0, 4.0};
        double[] thetas = {0.04, 0.09};
        double[] v0s = {0.025, 0.20};
        int[] maturities = {1, 31, 182, 1850};

        Option.Type optionType = Option.Type.Call;

        for (int maturitie : maturities) {
            Date maturityDate2 = todaysDate.add(
                    new Period(maturitie, TimeUnit.DAYS));
            double df = rTS.currentLink().discount(maturityDate2);
            double fwd2 = spot.currentLink().value()
                    * qTS.currentLink().discount(maturityDate2) / df;

            EuropeanExercise exercise = new EuropeanExercise(maturityDate2);

            double t = dc.yearFraction(todaysDate, maturityDate2);

            for (double kappa2 : kappas) {
                for (double theta2 : thetas) {
                    for (double v02 : v0s) {
                        PricingEngine engine =
                                new ExponentialFittingHestonEngine(
                                        new HestonModel(new HestonProcess(
                                                rTS, qTS, spot, v02,
                                                kappa2, theta2, 1e-13, -0.8)));

                        double stdDev = Math.sqrt(
                                ((1 - Math.exp(-kappa2 * t))
                                        * (v02 - theta2) / (kappa2 * t)
                                        + theta2) * t);

                        for (double strike2 =
                             spot.currentLink().value()
                                     * Math.exp(-10 * stdDev);
                             strike2 < spot.currentLink().value()
                                     * Math.exp(10 * stdDev);
                             strike2 *= 1.2) {

                            EuropeanOption option2 = new EuropeanOption(
                                    new PlainVanillaPayoff(
                                            optionType, strike2), exercise);

                            option2.setPricingEngine(engine);
                            double calculated = option2.NPV();

                            double expected2 = BlackFormula.blackFormula(
                                    optionType, strike2, fwd2,
                                    stdDev, df, 0.0);

                            double diff = Math.abs(expected2 - calculated);
                            if (diff > 1e-10) {
                                boostErrors.add(
                                        "failed to reproduce Black Scholes prices "
                                                + "for Heston model with very small sigma"
                                                + "\n  expceted  : " + expected2
                                                + "\n  calculated: " + calculated
                                                + "\n  diff      : " + diff
                                                + "\n  tolerance : " + 1e-10);
                            }

                            optionType = (optionType == Option.Type.Call)
                                    ? Option.Type.Put : Option.Type.Call;
                        }
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testExponentialFitting4StrikesAndMaturities() {
        System.out.println("Testing exponential fitting Heston engine "
                + "with high precision results for large moneyness...");

        Date todaysDate = new Date(13, Month.MAY, 2020);
        Settings.instance.evaluationDate().equal(todaysDate);

        DayCounter dc = new Actual365Fixed();

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.0507, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.0469, dc), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);

        double[] moneyness = {-20, -10, -5, 2.5, 1, 0, 1, 2.5, 5, 10, 20};
        Period[] maturities = {
                new Period(1, TimeUnit.DAYS),
                new Period(1, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS),
                new Period(10, TimeUnit.YEARS)
        };

        double v0 = 0.04;
        double rho = -0.6;
        double sigma = 0.75;
        double kappa = 2.5;
        double theta = 0.06;

        // Reference prices are calculated using a boost multi-precision
        // implementation of the AnalyticHestonEngine,
        // https://github.com/klausspanden/HestonExponentialFitting

        double[] referenceValues = {
                1.1631865252540813e-58,
                1.06426822273258466e-49,
                6.92896489110422086e-16,
                8.19515526286263236e-06,
                0.000625608178476390504,
                0.00417261379371945684,
                0.000625608178476390504,
                8.19515526286263236e-06,
                1.92308901296741414e-10,
                1.57327901822368115e-23,
                5.7830515043285098e-58,
                3.56081886910098813e-48,
                2.9489071194212509e-23,
                1.54181757781090727e-11,
                0.000367960011879847279,
                0.00493886106106039818,
                0.0227152343265593776,
                0.00493886106106039818,
                0.000367960011879847279,
                3.06653474407784574e-06,
                8.86665241279348934e-11,
                1.51206812371708868e-20,
                4.18506719865401643e-29,
                2.46637786897559908e-15,
                1.75338784910563671e-08,
                0.00284789176080218294,
                0.0199133097064688458,
                0.0776848755698912041,
                0.0199133097064688458,
                0.00284789176080218294,
                0.00012462190796343504,
                2.59755319566692257e-07,
                1.13853114743124721e-12,
                4.27612073892114211e-39,
                1.08387452075906664e-25,
                4.15179522944463802e-11,
                0.00134157732880653131,
                0.029018582813884912,
                0.176405213088554197,
                0.029018582813884912,
                0.00134157732880653131,
                5.43674074281991917e-06,
                6.51443921040230507e-11,
                9.25756999394709285e-21
        };

        HestonModel model = new HestonModel(new HestonProcess(
                rTS, qTS, s0, v0, kappa, theta, sigma, rho));

        PricingEngine engine = new ExponentialFittingHestonEngine(model);

        int idx = 0;
        for (Period maturitie : maturities) {
            Date maturityDate = todaysDate.add(maturitie);
            double t = dc.yearFraction(todaysDate, maturityDate);

            EuropeanExercise exercise = new EuropeanExercise(maturityDate);

            double df = rTS.currentLink().discount(t);
            double fwd = s0.currentLink().value()
                    * qTS.currentLink().discount(t) / df;

            for (int j = 0; j < moneyness.length; ++j, ++idx) {
                double strike =
                        Math.exp(-moneyness[j] * Math.sqrt(theta * t)) * fwd;

                for (int k = 0; k < 2; ++k) {
                    PlainVanillaPayoff payoff = new PlainVanillaPayoff(
                            (k) != 0 ? Option.Type.Put : Option.Type.Call,
                            strike);

                    EuropeanOption option =
                            new EuropeanOption(payoff, exercise);
                    option.setPricingEngine(engine);

                    double calculated = option.NPV();

                    double expected;
                    if (payoff.optionType() == Option.Type.Call)
                        if (fwd < strike)
                            expected = referenceValues[idx];
                        else
                            expected = (fwd - strike) * df
                                    + referenceValues[idx];
                    else
                        if (fwd > strike)
                            expected = referenceValues[idx];
                        else
                            expected = referenceValues[idx]
                                    - (fwd - strike) * df;

                    double diff = Math.abs(calculated - expected);
                    double tol = 1e-8;
                    if (diff > tol) {
                        boostErrors.add(
                                "failed to reproduce cached extreme "
                                        + "Heston model prices with exponential fitted "
                                        + "Gauss-Laguerre quadrature rule"
                                        + "\n  forward   : " + fwd
                                        + "\n  strike    : " + strike
                                        + "\n  expected  : " + expected
                                        + "\n  calculated: " + calculated
                                        + "\n  diff      : " + diff
                                        + "\n  tolerance : " + tol);
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testHestonEngineIntegration() {
        System.out.println("Testing Heston engine integration signature...");

        java.util.function.DoubleUnaryOperator square = x -> x * x;

        FourierIntegration integration =
                FourierIntegration.gaussLobatto(1e-12, 1e-12);

        double c1 = integration.calculate(1.0, square, 1.0);

        HestonIntegrationMaxBoundTestFct testFct =
                new HestonIntegrationMaxBoundTestFct(1.0);
        double c2 = integration.calculate(1.0, square, testFct, 1.0);

        if (testFct.getCallCounter() == 0
                || Math.abs(c1 - 1 / 3.) > 1e-10
                || Math.abs(c2 - 1 / 3.) > 1e-10) {
            boostErrors.add(
                    "failed to test Heston engine integration signature");
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testOptimalControlVariateChoice() {
        System.out.println(
                "Testing optimal control variate choice for the Heston model...");

        double v0 = 0.0225;
        double rho = 0.5;
        double sigma = 2.0;
        double kappa = 0.1;
        double theta = 0.01;
        double t = 2.0;

        AnalyticHestonEngine.ComplexLogFormula calculated =
                AnalyticHestonEngine.optimalControlVariate(
                        t, v0, kappa, theta, sigma, rho);

        if (calculated != AnalyticHestonEngine.ComplexLogFormula.AsymptoticChF) {
            boostErrors.add(
                    "failed to reproduce optimal control variate choice");
        }

        calculated = AnalyticHestonEngine.optimalControlVariate(
                t, v0, kappa, theta, 0.05, rho);
        if (calculated != AnalyticHestonEngine.ComplexLogFormula.AngledContour) {
            boostErrors.add(
                    "failed to reproduce optimal control variate choice");
        }

        calculated = AnalyticHestonEngine.optimalControlVariate(
                t, 0.5, kappa, theta, sigma, rho);
        if (calculated != AnalyticHestonEngine.ComplexLogFormula.AngledContour) {
            boostErrors.add(
                    "failed to reproduce optimal control variate choice");
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAsymptoticControlVariate() {
        System.out.println("Testing Heston asymptotic control variate...");

        Date todaysDate = new Date(4, Month.AUGUST, 2020);
        Settings.instance.evaluationDate().equal(todaysDate);

        DayCounter dc = new Actual365Fixed();

        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.0, dc), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.0), true);

        double v0 = 0.0225;
        double rho = 0.5;
        double sigma = 2.0;
        double kappa = 0.1;
        double theta = 0.01;

        HestonModel model = new HestonModel(new HestonProcess(
                rTS, rTS, s0, v0, kappa, theta, sigma, rho));

        Date maturityDate = todaysDate.add(
                new Period(2, TimeUnit.YEARS));
        double t = dc.yearFraction(todaysDate, maturityDate);
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        double[] moneynesses = {-15, -10, -5, 0, 5, 10, 15};

        double[] expected = {
                0.0074676425640918,
                0.008680823863233695,
                0.010479611906112223,
                0.023590088942038945,
                0.0019575784806211706,
                0.0005490310253748906,
                0.0001657118753134695
        };

        PricingEngine[] engines = {
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.OptimalCV,
                        FourierIntegration.gaussLobatto(
                                1e-10, 1e-10, 100000)),
                new AnalyticHestonEngine(
                        model,
                        AnalyticHestonEngine.ComplexLogFormula.OptimalCV,
                        FourierIntegration.gaussLaguerre(96)),
                new ExponentialFittingHestonEngine(model)
        };

        for (int j = 0; j < engines.length; ++j) {
            for (int i = 0; i < moneynesses.length; ++i) {
                double moneyness = moneynesses[i];

                double strike =
                        Math.exp(-moneyness * Math.sqrt(theta * t));

                PlainVanillaPayoff payoff = new PlainVanillaPayoff(
                        strike > 1.0 ? Option.Type.Call : Option.Type.Put,
                        strike);

                PricingEngine engine = engines[j];

                EuropeanOption option = new EuropeanOption(payoff, exercise);
                option.setPricingEngine(engine);

                double calculated = option.NPV();

                if (engine instanceof AnalyticHestonEngine analyticHestonEngine
                        && analyticHestonEngine.numberOfEvaluations() > 5000) {
                    boostErrors.add(
                            "too many function valuation needed "
                                    + "\n  moneyness      : " + moneyness
                                    + "\n  evaluations    : "
                                    + analyticHestonEngine.numberOfEvaluations()
                                    + "\n  max evaluations: " + 5000);
                }

                double diff = Math.abs(calculated - expected[i]);
                if (diff > 5e-8) {
                    boostErrors.add(
                            "failed to reproduce extreme Heston model values for"
                                    + "\n  moneyness : " + moneyness
                                    + "\n  #engine   : " + j
                                    + "\n  calculated: " + calculated
                                    + "\n  expected  : " + expected[i]
                                    + "\n  difference: " + diff
                                    + "\n  tolerance : " + 1e-8);
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testLocalVolFromHestonModel() {
        System.out.println(
                "Testing Local Volatility pricing from Heston Model...");

        Date todaysDate = new Date(28, Month.JUNE, 2021);
        Settings.instance.evaluationDate().equal(todaysDate);

        DayCounter dc = new Actual365Fixed();

        Handle<YieldTermStructure> rTS = new Handle<>(new ZeroCurve(
                List.of(todaysDate,
                        todaysDate.add(new Period(90, TimeUnit.DAYS)),
                        todaysDate.add(new Period(180, TimeUnit.DAYS)),
                        todaysDate.add(new Period(1, TimeUnit.YEARS))),
                List.of(0.075, 0.05, 0.075, 0.1),
                dc), true);

        Handle<YieldTermStructure> qTS = new Handle<>(new ZeroCurve(
                List.of(todaysDate,
                        todaysDate.add(new Period(90, TimeUnit.DAYS)),
                        todaysDate.add(new Period(1, TimeUnit.YEARS))),
                List.of(0.06, 0.04, 0.12),
                dc), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.75;
        double sigma = 0.8;
        double kappa = 1.0;
        double theta = 0.16;

        HestonModel hestonModel = new HestonModel(new HestonProcess(
                rTS, qTS, s0, v0, kappa, theta, sigma, rho));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 120.0),
                new EuropeanExercise(
                        todaysDate.add(new Period(1, TimeUnit.YEARS))));

        option.setPricingEngine(new AnalyticHestonEngine(
                hestonModel,
                AnalyticHestonEngine.ComplexLogFormula.OptimalCV,
                FourierIntegration.gaussLaguerre(192)));

        double expected = option.NPV();

        HestonBlackVolSurface hestonSurface = new HestonBlackVolSurface(
                new Handle<>(hestonModel, true),
                AnalyticHestonEngine.ComplexLogFormula.OptimalCV,
                FourierIntegration.gaussLaguerre(24));

        option.setPricingEngine(new FdBlackScholesVanillaEngine(
                new BlackScholesMertonProcess(
                        s0, qTS, rTS,
                        new Handle<BlackVolTermStructure>(hestonSurface, true)),
                25, 125, 1, FdmSchemeDesc.Douglas(), true, 0.4));

        double calculated = option.NPV();

        double tol = 0.002;
        double diff = Math.abs(calculated - expected);
        if (diff > tol) {
            boostErrors.add(
                    "failed to reproduce Heston model values with "
                            + "local volatility pricing"
                            + "\n  calculated: " + calculated
                            + "\n  expected  : " + expected
                            + "\n  difference: " + diff
                            + "\n  tolerance : " + tol);
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testOptimalAlphaKmin() {
        System.out.println(
                "Testing optimal Alpha k_min for characteristic function...");

        Date todaysDate = new Date(1, Month.JANUARY, 2023);
        Settings.instance.evaluationDate().equal(todaysDate);

        // Example taken from figure 3 in Andersen and M. Lake, 2018
        // Robust High-Precision Option Pricing by Fourier Transforms:
        DayCounter dc = new Actual365Fixed();
        HestonModel model = new HestonModel(new HestonProcess(
                new Handle<>(flatRate(0.0, dc), true),
                new Handle<>(flatRate(0.0, dc), true),
                new Handle<>(new SimpleQuote(150), true),
                0.01, 0.1, 0.01, 2.0, 0.8));

        AnalyticHestonEngine engine = new AnalyticHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.Gatheral,
                FourierIntegration.gaussLobatto(NULL_REAL, 1e-12, 100000));

        double strike = 100;

        double alphaStar = new AnalyticHestonEngine.OptimalAlpha(
                1.0, engine).alphaSmallerMinusOne(strike).first;

        if (Math.abs(alphaStar + 3.71) > 0.0051) {
            boostErrors.add(
                    "failed to check optimal Alpha k_min "
                            + "\n  calculated: " + alphaStar
                            + "\n  expected  : " + -3.71
                            + "\n  difference: " + Math.abs(alphaStar + 3.71)
                            + "\n  tolerance : " + 0.0051);
        }

        Date maturityDate = todaysDate.add(
                new Period(15, TimeUnit.MONTHS));

        EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, strike),
                new EuropeanExercise(maturityDate));
        option.setPricingEngine(engine);
        double expected = option.NPV();

        option.setPricingEngine(new AnalyticHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                FourierIntegration.gaussLobatto(NULL_REAL, 1e-12, 5000)));

        if (Math.abs(option.NPV() - expected) / expected > 1e-10)
            boostErrors.add(
                    "failed to reproduce reference values with Angled-Contour");

        option.setPricingEngine(new ExponentialFittingHestonEngine(
                model,
                AnalyticHestonEngine.ComplexLogFormula.OptimalCV));

        if (Math.abs(option.NPV() - expected) / expected > 1e-8)
            boostErrors.add(
                    "failed to reproduce reference values with Angled-Contour"
                            + "and exponential fitting");

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testOptimalAlphaKmax() {
        System.out.println(
                "Testing optimal Alpha k_min for characteristic function...");

        Date todaysDate = new Date(1, Month.JANUARY, 2022);
        Settings.instance.evaluationDate().equal(todaysDate);

        DayCounter dc = new Actual365Fixed();
        Handle<YieldTermStructure> yTS =
                new Handle<>(flatRate(0.0, dc), true);
        Handle<Quote> spot =
                new Handle<>(new SimpleQuote(75), true);

        double T = 2;
        double strike = 100;

        // case 1: kappa - sigma*rho > 0
        HestonModel model = new HestonModel(new HestonProcess(
                yTS, yTS, spot, .1, 1.2, 0.2, 0.2, -0.8));
        AnalyticHestonEngine engine = new AnalyticHestonEngine(model);
        double alphaStar = new AnalyticHestonEngine.OptimalAlpha(T, engine)
                .alphaGreaterZero(strike).first;
        checkSmall(alphaStar - 3.22615, 1e-4,
                "optimal Alpha k_max, case 1");

        // case 2: kappa - sigma*rho < 0, T < t_cut
        model = new HestonModel(new HestonProcess(
                yTS, yTS, spot, 0.1, 1.2, 0.2, 1.5, 0.9));
        engine = new AnalyticHestonEngine(model);
        alphaStar = new AnalyticHestonEngine.OptimalAlpha(T, engine)
                .alphaGreaterZero(strike).first;
        checkSmall(alphaStar - 0.31137, 1e-4,
                "optimal Alpha k_max, case 2");

        // case 3: kappa - sigma*rho < 0, T >= t_cut
        model = new HestonModel(new HestonProcess(
                yTS, yTS, spot, 0.1, 1.2, 0.2, 2.25, 0.9));
        engine = new AnalyticHestonEngine(model);
        alphaStar = new AnalyticHestonEngine.OptimalAlpha(T, engine)
                .alphaGreaterZero(strike).first;
        checkSmall(alphaStar - 0.11940, 1e-4,
                "optimal Alpha k_max, case 3");

        // case 4: kappa - sigma*rho == 0
        model = new HestonModel(new HestonProcess(
                yTS, yTS, spot, 0.1, 1.0, 0.2, 2.0, 0.5));
        engine = new AnalyticHestonEngine(model);
        alphaStar = new AnalyticHestonEngine.OptimalAlpha(T, engine)
                .alphaGreaterZero(strike).first;
        checkSmall(alphaStar - 0.28006, 1e-4,
                "optimal Alpha k_max, case 4");

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testHestonBlackVolSurfaceAtmLevel() {
        System.out.println("Testing atmLevel on HestonBlackVolSurface...");

        Date today = new Date(4, Month.MARCH, 2026);
        Settings.instance.evaluationDate().equal(today);
        DayCounter dc = new Actual365Fixed();

        double s0 = 100.0;
        double r = 0.05, q = 0.02;

        Handle<Quote> spot = new Handle<>(new SimpleQuote(s0), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(today, r, dc), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(today, q, dc), true);

        HestonProcess process = new HestonProcess(
                rTS, qTS, spot, 0.04, 1.0, 0.04, 0.3, -0.5);
        HestonModel model = new HestonModel(process);
        Handle<HestonModel> modelH = new Handle<>(model, true);
        HestonBlackVolSurface surface = new HestonBlackVolSurface(modelH);

        double tol = 1e-12;
        for (double t : new double[]{0.25, 1.0, 5.0}) {
            double expected = s0 * qTS.currentLink().discount(t)
                    / rTS.currentLink().discount(t);
            double calculated = surface.atmLevel(t);
            if (Math.abs(calculated - expected) > tol)
                fail("HestonBlackVolSurface::atmLevel mismatch"
                        + "\n   t:          " + t
                        + "\n   calculated: " + calculated
                        + "\n   expected:   " + expected
                        + "\n   diff:       " + (calculated - expected)
                        + "\n   tolerance:  " + tol);
        }
    }

    @Test
    public void testAsymptoticCVwithDifferentAlpha() {
        System.out.println("Testing optimal asymptotic "
                + "control variate with different alphas...");
        Date settlementDate = new Date(29, Month.SEPTEMBER, 2026);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.07, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.185, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double sigma = 2.0;
        double[] strikes = {50, 75, 100, 125, 200};
        double[] rhos = {-0.9, -0.5, 0.0, 0.9};
        double[] kappas = {1.0, 0.5, 0.3};
        double[] thetas = {0.01, 0.3, 0.5};

        for (double strike : strikes) {
            for (double rho : rhos) {
                for (double kappa : kappas) {
                    for (double theta : thetas) {

                        HestonModel model = new HestonModel(
                                new HestonProcess(
                                        riskFreeTS, dividendTS, s0,
                                        v0, kappa, theta, sigma, rho));

                        Date maturityDate = new Date(5, Month.JULY, 2027);

                        EuropeanOption option = new EuropeanOption(
                                new PlainVanillaPayoff(
                                        Option.Type.Call, strike),
                                new EuropeanExercise(maturityDate));

                        AnalyticHestonEngine engine =
                                new AnalyticHestonEngine(
                                        model,
                                        AnalyticHestonEngine.ComplexLogFormula
                                                .AngledContour,
                                        FourierIntegration.expSinh(1e-12));

                        option.setPricingEngine(engine);
                        double expected = option.NPV();

                        AnalyticHestonEngine.OptimalAlpha optimalAlpha =
                                new AnalyticHestonEngine.OptimalAlpha(
                                        dayCounter.yearFraction(
                                                settlementDate, maturityDate),
                                        engine);
                        double alphaMin = 0.75 * optimalAlpha.alphaMin(strike);
                        double alphaMax = 0.75 * optimalAlpha.alphaMax(strike);
                        Concentrating1dMesher mesher =
                                new Concentrating1dMesher(
                                        alphaMin, alphaMax, 5,
                                        new Point<>(-0.5, 0.1), true);

                        for (double alpha : mesher.locations()) {
                            List<String> engineNames = List.of(
                                    "AngledContour",
                                    "AngledContourNoCV",
                                    "AsymptoticChF");
                            List<AnalyticHestonEngine> engines = List.of(
                                    new AnalyticHestonEngine(
                                            model,
                                            AnalyticHestonEngine
                                                    .ComplexLogFormula.AngledContour,
                                            FourierIntegration.expSinh(1e-8),
                                            1e-25, alpha),
                                    new AnalyticHestonEngine(
                                            model,
                                            AnalyticHestonEngine
                                                    .ComplexLogFormula.AngledContourNoCV,
                                            FourierIntegration.expSinh(1e-8),
                                            1e-25, alpha),
                                    new AnalyticHestonEngine(
                                            model,
                                            AnalyticHestonEngine
                                                    .ComplexLogFormula.AsymptoticChF,
                                            FourierIntegration.expSinh(1e-12),
                                            1e-25, alpha));

                            for (int e = 0; e < engines.size(); ++e) {
                                option.setPricingEngine(engines.get(e));

                                double calculated = option.NPV();
                                double diff = Math.abs(calculated - expected);
                                double tol = !engineNames.get(e)
                                        .equals("AsymptoticChF")
                                        ? 1e-6 : 1e-10;

                                if (diff > tol) {
                                    boostErrors.add(
                                            "failed to reproduce Heston option values with "
                                                    + "AsymptoticChF "
                                                    + "\n  calculated: " + calculated
                                                    + "\n  expected  : " + expected
                                                    + "\n  difference: " + diff
                                                    + "\n  tolerance : " + tol
                                                    + "\n  engine    : "
                                                    + engineNames.get(e)
                                                    + "\n  strike    : " + strike
                                                    + "\n  alpha     : " + alpha
                                                    + "\n  theta     : " + theta
                                                    + "\n  kappa     : " + kappa
                                                    + "\n  rho       : " + rho);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    @Test
    public void testAnalyticPDFHestonEngine() {
        System.out.println("Testing analytic PDF Heston engine...");

        Date settlementDate = new Date(5, Month.JANUARY, 2014);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();
        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.07, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.185, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);

        double v0 = 0.1;
        double rho = -0.5;
        double sigma = 1.0;
        double kappa = 4.0;
        double theta = 0.05;

        HestonModel model = new HestonModel(new HestonProcess(
                riskFreeTS, dividendTS, s0, v0, kappa, theta, sigma, rho));

        double tol = 1e-6;
        AnalyticPDFHestonEngine pdfEngine =
                new AnalyticPDFHestonEngine(model, tol);

        PricingEngine analyticEngine = new AnalyticHestonEngine(model, 178);

        Date maturityDate = new Date(5, Month.JULY, 2014);
        double maturity =
                dayCounter.yearFraction(settlementDate, maturityDate);
        EuropeanExercise exercise = new EuropeanExercise(maturityDate);

        // 1. check a plain vanilla call option
        for (double strike = 40; strike < 190; strike += 20) {
            StrikedTypePayoff vanillaPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, strike);

            EuropeanOption planVanillaOption =
                    new EuropeanOption(vanillaPayoff, exercise);

            planVanillaOption.setPricingEngine(pdfEngine);
            double calculated = planVanillaOption.NPV();

            planVanillaOption.setPricingEngine(analyticEngine);
            double expected = planVanillaOption.NPV();

            if (Math.abs(calculated - expected) > 3 * tol) {
                fail("failed to reproduce plain vanilla european prices with"
                        + " the analytic probability density engine"
                        + "\n    strike     : " + strike
                        + "\n    expected   : " + expected
                        + "\n    calculated : " + calculated
                        + "\n    diff       : "
                        + Math.abs(calculated - expected)
                        + "\n    tol        ; " + tol);
            }
        }

        // 2. digital call option (approx. with a call spread)
        for (double strike = 40; strike < 190; strike += 10) {
            EuropeanOption digitalOption = new EuropeanOption(
                    new CashOrNothingPayoff(
                            Option.Type.Call, strike, 1.0),
                    exercise);
            digitalOption.setPricingEngine(pdfEngine);
            double calculated = digitalOption.NPV();

            double eps = 0.01;
            EuropeanOption longCall = new EuropeanOption(
                    new PlainVanillaPayoff(
                            Option.Type.Call, strike - eps),
                    exercise);
            longCall.setPricingEngine(analyticEngine);

            EuropeanOption shortCall = new EuropeanOption(
                    new PlainVanillaPayoff(
                            Option.Type.Call, strike + eps),
                    exercise);
            shortCall.setPricingEngine(analyticEngine);

            double expected =
                    (longCall.NPV() - shortCall.NPV()) / (2 * eps);
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce european digital prices with"
                        + " the analytic probability density engine"
                        + "\n    strike     : " + strike
                        + "\n    expected   : " + expected
                        + "\n    calculated : " + calculated
                        + "\n    diff       : "
                        + Math.abs(calculated - expected)
                        + "\n    tol        : " + tol);
            }

            double d = riskFreeTS.currentLink().discount(maturityDate);
            double expectedCDF = 1.0 - expected / d;
            double calculatedCDF = pdfEngine.cdf(strike, maturity);

            if (Math.abs(expectedCDF - calculatedCDF) > tol) {
                fail("failed to reproduce cumulative distribution function"
                        + "\n    strike        : " + strike
                        + "\n    expected CDF  : " + expectedCDF
                        + "\n    calculated CDF: " + calculatedCDF
                        + "\n    diff          : "
                        + Math.abs(calculatedCDF - expectedCDF)
                        + "\n    tol           : " + tol);
            }
        }
    }

    private static double square(double x) {
        return x * x;
    }

    private void checkSmall(double value, double tolerance, String label) {
        if (Math.abs(value) > tolerance) {
            boostErrors.add(
                    "failed to check " + label
                            + "\n  difference: " + Math.abs(value)
                            + "\n  tolerance : " + tolerance);
        }
    }

    private static class HestonProcessDiscretizationDesc {
        final HestonProcess.Discretization discretization;
        final int nSteps;
        final String name;

        HestonProcessDiscretizationDesc(
                HestonProcess.Discretization discretization,
                int nSteps, String name) {
            this.discretization = discretization;
            this.nSteps = nSteps;
            this.name = name;
        }
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

    /**
     * Official {@code flatRate(const Date&, Rate, const DayCounter&)}.
     */
    private static YieldTermStructure flatRate(Date today, double forward,
                                               DayCounter dc) {
        return new FlatForward(today,
                new Handle<>(new SimpleQuote(forward), true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    /**
     * Official {@code flatVol(const Date&, Volatility, const DayCounter&)}.
     */
    private static BlackVolTermStructure flatVol(Date today, double volatility,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(new SimpleQuote(volatility), true), dc);
    }
}
