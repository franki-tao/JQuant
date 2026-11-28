package pricingengines;

import jquant.AmericanExercise;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.math.Array;
import jquant.math.distributions.GammaFunction;
import jquant.math.integrals.FourierIntegration;
import jquant.math.ode.FractionalAdams;
import jquant.math.ode.FractionalKernelApproximation;
import jquant.math.ode.RiemannLiouvilleIntegral;
import jquant.math.ode.RiemannLiouvilleWeights;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.models.BlackCalibrationHelper;
import jquant.models.BlackCalibrationHelper.CalibrationErrorType;
import jquant.models.CalibrationHelper;
import jquant.models.equity.HestonModel;
import jquant.models.equity.HestonModelHelper;
import jquant.models.equity.RoughHestonModel;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.pricingengines.vanilla.AnalyticRoughHestonEngine;
import jquant.pricingengines.vanilla.AnalyticRoughHestonEngine.Approximation;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/roughhestonmodel.cpp} tests
 * (suite {@code RoughHestonModelTests}).
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end. Official
 * {@code BOOST_FAIL} would abort immediately (none in this suite).
 * {@code BOOST_TEST_MESSAGE} is printed with {@link System#out}.
 * {@code QL_CHECK_CLOSE} is Boost {@code BOOST_CHECK_CLOSE}, i.e. a
 * percentage-relative tolerance.
 */
public class RoughHestonModelTest {

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
        // SavedSettings restores the evaluation date. Only copy the observers
        // this case added: a long Surefire JVM can already hold a large
        // leftover set from earlier classes.
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

    private void boostError(String message) {
        boostErrors.add(message);
    }

    private void checkBoostErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    /** Official {@code QL_CHECK_CLOSE}: tolerance in percent. */
    private void checkClosePercent(double calculated, double expected,
                                   double tolerancePercent, String message) {
        final double relativePercent = Math.abs(calculated - expected)
                / Math.abs(expected) * 100.0;
        if (relativePercent > tolerancePercent) {
            boostError(message
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected
                    + "\n    tolerance:  " + tolerancePercent + "%");
        }
    }

    // ------------------------------------------------------------------
    // 1. testFractionalAdamsScheme
    // ------------------------------------------------------------------

    private static double maxAdamsError(double g, double alpha,
                                        double tMax, int steps) {
        final double[] y = new FractionalAdams(alpha).solve(
                (t, x) -> g * Math.pow(t, 4 - alpha) + t * t * t * t - x,
                0.0, tMax, steps);

        double error = 0.0;
        for (int i = 0; i <= steps; ++i) {
            final double t = (double) i * tMax / steps;
            error = Math.max(error, Math.abs(y[i] - t * t * t * t));
        }
        return error;
    }

    @Test
    void testFractionalAdamsScheme() {
        System.out.println(
                "Testing fractional Adams scheme against known solutions...");

        // D^a y = Gamma(5)/Gamma(5-a) t^(4-a) + t^4 - y with y(0) = 0
        // has the exact solution y(t) = t^4
        final double alpha = 0.6;
        final double g = new GammaFunction().value(5.0)
                / new GammaFunction().value(5.0 - alpha);

        final double tMax = 1.0;

        final double coarseError = maxAdamsError(g, alpha, tMax, 100);
        final double fineError = maxAdamsError(g, alpha, tMax, 200);

        final double tol = 3e-4;
        if (fineError > tol)
            boostError("failed to reproduce known fractional ODE solution"
                    + "\n    max error:  " + fineError
                    + "\n    tolerance:  " + tol);

        // at convergence order 1 + alpha = 1.6, halving the step size
        // should reduce the error by about a factor of three
        final double minRatio = 2.2;
        if (coarseError / fineError < minRatio)
            boostError("failed to reproduce fractional Adams convergence order"
                    + "\n    error (n=100):  " + coarseError
                    + "\n    error (n=200):  " + fineError
                    + "\n    ratio:          " + coarseError / fineError
                    + "\n    expected ratio: > " + minRatio);

        // the linear problem D^a y = -y with y(0) = 1 has the solution
        // y(t) = E_a(-t^a), the Mittag-Leffler function, computed here
        // by direct series summation
        double mlSum = 0.0;
        double sgn = 1.0;
        for (int k = 0; k < 100; ++k, sgn = -sgn)
            mlSum += sgn / new GammaFunction().value(alpha * k + 1.0);
        final double mittagLefflerRef = mlSum;

        final double[] w = new FractionalAdams(alpha).solve(
                (t, x) -> -x, 1.0, 1.0, 400);

        final double mlTol = 1e-4;
        if (Math.abs(w[w.length - 1] - mittagLefflerRef) > mlTol)
            boostError("failed to reproduce Mittag-Leffler solution of the "
                    + "linear fractional ODE"
                    + "\n    calculated: " + w[w.length - 1]
                    + "\n    expected:   " + mittagLefflerRef
                    + "\n    tolerance:  " + mlTol);

        // the pricing engine only ever instantiates the complex overload;
        // D^a z = i omega z with z(0) = 1 has the solution
        // z(t) = E_a(i omega t^a)
        final Complex lambda = new Complex(0.0, 1.5);
        Complex complexSum = Complex.ZERO;
        Complex term = Complex.ONE;
        for (int k = 0; k < 100; ++k, term = term.multiply(lambda))
            complexSum = complexSum.add(term.divide(
                    new GammaFunction().value(alpha * k + 1.0)));
        final Complex mittagLefflerComplexRef = complexSum;

        final Complex[] wc = new FractionalAdams(alpha).solve(
                (t, x) -> lambda.multiply(x), Complex.ONE, 1.0, 400);

        if (wc[wc.length - 1].subtract(mittagLefflerComplexRef).abs() > mlTol)
            boostError("failed to reproduce complex Mittag-Leffler solution of "
                    + "the linear fractional ODE"
                    + "\n    calculated: " + wc[wc.length - 1]
                    + "\n    expected:   " + mittagLefflerComplexRef
                    + "\n    tolerance:  " + mlTol);

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 2. testRiemannLiouvilleIntegral
    // ------------------------------------------------------------------

    @Test
    void testRiemannLiouvilleIntegral() {
        System.out.println(
                "Testing product-trapezoidal Riemann-Liouville integration...");

        // I^a t^p = Gamma(p+1)/Gamma(p+1+a) t^(p+a)
        final int steps = 500;
        final double tMax = 2.0;
        final double dt = tMax / steps;

        for (double p : new double[]{1.0, 2.0, 3.0}) {
            final double[] y = new double[steps + 1];
            for (int i = 0; i <= steps; ++i)
                y[i] = Math.pow((double) i * dt, p);

            for (double a : new double[]{0.0, 0.25, 0.6, 1.0}) {
                final double calculated =
                        RiemannLiouvilleIntegral.integrate(y, a, dt);

                final double expected = new GammaFunction().value(p + 1.0)
                        / new GammaFunction().value(p + 1.0 + a)
                        * Math.pow(tMax, p + a);

                final double tol = (p == 1.0) ? 1e-12 : 5e-5;
                if (Math.abs(calculated - expected) > tol)
                    boostError("failed to reproduce fractional integral of t^p"
                            + "\n    p:          " + p
                            + "\n    alpha:      " + a
                            + "\n    calculated: " + calculated
                            + "\n    expected:   " + expected
                            + "\n    tolerance:  " + tol);

                // the engine only ever integrates complex-valued vectors in
                // production; check the complex path directly
                final Complex[] yc = new Complex[y.length];
                for (int i = 0; i <= steps; ++i)
                    yc[i] = new Complex(1.0, 1.0).multiply(y[i]);

                final Complex calculatedC =
                        RiemannLiouvilleIntegral.integrate(yc, a, dt);

                final Complex expectedC = new Complex(1.0, 1.0)
                        .multiply(expected);

                if (calculatedC.subtract(expectedC).abs() > tol)
                    boostError("failed to reproduce complex fractional integral of t^p"
                            + "\n    p:          " + p
                            + "\n    alpha:      " + a
                            + "\n    calculated: " + calculatedC
                            + "\n    expected:   " + expectedC
                            + "\n    tolerance:  " + tol);
            }
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 3. testRiemannLiouvilleWeightReuse
    // ------------------------------------------------------------------

    @Test
    void testRiemannLiouvilleWeightReuse() {
        System.out.println(
                "Testing reusable Riemann-Liouville quadrature weights...");

        // testRiemannLiouvilleIntegral above already validates the arithmetic
        // against the closed form, and now reaches it through this class, so it
        // is not repeated here. What is new is that one object is integrated
        // many times: it has to stay stateless, so that a cached set of weights
        // gives exactly what a set built for that one call would have given.
        //
        // steps = 1 leaves the interior loop empty, steps = 2 runs it once and
        // reaches index 0 of the power table, steps = 256 is the engine default.
        for (int steps : new int[]{1, 2, 256}) {
            final double[] real = new double[steps + 1];
            final Complex[] complex = new Complex[steps + 1];

            for (int i = 0; i <= steps; ++i) {
                final double x = (double) i / steps;
                real[i] = Math.exp(-0.7 * x) * (1.0 + x * x);
                complex[i] = new Complex(real[i], Math.sin(37.0 * x));
            }

            // 0 and 1 are the degenerate orders; 0.25 is the fractional one
            // the engine asks for at H = 0.25
            for (double alpha : new double[]{0.0, 0.25, 1.0}) {
                final RiemannLiouvilleWeights reused =
                        new RiemannLiouvilleWeights(alpha, steps);

                // the spacing varies with the maturity while the weights do
                // not, so a reused object has to survive a change of dt
                for (double dt : new double[]{1e-3, 0.5}) {
                    final double calculated = reused.integrate(real, dt);
                    final double expected = RiemannLiouvilleIntegral
                            .integrate(real, alpha, dt);

                    if (calculated != expected)
                        boostError("reused weights do not reproduce freshly built ones"
                                + "\n    alpha:      " + alpha
                                + "\n    steps:      " + steps
                                + "\n    dt:         " + dt
                                + "\n    calculated: " + calculated
                                + "\n    expected:   " + expected);

                    // production only ever integrates complex vectors
                    final Complex calculatedC = reused.integrate(complex, dt);
                    final Complex expectedC = RiemannLiouvilleIntegral
                            .integrate(complex, alpha, dt);

                    if (calculatedC.getReal() != expectedC.getReal()
                            || calculatedC.getImaginary()
                            != expectedC.getImaginary())
                        boostError("reused weights do not reproduce freshly built ones "
                                + "on the complex path"
                                + "\n    alpha:      " + alpha
                                + "\n    steps:      " + steps
                                + "\n    dt:         " + dt
                                + "\n    calculated: " + calculatedC
                                + "\n    expected:   " + expectedC);
                }
            }
        }

        final int steps = 64;
        final double dt = 0.01;

        final double[] y = new double[steps + 1];
        for (int i = 0; i <= steps; ++i)
            y[i] = 1.0 + Math.sin((double) i);

        // An oracle for the weights themselves rather than for the integral: at
        // alpha = 1 they have to be exactly the trapezoidal rule, which pins
        // down the formula more tightly than agreement with I^a t^p to 5e-5.
        double trapezoid = 0.5 * (y[0] + y[steps]);
        for (int i = 1; i < steps; ++i)
            trapezoid += y[i];
        trapezoid *= dt;

        checkClosePercent(
                new RiemannLiouvilleWeights(1.0, steps).integrate(y, dt),
                trapezoid, 1e-10,
                "Riemann-Liouville weights do not reproduce the trapezoidal rule");

        // steps = 0 would underflow (steps - 1) into an out-of-bounds index, and
        // a grid that does not match the weights is the mistake a caller
        // reusing a cached object will make
        assertThrows(IllegalArgumentException.class,
                () -> new RiemannLiouvilleWeights(0.6, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RiemannLiouvilleWeights(-1e-8, steps));
        assertThrows(IllegalArgumentException.class,
                () -> new RiemannLiouvilleWeights(0.6, steps - 1)
                        .integrate(y, dt));
        assertThrows(IllegalArgumentException.class,
                () -> new RiemannLiouvilleWeights(0.6, steps)
                        .integrate(y, 0.0));

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 4. testWeightCacheInvalidation
    // ------------------------------------------------------------------

    @Test
    void testWeightCacheInvalidation() {
        System.out.println(
                "Testing that the rough Heston engine drops cached quadrature weights "
                        + "when the Hurst exponent moves...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.04, kappa = 1.5, theta = 0.04, sigma = 0.3,
                rho = -0.7;

        final PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final double t = 1.0;

        for (Approximation approximation : new Approximation[]{
                Approximation.AdamsPredictorCorrector,
                Approximation.Pade}) {

            final RoughHestonModel model = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                            rho), 0.1);
            final AnalyticRoughHestonEngine engine =
                    new AnalyticRoughHestonEngine(model, 128, 64,
                            approximation);

            final double firstPrice =
                    engine.priceVanillaPayoff(payoff, t);

            // The fractional weights are keyed on 1 - (H + 1/2); moving H has
            // to rebuild them, or the engine keeps pricing the old model.
            final Array params = model.params();
            params.set(5, 0.3);
            model.setParams(params);

            final double movedPrice =
                    engine.priceVanillaPayoff(payoff, t);

            final RoughHestonModel referenceModel = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                            rho), 0.3);
            final AnalyticRoughHestonEngine reference =
                    new AnalyticRoughHestonEngine(referenceModel, 128, 64,
                            approximation);
            final double referencePrice =
                    reference.priceVanillaPayoff(payoff, t);

            if (Math.abs(movedPrice - referencePrice) > 1e-12)
                boostError("stale quadrature weights after a change of the "
                        + "Hurst exponent"
                        + "\n    reused engine: " + movedPrice
                        + "\n    fresh engine:  " + referencePrice);

            if (Math.abs(movedPrice - firstPrice) < 1e-6)
                boostError("the Hurst exponent did not move the price, so the "
                        + "test cannot detect a stale cache"
                        + "\n    H = 0.1: " + firstPrice
                        + "\n    H = 0.3: " + movedPrice);

            engine.unregisterWithAll();
            reference.unregisterWithAll();
            model.unregisterWithAll();
            referenceModel.unregisterWithAll();
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 5. testEquivalenceWithHestonModel
    // ------------------------------------------------------------------

    @Test
    void testEquivalenceWithHestonModel() {
        System.out.println(
                "Testing rough Heston engine against the classical Heston "
                        + "engine for H = 0.5...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.05, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        // three parameter sets: the original strongly-correlated case, one
        // with near-zero correlation, and one with a small vol-of-vol
        final double[][] paramSets = new double[][]{
                {0.05, 1.5, 0.04, 0.5, -0.75},
                {0.05, 1.5, 0.04, 0.5, 0.02},
                {0.05, 1.5, 0.04, 0.1, -0.75},
        };

        for (double[] p : paramSets) {
            final double v0 = p[0], kappa = p[1], theta = p[2],
                    sigma = p[3], rho = p[4];

            final RoughHestonModel roughModel = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                            rho), 0.5);
            final AnalyticRoughHestonEngine roughEngine =
                    new AnalyticRoughHestonEngine(roughModel, 128, 512);

            final HestonModel hestonModel = new HestonModel(
                    new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                            rho));
            final AnalyticHestonEngine hestonEngine =
                    new AnalyticHestonEngine(hestonModel, 192);

            // characteristic function comparison along the integration contour
            for (double u : new double[]{0.5, 1.0, 5.0, 10.0, 25.0, 50.0}) {
                for (double t : new double[]{0.5, 2.0}) {
                    final Complex z = new Complex(u, -0.5);
                    final Complex calculated = roughEngine.chF(z, t);
                    final Complex expected = hestonEngine.chF(z, t);

                    final double tol = 1e-5;
                    if (calculated.subtract(expected).abs() > tol)
                        boostError("failed to reproduce Heston characteristic "
                                + "function for H = 0.5"
                                + "\n    rho:        " + rho
                                + "\n    sigma:      " + sigma
                                + "\n    u:          " + u
                                + "\n    t:          " + t
                                + "\n    calculated: " + calculated
                                + "\n    expected:   " + expected
                                + "\n    tolerance:  " + tol);
                }
            }

            // price comparison through the full pricing-engine interface
            for (Option.Type optionType : new Option.Type[]{
                    Option.Type.Call, Option.Type.Put}) {
                for (double strike : new double[]{80.0, 90.0, 100.0, 110.0,
                        125.0}) {
                    for (int months : new int[]{6, 12, 24}) {
                        final Date maturity =
                                today.add(new Period(months, TimeUnit.MONTHS));

                        final EuropeanOption option = new EuropeanOption(
                                new PlainVanillaPayoff(optionType, strike),
                                new EuropeanExercise(maturity));

                        option.setPricingEngine(roughEngine);
                        final double calculated = option.NPV();

                        option.setPricingEngine(hestonEngine);
                        final double expected = option.NPV();

                        final double tol = 5e-4;
                        if (Math.abs(calculated - expected) > tol)
                            boostError("failed to reproduce Heston price for "
                                    + "H = 0.5"
                                    + "\n    rho:        " + rho
                                    + "\n    sigma:      " + sigma
                                    + "\n    option:     " + optionType
                                    + "\n    strike:     " + strike
                                    + "\n    maturity:   " + maturity
                                    + "\n    calculated: " + calculated
                                    + "\n    expected:   " + expected
                                    + "\n    tolerance:  " + tol);
                    }
                }
            }

            roughEngine.unregisterWithAll();
            roughModel.unregisterWithAll();
            hestonModel.unregisterWithAll();
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 6. testIntegrationAlgorithmConsistency
    // ------------------------------------------------------------------

    @Test
    void testIntegrationAlgorithmConsistency() {
        System.out.println(
                "Testing consistency of rough Heston prices across Fourier "
                        + "integration algorithms...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.1);

        final int timeSteps = 256;
        final AnalyticRoughHestonEngine laguerreEngine =
                new AnalyticRoughHestonEngine(model, 128, timeSteps);
        final AnalyticRoughHestonEngine lobattoEngine =
                new AnalyticRoughHestonEngine(model,
                        FourierIntegration.gaussLobatto(1e-10, 1e-10, 100000),
                        timeSteps);

        for (double strike : new double[]{75.0, 100.0, 130.0}) {
            for (int months : new int[]{3, 12}) {
                final Date maturity =
                        today.add(new Period(months, TimeUnit.MONTHS));

                final EuropeanOption option = new EuropeanOption(
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        new EuropeanExercise(maturity));

                option.setPricingEngine(laguerreEngine);
                final double laguerreNPV = option.NPV();

                option.setPricingEngine(lobattoEngine);
                final double lobattoNPV = option.NPV();

                final double tol = 1e-6;
                if (Math.abs(laguerreNPV - lobattoNPV) > tol)
                    boostError("Gauss-Laguerre and Gauss-Lobatto rough Heston "
                            + "prices differ"
                            + "\n    strike:        " + strike
                            + "\n    maturity:      " + maturity
                            + "\n    Gauss-Laguerre: " + laguerreNPV
                            + "\n    Gauss-Lobatto:  " + lobattoNPV
                            + "\n    tolerance:      " + tol);
            }
        }

        laguerreEngine.unregisterWithAll();
        lobattoEngine.unregisterWithAll();
        model.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 7. testKnownReferenceValues
    // ------------------------------------------------------------------

    @Test
    void testKnownReferenceValues() {
        System.out.println(
                "Testing rough Heston prices against an independent reference "
                        + "implementation...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.1);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, 128, 512);

        // reference prices from a from-scratch NumPy/SciPy reimplementation,
        // independent of this engine's C++ code, converged to ~1e-4
        final double[][] references = new double[][]{
                {0.25, 75.0, 25.762400},
                {0.25, 100.0, 3.793313},
                {0.25, 130.0, 0.001586},
                {1.00, 75.0, 28.394103},
                {1.00, 100.0, 8.336634},
                {1.00, 130.0, 0.202295},
                {2.00, 75.0, 31.520600},
                {2.00, 100.0, 12.722717},
                {2.00, 130.0, 1.285820},
        };

        for (double[] ref : references) {
            final double calculated = engine.priceVanillaPayoff(
                    new PlainVanillaPayoff(Option.Type.Call, ref[1]),
                    ref[0]);

            final double tol = 5e-4;
            if (Math.abs(calculated - ref[2]) > tol)
                boostError("failed to reproduce independent reference price"
                        + "\n    strike:     " + ref[1]
                        + "\n    maturity:   " + ref[0]
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + ref[2]
                        + "\n    tolerance:  " + tol);
        }

        engine.unregisterWithAll();
        model.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 8. testPutCallParity
    // ------------------------------------------------------------------

    @Test
    void testPutCallParity() {
        System.out.println(
                "Testing put-call parity for the rough Heston engine...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        for (double hurst : new double[]{0.1, 0.3, 0.5}) {
            final RoughHestonModel model = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                            -0.7), hurst);
            final AnalyticRoughHestonEngine engine =
                    new AnalyticRoughHestonEngine(model, 128, 256);

            for (double strike : new double[]{70.0, 100.0, 130.0}) {
                for (double t : new double[]{0.1, 1.0, 2.0}) {
                    final double callPrice = engine.priceVanillaPayoff(
                            new PlainVanillaPayoff(Option.Type.Call, strike),
                            t);
                    final double putPrice = engine.priceVanillaPayoff(
                            new PlainVanillaPayoff(Option.Type.Put, strike),
                            t);

                    final double fwd = s0.currentLink().value()
                            * qTS.currentLink().discount(t)
                            / rTS.currentLink().discount(t);
                    final double expected =
                            (fwd - strike) * rTS.currentLink().discount(t);

                    final double tol = 1e-6;
                    if (Math.abs((callPrice - putPrice) - expected) > tol)
                        boostError("failed put-call parity"
                                + "\n    hurst:      " + hurst
                                + "\n    strike:     " + strike
                                + "\n    maturity:   " + t
                                + "\n    call - put: "
                                + (callPrice - putPrice)
                                + "\n    expected:   " + expected
                                + "\n    tolerance:  " + tol);
                }
            }

            engine.unregisterWithAll();
            model.unregisterWithAll();
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 9. testMonotonicityAndBounds
    // ------------------------------------------------------------------

    @Test
    void testMonotonicityAndBounds() {
        System.out.println(
                "Testing rough Heston call-price monotonicity, convexity and "
                        + "no-arbitrage bounds...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.15);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, 128, 256);

        final double t = 1.0;
        final double fwd = s0.currentLink().value()
                * qTS.currentLink().discount(t)
                / rTS.currentLink().discount(t);

        final double df = rTS.currentLink().discount(t);

        final double[] strikes = new double[]{
                60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0, 140.0};
        final double[] prices = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i)
            prices[i] = engine.priceVanillaPayoff(
                    new PlainVanillaPayoff(Option.Type.Call, strikes[i]), t);

        for (int i = 0; i < strikes.length; ++i) {
            final double lower = Math.max(fwd - strikes[i], 0.0) * df;
            final double upper = fwd * df;

            if (prices[i] < lower - 1e-8 || prices[i] > upper + 1e-8)
                boostError("call price violates no-arbitrage bounds"
                        + "\n    strike: " + strikes[i]
                        + "\n    price:  " + prices[i]
                        + "\n    lower:  " + lower
                        + "\n    upper:  " + upper);

            if (i > 0 && prices[i] > prices[i - 1] + 1e-8)
                boostError("call price is not monotonically decreasing in strike"
                        + "\n    strike: " + strikes[i - 1] + " -> "
                        + strikes[i]
                        + "\n    price:  " + prices[i - 1] + " -> "
                        + prices[i]);

            if (i > 0 && i + 1 < strikes.length) {
                final double secondDiff =
                        prices[i + 1] - 2 * prices[i] + prices[i - 1];
                if (secondDiff < -1e-6)
                    boostError("call price is not convex in strike"
                            + "\n    strike:            " + strikes[i]
                            + "\n    second difference: " + secondDiff);
            }
        }

        engine.unregisterWithAll();
        model.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 10. testInputValidation
    // ------------------------------------------------------------------

    /** Official {@code BOOST_CHECK_EXCEPTION} with a message predicate. */
    private void checkThrowsMessage(String expected, Executable e) {
        try {
            e.execute();
            boostError("expected exception with message containing \""
                    + expected + "\", but none was thrown");
        } catch (RuntimeException ex) {
            final String message = ex.getMessage();
            if (message == null || !message.contains(expected))
                boostError("expected exception with message containing \""
                        + expected + "\", got: " + ex);
        } catch (Throwable t) {
            boostError("expected exception with message containing \""
                    + expected + "\", got: " + t);
        }
    }

    @Test
    void testInputValidation() {
        System.out.println(
                "Testing input validation of the rough Heston engine...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.15);

        final String noTimeSteps = "at least one time step required";
        final String alphaOutOfRange = "must be in (-1, 0)";
        final String nonPositiveMaturity = "maturity must be positive";

        checkThrowsMessage(noTimeSteps,
                () -> new AnalyticRoughHestonEngine(model, 128, 0));

        final FourierIntegration integration =
                FourierIntegration.gaussLaguerre(128);
        checkThrowsMessage(noTimeSteps,
                () -> new AnalyticRoughHestonEngine(model, integration, 0));
        checkThrowsMessage(alphaOutOfRange,
                () -> new AnalyticRoughHestonEngine(model, integration, 256,
                        1e-25, 0.0));
        checkThrowsMessage(alphaOutOfRange,
                () -> new AnalyticRoughHestonEngine(model, integration, 256,
                        1e-25, -1.0));
        checkThrowsMessage(alphaOutOfRange,
                () -> new AnalyticRoughHestonEngine(model, integration, 256,
                        1e-25, 0.5));

        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, 128, 256);

        checkThrowsMessage(nonPositiveMaturity,
                () -> engine.priceVanillaPayoff(
                        new PlainVanillaPayoff(Option.Type.Call, 100.0),
                        0.0));
        checkThrowsMessage(nonPositiveMaturity,
                () -> engine.priceVanillaPayoff(
                        new PlainVanillaPayoff(Option.Type.Call, 100.0),
                        -1.0));

        final EuropeanOption americanOption = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, 100.0),
                new AmericanExercise(today,
                        today.add(new Period(1, TimeUnit.YEARS))));
        americanOption.setPricingEngine(engine);
        checkThrowsMessage("not an European option",
                () -> {
                    americanOption.NPV();
                });

        engine.unregisterWithAll();
        model.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 11. testShortMaturitySkewExplosion
    // ------------------------------------------------------------------

    /**
     * At-the-money implied volatility skew {@code d(sigma_iv)/d(ln K)},
     * official {@code atmSkew} lambda. The risk-free and dividend curves are
     * flat at zero in this case, so the Black discount factor is one.
     */
    private static double atmSkew(AnalyticRoughHestonEngine engine, double t) {
        final double dk = 0.02;

        final double[] iv = new double[2];
        for (int i = 0; i < 2; ++i) {
            final double strike = 100.0 * Math.exp(i == 0 ? -dk : dk);
            final double price = engine.priceVanillaPayoff(
                    new PlainVanillaPayoff(Option.Type.Call, strike), t);
            iv[i] = BlackFormula.blackFormulaImpliedStdDev(
                    Option.Type.Call, strike, 100.0, price, 1.0, 0.0,
                    Double.NaN, 1e-6, 100) / Math.sqrt(t);
        }
        return (iv[1] - iv[0]) / (2 * dk);
    }

    @Test
    void testShortMaturitySkewExplosion() {
        System.out.println(
                "Testing rough Heston short-maturity skew explosion...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double t1 = 0.1, t2 = 0.4;

        // classical Heston (H = 0.5) reference for the "materially less
        // explosion" comparison below
        final RoughHestonModel flatModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.5);
        final AnalyticRoughHestonEngine flatEngine =
                new AnalyticRoughHestonEngine(flatModel, 128, 512);
        final double flatSkewRatio =
                atmSkew(flatEngine, t1) / atmSkew(flatEngine, t2);

        // El Euch, Fukasawa, Rosenbaum: the ATM skew of rough volatility
        // models explodes like T^(H - 1/2) for T -> 0
        for (double hurst : new double[]{0.05, 0.1, 0.3}) {
            final RoughHestonModel model = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                            -0.7), hurst);
            final AnalyticRoughHestonEngine engine =
                    new AnalyticRoughHestonEngine(model, 128, 512);

            final double skewRatio =
                    atmSkew(engine, t1) / atmSkew(engine, t2);

            final double expected = Math.pow(t1 / t2, hurst - 0.5);

            final double tol = 0.15 * expected;
            if (Math.abs(skewRatio - expected) > tol)
                boostError("failed to reproduce short-maturity skew power law"
                        + "\n    hurst:      " + hurst
                        + "\n    skew ratio: " + skewRatio
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);

            // classical Heston must show materially less short-maturity
            // skew steepening than any rough case
            if (flatSkewRatio > 0.5 * (skewRatio + 1.0))
                boostError("classical limit shows unexpected skew explosion"
                        + "\n    hurst:                 " + hurst
                        + "\n    rough skew ratio:      " + skewRatio
                        + "\n    classical skew ratio:  " + flatSkewRatio);

            engine.unregisterWithAll();
            model.unregisterWithAll();
        }

        flatEngine.unregisterWithAll();
        flatModel.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 12. testCalibration
    // ------------------------------------------------------------------

    @Test
    void testCalibration() {
        System.out.println(
                "Testing rough Heston calibration to a synthetic surface...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar calendar = new NullCalendar();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.0225;
        final double kappa = 0.6;
        final double theta = 0.04;
        final double sigma = 0.35;
        final double rho = -0.65;
        final double hurst = 0.12;

        // the time steps must keep the fractional Adams scheme stable up to
        // frequencies where the characteristic function has decayed
        // (u ~ 0.35 N/T)
        final int integrationOrder = 64, timeSteps = 256;

        // synthetic market surface generated from known parameters
        final RoughHestonModel trueModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho),
                hurst);
        final AnalyticRoughHestonEngine trueEngine =
                new AnalyticRoughHestonEngine(trueModel, integrationOrder,
                        timeSteps);

        final List<CalibrationHelper> helpers = new ArrayList<>();
        for (int months : new int[]{3, 6, 12, 24}) {
            final Period maturity = new Period(months, TimeUnit.MONTHS);
            final Date maturityDate = today.add(maturity);
            final double t = dc.yearFraction(today, maturityDate);

            final double fwd = s0.currentLink().value()
                    * qTS.currentLink().discount(maturityDate)
                    / rTS.currentLink().discount(maturityDate);

            final double df = rTS.currentLink().discount(maturityDate);

            for (double strike : new double[]{80.0, 90.0, 100.0, 110.0,
                    120.0}) {
                final double price = trueEngine.priceVanillaPayoff(
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        maturityDate);
                final double impliedVol =
                        BlackFormula.blackFormulaImpliedStdDev(
                                Option.Type.Call, strike, fwd, price, df,
                                0.0, Double.NaN, 1e-6, 100) / Math.sqrt(t);

                // implied-vol errors keep the objective well scaled; relative
                // price errors can strand the optimizer in local minima
                helpers.add(new HestonModelHelper(
                        maturity, calendar, s0, strike,
                        new Handle<>(new SimpleQuote(impliedVol), true),
                        rTS, qTS, CalibrationErrorType.ImpliedVolError));
            }
        }

        // calibration starts from deliberately wrong parameters
        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 1.0, 0.02, 0.25, -0.4),
                0.2);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, integrationOrder,
                        timeSteps);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).setPricingEngine(engine);

        final LevenbergMarquardt om =
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        model.calibrate(helpers, om,
                new EndCriteria(400, 40, 1.0e-8, 1.0e-8, 1.0e-8));

        double sse = 0.0;
        for (CalibrationHelper helper : helpers) {
            final double error = helper.calibrationError();
            sse += error * error;
        }

        final double expectedSse = 1e-8;
        if (sse > expectedSse)
            boostError("failed to calibrate rough Heston model to a "
                    + "synthetic surface"
                    + "\n    sse:      " + sse
                    + "\n    expected: less than " + expectedSse
                    + "\n    v0:    " + model.v0() + " vs " + v0
                    + "\n    kappa: " + model.kappa() + " vs " + kappa
                    + "\n    theta: " + model.theta() + " vs " + theta
                    + "\n    sigma: " + model.sigma() + " vs " + sigma
                    + "\n    rho:   " + model.rho() + " vs " + rho
                    + "\n    hurst: " + model.hurst() + " vs " + hurst);

        // the Hurst exponent controls the skew term structure and should be
        // identified well
        final double hurstTol = 0.02;
        if (Math.abs(model.hurst() - hurst) > hurstTol)
            boostError("failed to recover the Hurst exponent"
                    + "\n    calculated: " + model.hurst()
                    + "\n    expected:   " + hurst
                    + "\n    tolerance:  " + hurstTol);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).unregisterWithAll();
        engine.unregisterWithAll();
        trueEngine.unregisterWithAll();
        model.unregisterWithAll();
        trueModel.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 13. testPadeRiccatiAsymptotics
    // ------------------------------------------------------------------

    @Test
    void testPadeRiccatiAsymptotics() {
        System.out.println(
                "Testing short- and long-time asymptotics of the Pade Riccati "
                        + "solution...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double kappa = 0.3, sigma = 0.4, rho = -0.7;

        for (double hurst : new double[]{0.1, 0.3}) {
            final double a = hurst + 0.5;

            final RoughHestonModel model = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, 0.04, kappa, 0.04, sigma,
                            rho), hurst);
            final AnalyticRoughHestonEngine engine =
                    new AnalyticRoughHestonEngine(model, 128, 256,
                            Approximation.Pade);

            for (double u : new double[]{0.5, 2.0, 5.0}) {
                final Complex z = new Complex(u, -0.5);
                final Complex i = new Complex(0.0, 1.0);

                // short-time leading term h(z, t) -> b_1 t^a with
                // b_1 = -z * (z + i) / (2 * Gamma(1 + a)); check the ratio
                // h / t^a
                final Complex b1 = z.multiply(z.add(i))
                        .multiply(-0.5 / new GammaFunction().value(1.0 + a));

                final double tShort = 1e-7;
                final Complex hShort =
                        engine.riccatiSolution(z, tShort);
                final Complex ratio =
                        hShort.divide(Math.pow(tShort, a));

                final double shortTol = 1e-3;
                if (ratio.subtract(b1).abs() / b1.abs() > shortTol)
                    boostError("Pade Riccati short-time limit off"
                            + "\n    u:          " + u
                            + "\n    hurst:      " + hurst
                            + "\n    h / t^a:    " + ratio
                            + "\n    b_1:        " + b1
                            + "\n    rel. tol.:  " + shortTol);

                // long-time limit h(z, t) -> r_- / sigma, the attracting root
                // of the Riccati quadratic
                final Complex w = new Complex(kappa / sigma, 0.0)
                        .subtract(i.multiply(z.multiply(rho)));
                final Complex A = z.multiply(z.add(i))
                        .add(w.multiply(w)).sqrt();
                final Complex g0 = w.subtract(A).divide(sigma);

                final double tLong = 1e8;
                final Complex hLong =
                        engine.riccatiSolution(z, tLong);

                final double longTol = 1e-3;
                if (hLong.subtract(g0).abs() / g0.abs() > longTol)
                    boostError("Pade Riccati long-time limit off"
                            + "\n    u:          " + u
                            + "\n    hurst:      " + hurst
                            + "\n    h(inf):     " + hLong
                            + "\n    r_-/sigma:  " + g0
                            + "\n    rel. tol.:  " + longTol);
            }

            engine.unregisterWithAll();
            model.unregisterWithAll();
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 14. testPadeTrivialContour
    // ------------------------------------------------------------------

    @Test
    void testPadeTrivialContour() {
        System.out.println(
                "Testing the Pade Riccati solution against the exact solution "
                        + "on the contour where the Riccati equation "
                        + "degenerates...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        // at z = 0 or z = -i, the Riccati forcing term c0 = -z * (z + i) / 2
        // vanishes identically, so h' = c_1 * h + c_2 * h^2 with h(0) = 0 has
        // the exact, Pade-construction-independent solution h = 0.
        for (double hurst : new double[]{0.05, 0.2, 0.4}) {
            final RoughHestonModel model = new RoughHestonModel(
                    new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                            -0.7), hurst);
            final AnalyticRoughHestonEngine engine =
                    new AnalyticRoughHestonEngine(model, 128, 256,
                            Approximation.Pade);

            for (Complex z : new Complex[]{
                    new Complex(0.0, 0.0), new Complex(0.0, -1.0)}) {
                for (double t : new double[]{0.01, 1.0, 5.0}) {
                    final Complex h = engine.riccatiSolution(z, t);

                    final double tol = 1e-10;
                    if (h.abs() > tol)
                        boostError("Pade Riccati solution is not exactly zero "
                                + "on the degenerate contour"
                                + "\n    hurst:      " + hurst
                                + "\n    z:          " + z
                                + "\n    maturity:   " + t
                                + "\n    h:          " + h
                                + "\n    tolerance:  " + tol);
                }
            }

            engine.unregisterWithAll();
            model.unregisterWithAll();
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 15. testPadeAgainstAdamsRiccati
    // ------------------------------------------------------------------

    @Test
    void testPadeAgainstAdamsRiccati() {
        System.out.println(
                "Testing the Pade Riccati solution against the Adams "
                        + "reference...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        // The rational approximation is sharpest for small Hurst exponents,
        // the empirically relevant regime; rho is swept through both signs
        // since the long-time coefficients hinge on a root selection
        // (w -/+ A) that could plausibly flip sign for some
        // (kappa, sigma, rho) combinations
        for (double hurst : new double[]{0.05, 0.1, 0.2}) {
            for (double rho : new double[]{-0.7, 0.3}) {
                final RoughHestonModel model = new RoughHestonModel(
                        new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                                rho), hurst);

                final AnalyticRoughHestonEngine adams =
                        new AnalyticRoughHestonEngine(model, 128, 1024,
                                Approximation.AdamsPredictorCorrector);
                final AnalyticRoughHestonEngine pade =
                        new AnalyticRoughHestonEngine(model, 128, 256,
                                Approximation.Pade);

                double maxError = 0.0;
                // pricing contour z = u - i/2 over the calibration-relevant
                // band
                for (double u : new double[]{0.5, 1.0, 2.0, 5.0, 10.0, 20.0}) {
                    for (double t : new double[]{0.25, 1.0, 2.0}) {
                        final Complex z = new Complex(u, -0.5);

                        final Complex ref = adams.riccatiSolution(z, t);
                        final Complex approx = pade.riccatiSolution(z, t);

                        maxError = Math.max(maxError,
                                approx.subtract(ref).abs() / ref.abs());
                    }
                }

                final double tol = 2e-2;
                if (maxError > tol)
                    boostError("Pade Riccati solution deviates from the Adams "
                            + "reference"
                            + "\n    hurst:          " + hurst
                            + "\n    rho:            " + rho
                            + "\n    max rel. error: " + maxError
                            + "\n    tolerance:      " + tol);

                adams.unregisterWithAll();
                pade.unregisterWithAll();
                model.unregisterWithAll();
            }
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 16. testPadeAgainstAdamsPricing
    // ------------------------------------------------------------------

    @Test
    void testPadeAgainstAdamsPricing() {
        System.out.println(
                "Testing Pade rough Heston prices against the Adams "
                        + "reference...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        for (double hurst : new double[]{0.05, 0.1, 0.2}) {
            for (double rho : new double[]{-0.7, 0.3}) {
                final RoughHestonModel model = new RoughHestonModel(
                        new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                                rho), hurst);

                final AnalyticRoughHestonEngine adams =
                        new AnalyticRoughHestonEngine(model, 128, 512,
                                Approximation.AdamsPredictorCorrector);
                final AnalyticRoughHestonEngine pade =
                        new AnalyticRoughHestonEngine(model, 128, 256,
                                Approximation.Pade);

                for (Option.Type optionType : new Option.Type[]{
                        Option.Type.Call, Option.Type.Put}) {
                    for (double strike : new double[]{80.0, 90.0, 100.0,
                            110.0, 120.0}) {
                        for (double t : new double[]{0.25, 1.0, 2.0}) {
                            final PlainVanillaPayoff payoff =
                                    new PlainVanillaPayoff(optionType, strike);

                            final double ref =
                                    adams.priceVanillaPayoff(payoff, t);
                            final double approx =
                                    pade.priceVanillaPayoff(payoff, t);

                            // a few basis points of the spot on prices of
                            // order 1 to 30 for the strongly negative
                            // correlation the approximation is designed for
                            // (rho = -0.7); the wider tolerance accommodates
                            // the observed ~4x larger error at rho = 0.3,
                            // still a few percent of the spot, not a sign of
                            // a wrong root being picked
                            final double tol = 5e-2;
                            if (Math.abs(approx - ref) > tol)
                                boostError("Pade price deviates from the "
                                        + "Adams reference"
                                        + "\n    hurst:      " + hurst
                                        + "\n    rho:        " + rho
                                        + "\n    option:     " + optionType
                                        + "\n    strike:     " + strike
                                        + "\n    maturity:   " + t
                                        + "\n    Pade:       " + approx
                                        + "\n    Adams:      " + ref
                                        + "\n    tolerance:  " + tol);
                        }
                    }
                }

                adams.unregisterWithAll();
                pade.unregisterWithAll();
                model.unregisterWithAll();
            }
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 17. testPadeHestonLimit
    // ------------------------------------------------------------------

    @Test
    void testPadeHestonLimit() {
        System.out.println(
                "Testing the Pade rough Heston engine against classical "
                        + "Heston for H = 0.5...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.05, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.05, kappa = 1.5, theta = 0.04, sigma = 0.5,
                rho = -0.75;

        final RoughHestonModel roughModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho),
                0.5);
        final AnalyticRoughHestonEngine padeEngine =
                new AnalyticRoughHestonEngine(roughModel, 128, 256,
                        Approximation.Pade);

        final HestonModel hestonModel = new HestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                        rho));
        final AnalyticHestonEngine hestonEngine =
                new AnalyticHestonEngine(hestonModel, 192);

        // the (3, 3) rational approximation is built for the rough regime and
        // degrades towards the classical limit
        for (Option.Type optionType : new Option.Type[]{
                Option.Type.Call, Option.Type.Put}) {
            for (double strike : new double[]{80.0, 90.0, 100.0, 110.0,
                    125.0}) {
                for (int months : new int[]{6, 12, 24}) {
                    final Date maturity =
                            today.add(new Period(months, TimeUnit.MONTHS));

                    final EuropeanOption option = new EuropeanOption(
                            new PlainVanillaPayoff(optionType, strike),
                            new EuropeanExercise(maturity));

                    option.setPricingEngine(padeEngine);
                    final double calculated = option.NPV();

                    option.setPricingEngine(hestonEngine);
                    final double expected = option.NPV();

                    final double tol = Math.max(0.05, 0.04 * expected);
                    if (Math.abs(calculated - expected) > tol)
                        boostError("Pade limit fails to match classical Heston"
                                + "\n    option:     " + optionType
                                + "\n    strike:     " + strike
                                + "\n    maturity:   " + maturity
                                + "\n    Pade:       " + calculated
                                + "\n    Heston:     " + expected
                                + "\n    tolerance:  " + tol);
                }
            }
        }

        padeEngine.unregisterWithAll();
        roughModel.unregisterWithAll();
        hestonModel.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 18. testPadeMonotonicityAndBounds
    // ------------------------------------------------------------------

    @Test
    void testPadeMonotonicityAndBounds() {
        System.out.println(
                "Testing no-arbitrage bounds of the Pade engine...");

        // put-call parity is exact by construction of priceVanillaPayoff (the
        // call and put branches share cvValue + h_cv and differ only by the
        // constant (fwd - strike) * df), independently of which route
        // computes chF; it is already covered for that shared code path by
        // the Adams testPutCallParity, so is not repeated here. Monotonicity
        // and convexity, in contrast, depend on chF actually being
        // numerically well-behaved across the whole integration contour,
        // which is specific to the (3, 3) approximation and worth checking
        // directly.

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4, -0.7),
                0.1);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, 128, 256,
                        Approximation.Pade);

        // call prices stay monotone, convex and within the no-arbitrage box
        final double t = 1.0;
        final double fwd = s0.currentLink().value()
                * qTS.currentLink().discount(t)
                / rTS.currentLink().discount(t);
        final double df = rTS.currentLink().discount(t);

        final double[] strikes = new double[]{
                70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0};
        final double[] prices = new double[strikes.length];
        for (int i = 0; i < strikes.length; ++i)
            prices[i] = engine.priceVanillaPayoff(
                    new PlainVanillaPayoff(Option.Type.Call, strikes[i]), t);

        for (int i = 0; i < strikes.length; ++i) {
            final double lower = Math.max(fwd - strikes[i], 0.0) * df;
            final double upper = fwd * df;

            if (prices[i] < lower - 1e-8 || prices[i] > upper + 1e-8)
                boostError("Pade call price violates no-arbitrage bounds"
                        + "\n    strike: " + strikes[i]
                        + "\n    price:  " + prices[i]);

            if (i > 0 && prices[i] > prices[i - 1] + 1e-8)
                boostError("Pade call price not decreasing in strike"
                        + "\n    strike: " + strikes[i - 1] + " -> "
                        + strikes[i]);

            if (i > 0 && i + 1 < strikes.length) {
                final double secondDiff =
                        prices[i + 1] - 2 * prices[i] + prices[i - 1];
                if (secondDiff < -1e-6)
                    boostError("Pade call price not convex in strike"
                            + "\n    strike:            " + strikes[i]
                            + "\n    second difference: " + secondDiff);
            }
        }

        engine.unregisterWithAll();
        model.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 19. testPadeCalibration
    // ------------------------------------------------------------------

    @Test
    void testPadeCalibration() {
        System.out.println(
                "Testing rough Heston calibration through the fast Pade "
                        + "engine...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar calendar = new NullCalendar();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.0225, kappa = 0.6, theta = 0.04;
        final double sigma = 0.35, rho = -0.65, hurst = 0.12;

        final int integrationOrder = 64, timeSteps = 256;

        // the synthetic surface is generated with the same Pade engine, so
        // the round trip isolates the optimizer's ability to recover
        // parameters from the fast approximation itself
        final RoughHestonModel trueModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho),
                hurst);
        final AnalyticRoughHestonEngine trueEngine =
                new AnalyticRoughHestonEngine(trueModel, integrationOrder,
                        timeSteps, Approximation.Pade);

        final List<CalibrationHelper> helpers = new ArrayList<>();
        for (int months : new int[]{3, 6, 12, 24}) {
            final Period maturity = new Period(months, TimeUnit.MONTHS);
            final Date maturityDate = today.add(maturity);
            final double t = dc.yearFraction(today, maturityDate);

            final double fwd = s0.currentLink().value()
                    * qTS.currentLink().discount(maturityDate)
                    / rTS.currentLink().discount(maturityDate);
            final double df = rTS.currentLink().discount(maturityDate);

            for (double strike : new double[]{80.0, 90.0, 100.0, 110.0,
                    120.0}) {
                final double price = trueEngine.priceVanillaPayoff(
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        maturityDate);
                final double impliedVol =
                        BlackFormula.blackFormulaImpliedStdDev(
                                Option.Type.Call, strike, fwd, price, df,
                                0.0, Double.NaN, 1e-6, 100) / Math.sqrt(t);

                helpers.add(new HestonModelHelper(
                        maturity, calendar, s0, strike,
                        new Handle<>(new SimpleQuote(impliedVol), true),
                        rTS, qTS, CalibrationErrorType.ImpliedVolError));
            }
        }

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 1.0, 0.02, 0.25, -0.4),
                0.2);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, integrationOrder,
                        timeSteps, Approximation.Pade);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).setPricingEngine(engine);

        final LevenbergMarquardt om =
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        model.calibrate(helpers, om,
                new EndCriteria(400, 40, 1.0e-8, 1.0e-8, 1.0e-8));

        double sse = 0.0;
        for (CalibrationHelper helper : helpers) {
            final double error = helper.calibrationError();
            sse += error * error;
        }

        final double expectedSse = 1e-8;
        if (sse > expectedSse)
            boostError("failed to calibrate the Pade rough Heston model"
                    + "\n    sse:      " + sse
                    + "\n    expected: less than " + expectedSse
                    + "\n    v0:    " + model.v0() + " vs " + v0
                    + "\n    kappa: " + model.kappa() + " vs " + kappa
                    + "\n    theta: " + model.theta() + " vs " + theta
                    + "\n    sigma: " + model.sigma() + " vs " + sigma
                    + "\n    rho:   " + model.rho() + " vs " + rho
                    + "\n    hurst: " + model.hurst() + " vs " + hurst);

        final double hurstTol = 0.02;
        if (Math.abs(model.hurst() - hurst) > hurstTol)
            boostError("failed to recover the Hurst exponent through Pade"
                    + "\n    calculated: " + model.hurst()
                    + "\n    expected:   " + hurst
                    + "\n    tolerance:  " + hurstTol);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).unregisterWithAll();
        engine.unregisterWithAll();
        trueEngine.unregisterWithAll();
        model.unregisterWithAll();
        trueModel.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 20. testFractionalKernelApproximation
    // ------------------------------------------------------------------

    /**
     * Relative L1 error of the kernel approximation on {@code (0, tMax]}.
     * K is integrable at the origin, so this needs no cut-off and is free of
     * any time grid.
     */
    private static double kernelL1Error(
            FractionalKernelApproximation kernel, double alpha, double tMax) {
        final int m = 6, q = 4000;

        double error = 0.0;
        for (int j = 0; j < q; ++j) {
            final double u = (j + 0.5) / q;
            // t = tMax u^m concentrates the nodes near the singularity
            final double t = tMax * Math.pow(u, (double) m);
            final double weight =
                    tMax * m * Math.pow(u, (double) (m - 1)) / q;

            final double exact = Math.pow(t, alpha - 1.0)
                    / new GammaFunction().value(alpha);

            error += weight * Math.abs(kernel.value(t) - exact);
        }

        return error / (Math.pow(tMax, alpha)
                / new GammaFunction().value(1.0 + alpha));
    }

    @Test
    void testFractionalKernelApproximation() {
        System.out.println(
                "Testing the sum-of-exponentials approximation of the "
                        + "fractional kernel...");

        final double tMax = 1.0;

        for (double hurst : new double[]{0.05, 0.1, 0.3}) {
            final double alpha = hurst + 0.5;

            double previousError = Double.MAX_VALUE;
            for (int n : new int[]{10, 20, 40, 80}) {
                final FractionalKernelApproximation kernel =
                        new FractionalKernelApproximation(alpha, n, tMax);

                if (kernel.size() != n)
                    boostError("wrong number of kernel nodes"
                            + "\n    calculated: " + kernel.size()
                            + "\n    expected:   " + n);

                // Non-negativity is what keeps K^n completely monotone, and
                // with it the lifted model well posed
                for (int i = 0; i < n; ++i) {
                    if (kernel.weights().get(i) < 0.0
                            || kernel.rates().get(i) < 0.0)
                        boostError("negative kernel node"
                                + "\n    index:  " + i
                                + "\n    weight: " + kernel.weights().get(i)
                                + "\n    rate:   " + kernel.rates().get(i));

                    if (i > 0 && kernel.rates().get(i)
                            <= kernel.rates().get(i - 1))
                        boostError("kernel rates are not increasing"
                                + "\n    index: " + i
                                + "\n    rates: "
                                + kernel.rates().get(i - 1) + " -> "
                                + kernel.rates().get(i));
                }

                final double error = kernelL1Error(kernel, alpha, tMax);

                // The measured rate is n^(-1.55), a factor of 2.9 per
                // doubling; 1.7 leaves room at the coarse end
                final double expected = previousError / 1.7;
                if (error > expected)
                    boostError("kernel approximation is not converging in n"
                            + "\n    hurst:      " + hurst
                            + "\n    factors:    " + n
                            + "\n    L1 error:   " + error
                            + "\n    expected:   less than " + expected);

                previousError = error;
            }
        }

        // At alpha = 1 the measure is a point mass at the origin, which the
        // leading interval absorbs entirely: the construction collapses to
        // the single node (1, 0) and reproduces K exactly, with no
        // special-casing
        final FractionalKernelApproximation classical =
                new FractionalKernelApproximation(1.0, 20, tMax);

        final double tol = 10 * Math.ulp(1.0);
        if (Math.abs(classical.weights().get(0) - 1.0) > tol
                || classical.rates().get(0) != 0.0)
            boostError("alpha = 1 does not collapse to the single node (1, 0)"
                    + "\n    weight: " + classical.weights().get(0)
                    + "\n    rate:   " + classical.rates().get(0));

        for (int i = 1; i < classical.size(); ++i)
            if (Math.abs(classical.weights().get(i)) > tol)
                boostError("alpha = 1 leaves a non-vanishing node"
                        + "\n    index:  " + i
                        + "\n    weight: " + classical.weights().get(i));

        for (double t : new double[]{0.1, 0.5, 1.0})
            if (Math.abs(classical.value(t) - 1.0) > tol)
                boostError("alpha = 1 does not reproduce the constant kernel"
                        + "\n    t:          " + t
                        + "\n    calculated: " + classical.value(t)
                        + "\n    expected:   1");

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 21. testLiftedAgainstAdamsRiccati
    // ------------------------------------------------------------------

    @Test
    void testLiftedAgainstAdamsRiccati() {
        System.out.println(
                "Testing convergence of the lifted Riccati solution to the "
                        + "Adams reference...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        // Unlike Pade, the lifted route has a convergence parameter, so what
        // is asserted is not closeness at a fixed n but that the error keeps
        // falling as n grows
        for (double hurst : new double[]{0.05, 0.1, 0.2}) {
            for (double rho : new double[]{-0.7, 0.3}) {
                final RoughHestonModel model = new RoughHestonModel(
                        new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                                rho), hurst);

                final AnalyticRoughHestonEngine adams =
                        new AnalyticRoughHestonEngine(model, 128, 1024,
                                Approximation.AdamsPredictorCorrector);

                double previousError = Double.MAX_VALUE;
                for (int n : new int[]{10, 40, 160}) {
                    final AnalyticRoughHestonEngine lifted =
                            new AnalyticRoughHestonEngine(model, 128, 512,
                                    Approximation.Lifted, n);

                    double maxError = 0.0;
                    for (double u : new double[]{0.5, 1.0, 2.0, 5.0, 10.0,
                            20.0}) {
                        for (double t : new double[]{0.25, 1.0, 2.0}) {
                            final Complex z = new Complex(u, -0.5);

                            final Complex ref =
                                    adams.riccatiSolution(z, t);
                            final Complex approx =
                                    lifted.riccatiSolution(z, t);

                            maxError = Math.max(maxError,
                                    approx.subtract(ref).abs() / ref.abs());
                        }
                    }

                    // A factor of 16 in n at the measured rate n^(-1.5) is
                    // about 64; 6 is a wide margin on the worst contour point
                    final double expected = previousError / 6.0;
                    if (maxError > expected)
                        boostError("lifted Riccati solution is not converging "
                                + "to the Adams reference"
                                + "\n    hurst:          " + hurst
                                + "\n    rho:            " + rho
                                + "\n    factors:        " + n
                                + "\n    max rel. error: " + maxError
                                + "\n    expected:       less than "
                                + expected);

                    previousError = maxError;

                    lifted.unregisterWithAll();
                }

                // And the finest approximation is genuinely close
                final double tol = 5e-3;
                if (previousError > tol)
                    boostError("lifted Riccati solution deviates from the "
                            + "Adams reference"
                            + "\n    hurst:          " + hurst
                            + "\n    rho:            " + rho
                            + "\n    max rel. error: " + previousError
                            + "\n    tolerance:      " + tol);

                adams.unregisterWithAll();
                model.unregisterWithAll();
            }
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 22. testLiftedHestonLimit
    // ------------------------------------------------------------------

    @Test
    void testLiftedHestonLimit() {
        System.out.println(
                "Testing the lifted rough Heston engine against classical "
                        + "Heston for H = 0.5...");

        // At H = 0.5 the kernel is constant, the surviving factor has x = 0
        // and the integrator degenerates to the trapezoidal rule, so the
        // lifted route is not an approximation here at all - hence a much
        // tighter tolerance than the Pade equivalent

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.05, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.05, kappa = 1.5, theta = 0.04, sigma = 0.5,
                rho = -0.75;

        final RoughHestonModel roughModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho),
                0.5);
        final AnalyticRoughHestonEngine lifted =
                new AnalyticRoughHestonEngine(roughModel, 128, 256,
                        Approximation.Lifted);

        final HestonModel hestonModel = new HestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma,
                        rho));
        final AnalyticHestonEngine classical =
                new AnalyticHestonEngine(hestonModel, 192);

        // Calls only: puts differ by an exact constant in priceVanillaPayoff
        for (double strike : new double[]{80.0, 90.0, 100.0, 110.0, 125.0}) {
            for (int months : new int[]{6, 12, 24}) {
                final Date maturity =
                        today.add(new Period(months, TimeUnit.MONTHS));
                final PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, strike);

                final EuropeanOption option = new EuropeanOption(payoff,
                        new EuropeanExercise(maturity));
                option.setPricingEngine(classical);
                final double ref = option.NPV();

                final double approx =
                        lifted.priceVanillaPayoff(payoff, maturity);

                final double tol = 5e-4;
                if (Math.abs(approx - ref) > tol)
                    boostError("lifted engine does not reproduce classical "
                            + "Heston at H = 0.5"
                            + "\n    strike:     " + strike
                            + "\n    maturity:   " + months + "M"
                            + "\n    lifted:     " + approx
                            + "\n    Heston:     " + ref
                            + "\n    difference: " + Math.abs(approx - ref)
                            + "\n    tolerance:  " + tol);
            }
        }

        lifted.unregisterWithAll();
        roughModel.unregisterWithAll();
        hestonModel.unregisterWithAll();

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 23. testLiftedAgainstAdamsPricing
    // ------------------------------------------------------------------

    @Test
    void testLiftedAgainstAdamsPricing() {
        System.out.println(
                "Testing convergence of lifted rough Heston prices to the "
                        + "Adams reference...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.03, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.01, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        for (double hurst : new double[]{0.05, 0.1, 0.2}) {
            for (double rho : new double[]{-0.7, 0.3}) {
                final RoughHestonModel model = new RoughHestonModel(
                        new HestonProcess(rTS, qTS, s0, 0.04, 0.3, 0.04, 0.4,
                                rho), hurst);

                final AnalyticRoughHestonEngine adams =
                        new AnalyticRoughHestonEngine(model, 128, 1024,
                                Approximation.AdamsPredictorCorrector);

                double previousError = Double.MAX_VALUE;
                for (int n : new int[]{10, 40, 160}) {
                    final AnalyticRoughHestonEngine lifted =
                            new AnalyticRoughHestonEngine(model, 128, 512,
                                    Approximation.Lifted, n);

                    double maxError = 0.0;
                    for (double strike : new double[]{80.0, 90.0, 100.0,
                            110.0, 120.0}) {
                        for (double t : new double[]{0.25, 1.0, 2.0}) {
                            final PlainVanillaPayoff payoff =
                                    new PlainVanillaPayoff(Option.Type.Call,
                                            strike);

                            maxError = Math.max(maxError,
                                    Math.abs(lifted.priceVanillaPayoff(
                                            payoff, t)
                                            - adams.priceVanillaPayoff(
                                            payoff, t)));
                        }
                    }

                    final double expected = previousError / 4.0;
                    if (maxError > expected)
                        boostError("lifted prices are not converging to the "
                                + "Adams reference"
                                + "\n    hurst:     " + hurst
                                + "\n    rho:       " + rho
                                + "\n    factors:   " + n
                                + "\n    max error: " + maxError
                                + "\n    expected:  less than " + expected);

                    previousError = maxError;

                    lifted.unregisterWithAll();
                }

                // On prices of order 1 to 30; the bound is set by the
                // roughest kernel (H = 0.05) at the longest maturity
                final double tol = 3e-3;
                if (previousError > tol)
                    boostError("lifted price deviates from the Adams "
                            + "reference"
                            + "\n    hurst:     " + hurst
                            + "\n    rho:       " + rho
                            + "\n    max error: " + previousError
                            + "\n    tolerance: " + tol);

                adams.unregisterWithAll();
                model.unregisterWithAll();
            }
        }

        checkBoostErrors();
    }

    // ------------------------------------------------------------------
    // 24. testLiftedCalibration
    // ------------------------------------------------------------------

    @Test
    void testLiftedCalibration() {
        System.out.println(
                "Testing rough Heston calibration through the lifted "
                        + "engine...");

        final Date today = new Date(2, Month.JULY, 2026);
        Settings.instance.evaluationDate().equal(today);
        final DayCounter dc = new Actual365Fixed();
        final Calendar calendar = new NullCalendar();

        final Handle<YieldTermStructure> rTS =
                new Handle<>(new FlatForward(today, 0.02, dc), true);
        final Handle<YieldTermStructure> qTS =
                new Handle<>(new FlatForward(today, 0.0, dc), true);
        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final double v0 = 0.0225, kappa = 0.6, theta = 0.04;
        final double sigma = 0.35, rho = -0.65, hurst = 0.12;

        final int integrationOrder = 64, timeSteps = 256, nFactors = 20;

        // The kernel nodes depend on the Hurst exponent, so this is what
        // catches update() failing to invalidate the per-maturity grid cache
        final RoughHestonModel trueModel = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, v0, kappa, theta, sigma, rho),
                hurst);
        final AnalyticRoughHestonEngine trueEngine =
                new AnalyticRoughHestonEngine(trueModel, integrationOrder,
                        timeSteps, Approximation.Lifted, nFactors);

        final List<CalibrationHelper> helpers = new ArrayList<>();
        for (int months : new int[]{3, 6, 12, 24}) {
            final Period maturity = new Period(months, TimeUnit.MONTHS);
            final Date maturityDate = today.add(maturity);
            final double t = dc.yearFraction(today, maturityDate);

            final double fwd = s0.currentLink().value()
                    * qTS.currentLink().discount(maturityDate)
                    / rTS.currentLink().discount(maturityDate);
            final double df = rTS.currentLink().discount(maturityDate);

            for (double strike : new double[]{80.0, 90.0, 100.0, 110.0,
                    120.0}) {
                final double price = trueEngine.priceVanillaPayoff(
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        maturityDate);
                final double impliedVol =
                        BlackFormula.blackFormulaImpliedStdDev(
                                Option.Type.Call, strike, fwd, price, df,
                                0.0, Double.NaN, 1e-6, 100) / Math.sqrt(t);

                helpers.add(new HestonModelHelper(
                        maturity, calendar, s0, strike,
                        new Handle<>(new SimpleQuote(impliedVol), true),
                        rTS, qTS, CalibrationErrorType.ImpliedVolError));
            }
        }

        final RoughHestonModel model = new RoughHestonModel(
                new HestonProcess(rTS, qTS, s0, 0.04, 1.0, 0.02, 0.25, -0.4),
                0.2);
        final AnalyticRoughHestonEngine engine =
                new AnalyticRoughHestonEngine(model, integrationOrder,
                        timeSteps, Approximation.Lifted, nFactors);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).setPricingEngine(engine);

        final LevenbergMarquardt om =
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8);
        model.calibrate(helpers, om,
                new EndCriteria(400, 40, 1.0e-8, 1.0e-8, 1.0e-8));

        double sse = 0.0;
        for (CalibrationHelper helper : helpers) {
            final double error = helper.calibrationError();
            sse += error * error;
        }

        final double expectedSse = 1e-8;
        if (sse > expectedSse)
            boostError("failed to calibrate the lifted rough Heston model"
                    + "\n    sse:      " + sse
                    + "\n    expected: less than " + expectedSse
                    + "\n    v0:    " + model.v0() + " vs " + v0
                    + "\n    kappa: " + model.kappa() + " vs " + kappa
                    + "\n    theta: " + model.theta() + " vs " + theta
                    + "\n    sigma: " + model.sigma() + " vs " + sigma
                    + "\n    rho:   " + model.rho() + " vs " + rho
                    + "\n    hurst: " + model.hurst() + " vs " + hurst);

        final double hurstTol = 0.02;
        if (Math.abs(model.hurst() - hurst) > hurstTol)
            boostError("failed to recover the Hurst exponent through the "
                    + "lifted engine"
                    + "\n    calculated: " + model.hurst()
                    + "\n    expected:   " + hurst
                    + "\n    tolerance:  " + hurstTol);

        for (CalibrationHelper helper : helpers)
            ((BlackCalibrationHelper) helper).unregisterWithAll();
        engine.unregisterWithAll();
        trueEngine.unregisterWithAll();
        model.unregisterWithAll();
        trueModel.unregisterWithAll();

        checkBoostErrors();
    }
}
