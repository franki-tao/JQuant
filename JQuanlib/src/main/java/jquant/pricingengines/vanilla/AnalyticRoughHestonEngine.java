package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.math.distributions.GammaFunction;
import jquant.math.integrals.FourierIntegration;
import jquant.math.ode.FractionalAdams;
import jquant.math.ode.RiemannLiouvilleWeights;
import jquant.models.equity.RoughHestonModel;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;

/**
 * Analytic rough Heston-model engine based on Fourier transform.
 *
 * <p>Official QuantLib
 * {@code ql/pricingengines/vanilla/analyticroughhestonengine.{hpp,cpp}} port.
 *
 * <p>The log-price characteristic function of the rough Heston model retains
 * the affine structure of the classical Heston model, with the Riccati ODE
 * replaced by a fractional Riccati equation. This engine solves the latter by
 * one of three routes: the reference Diethelm-Ford-Freed Adams
 * predictor-corrector scheme, the Pade approximation, or the lifted
 * (multi-factor Markovian) approximation. European options are priced with
 * the Andersen-Piterbarg Fourier integral and a Black-Scholes control
 * variate.
 *
 * <p>References:
 * O. El Euch and M. Rosenbaum, The characteristic function of rough Heston
 * models, Mathematical Finance 29(1), 3-38 (2019).
 *
 * <p>K. Diethelm, N.J. Ford, A.D. Freed, A Predictor-Corrector Approach for
 * the Numerical Solution of Fractional Differential Equations, Nonlinear
 * Dynamics 29, 3-22 (2002).
 *
 * <p>J. Gatheral and R. Radoicic, Rational approximation of the rough Heston
 * solution, International Journal of Theoretical and Applied Finance 22(3),
 * 1950010 (2019).
 *
 * <p>E. Abi Jaber, Lifting the Heston model, Quantitative Finance 19(12),
 * 1995-2013 (2019).
 */
public class AnalyticRoughHestonEngine
        extends GenericEngine<Option.arguments, EuropeanOption.results> {

    /** Route used to solve the fractional Riccati equation. */
    public enum Approximation {AdamsPredictorCorrector, Pade, Lifted}

    private final RoughHestonModel model_;
    private final int timeSteps_;
    private final FourierIntegration integration_;
    private final double andersenPiterbargEpsilon_;
    private final double alpha_;
    private final Approximation approximation_;
    private final int nFactors_;

    private int evaluations_ = 0;
    private final Map<ComplexKey, Complex> chFCache_ = new HashMap<>();
    private final Map<Double, LiftedGrid> liftedGridCache_ = new HashMap<>();

    /*
        Quadrature weights of the two Riemann-Liouville integrals the chF is
        built from. Both depend only on (alpha, timeSteps_); the fractional
        one moves with the Hurst exponent, so both are dropped in update().
    */
    private RiemannLiouvilleWeights rlWeightsOne_;
    private RiemannLiouvilleWeights rlWeightsFractional_;

    /** Official {@code std::tuple<Real, Real, Time>} cache key. */
    private static final class ComplexKey {
        final double re, im, t;

        ComplexKey(double re, double im, double t) {
            this.re = re;
            this.im = im;
            this.t = t;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ComplexKey other))
                return false;
            return re == other.re && im == other.im && t == other.t;
        }

        @Override
        public int hashCode() {
            int result = Double.hashCode(re);
            result = 31 * result + Double.hashCode(im);
            result = 31 * result + Double.hashCode(t);
            return result;
        }
    }

    /** Coefficients c0, c1, c2 of the Riccati equation h' = c0 + c1 h + c2 h^2. */
    private static final class RiccatiCoefficients {
        final Complex c0, c1, c2;

        RiccatiCoefficients(Complex c0, Complex c1, Complex c2) {
            this.c0 = c0;
            this.c1 = c1;
            this.c2 = c2;
        }
    }

    /**
     * Coefficients of the (3, 3) Gatheral-Radoicic rational approximation
     * h(z, t) = N(y) / D(y) with y = sigma t^a, matching the short-time
     * series and the long-time root of the Riccati solution.
     */
    private static final class PadeCoefficients {
        final Complex p1, p2, p3, q1, q2, q3;

        PadeCoefficients(Complex p1, Complex p2, Complex p3,
                         Complex q1, Complex q2, Complex q3) {
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.q1 = q1;
            this.q2 = q2;
            this.q3 = q3;
        }
    }

    /**
     * Per-maturity data of the lifted approximation: the exponential kernel
     * nodes (c_i, x_i) and the step weights of the integrating-factor scheme.
     */
    private static final class LiftedGrid {
        double[] c;
        // e^{-x_i dt}, its c_i-weighted form, and the weights of
        // F_k, F_{k + 1} in the step
        double[] expDt, cExpDt, w0, w1;
        // c_i (1 - e^{-x_i dt}) / x_i and its sum, the psi_i and
        // constant-F weights of the psi-integral quadrature
        double[] cA;
        double cASum;
        // The F_k and (F_{k + 1} - F_k) weights of the same quadrature
        double cP, cQ;
    }

    /** psi^n(z, t) together with the two integrals the chF is built from. */
    private static final class LiftedSolution {
        final Complex psi, fIntegral, psiIntegral;

        LiftedSolution(Complex psi, Complex fIntegral, Complex psiIntegral) {
            this.psi = psi;
            this.fIntegral = fIntegral;
            this.psiIntegral = psiIntegral;
        }
    }

    // Constructor using Gauss-Laguerre integration.
    public AnalyticRoughHestonEngine(final RoughHestonModel model) {
        this(model, 128, 256, Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     int integrationOrder) {
        this(model, integrationOrder, 256,
                Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     int integrationOrder, int timeSteps) {
        this(model, integrationOrder, timeSteps,
                Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     int integrationOrder, int timeSteps,
                                     Approximation approximation) {
        this(model, integrationOrder, timeSteps, approximation, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     int integrationOrder, int timeSteps,
                                     Approximation approximation, int nFactors) {
        this(model, FourierIntegration.gaussLaguerre(integrationOrder),
                timeSteps, 1e-25, -0.5, approximation, nFactors);
    }

    // Constructor giving full control over the Fourier integration algorithm.
    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     FourierIntegration integration,
                                     int timeSteps) {
        this(model, integration, timeSteps, 1e-25, -0.5,
                Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     FourierIntegration integration,
                                     int timeSteps,
                                     double andersenPiterbargEpsilon) {
        this(model, integration, timeSteps, andersenPiterbargEpsilon, -0.5,
                Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     FourierIntegration integration,
                                     int timeSteps,
                                     double andersenPiterbargEpsilon,
                                     double alpha) {
        this(model, integration, timeSteps, andersenPiterbargEpsilon, alpha,
                Approximation.AdamsPredictorCorrector, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     FourierIntegration integration,
                                     int timeSteps,
                                     double andersenPiterbargEpsilon,
                                     double alpha,
                                     Approximation approximation) {
        this(model, integration, timeSteps, andersenPiterbargEpsilon, alpha,
                approximation, 20);
    }

    public AnalyticRoughHestonEngine(final RoughHestonModel model,
                                     FourierIntegration integration,
                                     int timeSteps,
                                     double andersenPiterbargEpsilon,
                                     double alpha,
                                     Approximation approximation,
                                     int nFactors) {
        super(new Option.arguments(), new EuropeanOption.results());
        // Instrument::calculate resets the results object, and
        // Instrument::results::reset() clears this map, so it has to exist
        // before the engine can be attached to an option.
        results_.additionalResults = new HashMap<>();
        model_ = model;
        timeSteps_ = timeSteps;
        integration_ = integration;
        andersenPiterbargEpsilon_ = andersenPiterbargEpsilon;
        alpha_ = alpha;
        approximation_ = approximation;
        nFactors_ = nFactors;

        QL_REQUIRE(timeSteps > 0, "at least one time step required");
        QL_REQUIRE(alpha > -1.0 && alpha < 0.0,
                "alpha (" + alpha + ") must be in (-1, 0)");
        QL_REQUIRE(nFactors > 0, "at least one lifted factor required");

        // Official GenericModelEngine registers the engine with the model;
        // unregisterWithAll() detaches it again.
        registerWith(model_);
    }

    @Override
    public void update() {
        chFCache_.clear();
        // The kernel nodes depend on the Hurst exponent.
        liftedGridCache_.clear();
        // As does the fractional order of the Riemann-Liouville integral
        rlWeightsOne_ = null;
        rlWeightsFractional_ = null;

        super.update();
    }

    private RiemannLiouvilleWeights unitWeights() {
        if (rlWeightsOne_ == null) {
            rlWeightsOne_ = new RiemannLiouvilleWeights(1.0, timeSteps_);
        }
        return rlWeightsOne_;
    }

    private RiemannLiouvilleWeights fractionalWeights(double alpha) {
        // Identity check on a cache key, not a numerical comparison: alpha is
        // rebuilt from the same expression every call, so a difference means
        // the Hurst exponent moved.
        if (rlWeightsFractional_ == null
                || rlWeightsFractional_.alpha() != alpha) {
            rlWeightsFractional_ =
                    new RiemannLiouvilleWeights(alpha, timeSteps_);
        }
        return rlWeightsFractional_;
    }

    /** Log of the normalized characteristic function. */
    public Complex lnChF(Complex z, double t) {
        switch (approximation_) {
            case AdamsPredictorCorrector:
                return lnChFAdams(z, t);
            case Pade:
                return lnChFPade(z, t);
            case Lifted:
                return lnChFLifted(z, t);
            default:
                QL_FAIL("unknown approximation: " + approximation_);
                return Complex.ZERO;
        }
    }

    /** Normalized characteristic function of the log forward moneyness. */
    public Complex chF(Complex z, double t) {
        final ComplexKey key =
                new ComplexKey(z.getReal(), z.getImaginary(), t);
        final Complex cached = chFCache_.get(key);
        if (cached != null)
            return cached;

        final Complex value = lnChF(z, t).exp();
        chFCache_.put(key, value);

        return value;
    }

    // Coefficients of the non-fractional Riccati equation satisfied by the
    // rough Heston h(z, t), shared by the Adams and Pade routes.
    private RiccatiCoefficients riccatiCoefficients(Complex z) {
        final double kappa = model_.kappa();
        final double sigma = model_.sigma();
        final double rho = model_.rho();

        final Complex i = new Complex(0.0, 1.0);

        final Complex c0 = z.multiply(z.add(i)).multiply(-0.5);
        final Complex c1 = i.multiply(rho * sigma).multiply(z)
                .subtract(kappa);
        final double c2 = 0.5 * sigma * sigma;

        return new RiccatiCoefficients(c0, c1, new Complex(c2, 0.0));
    }

    private Complex[] solveAdamsRiccati(Complex z, double t) {
        final double a = model_.hurst() + 0.5;
        final RiccatiCoefficients c = riccatiCoefficients(z);

        return new FractionalAdams(a).solve(
                (time, x) -> c.c0.add(c.c1.add(c.c2.multiply(x)).multiply(x)),
                Complex.ZERO, t, timeSteps_);
    }

    private Complex lnChFAdams(Complex z, double t) {
        final double kappa = model_.kappa();
        final double theta = model_.theta();
        final double v0 = model_.v0();
        final double a = model_.hurst() + 0.5;

        final Complex[] h = solveAdamsRiccati(z, t);

        final double dt = t / timeSteps_;

        return unitWeights().integrate(h, dt).multiply(kappa * theta)
                .add(fractionalWeights(1.0 - a).integrate(h, dt)
                        .multiply(v0));
    }

    private Complex lnChFPade(Complex z, double t) {
        final double kappa = model_.kappa();
        final double theta = model_.theta();
        final double sigma = model_.sigma();
        final double v0 = model_.v0();
        final double a = model_.hurst() + 0.5;

        final PadeCoefficients c = padeCoefficients(z);

        final double dt = t / timeSteps_;

        final Complex[] h = new Complex[timeSteps_ + 1];
        for (int j = 0; j <= timeSteps_; ++j) {
            h[j] = evaluatePade(c, sigma * Math.pow((double) j * dt, a));
        }

        return unitWeights().integrate(h, dt).multiply(kappa * theta)
                .add(fractionalWeights(1.0 - a).integrate(h, dt)
                        .multiply(v0));
    }

    // (3, 3) global rational approximation of Gatheral-Radoicic
    private PadeCoefficients padeCoefficients(Complex z) {
        final double kappa = model_.kappa();
        final double sigma = model_.sigma();
        final double rho = model_.rho();
        final double a = model_.hurst() + 0.5;

        final Complex i = new Complex(0.0, 1.0);

        final RiccatiCoefficients rc = riccatiCoefficients(z);
        final Complex c0 = rc.c0;
        final Complex c1 = rc.c1;
        final double c2 = rc.c2.getReal();

        // c0 = -z * (z + i) / 2 vanishes exactly at z = 0 and z = -i. There,
        // v = 0 solves the Riccati equation identically, so h is exactly
        // zero for all t. It also makes the long-time root gamma0 vanish,
        // which degenerates the (3, 3) system.
        if (c0.abs() < 1e-14) {
            return new PadeCoefficients(Complex.ZERO, Complex.ZERO,
                    Complex.ZERO, Complex.ZERO, Complex.ZERO, Complex.ZERO);
        }

        final GammaFunction g = new GammaFunction();

        // Reciprocal gamma 1 / Gamma(x), which vanishes at the poles
        // 0, -1, -2, ...; keeps the long-time coefficients finite as a -> 1
        // Short-time Taylor coefficients beta_k = b_k / sigma^k of
        // h = sum_k b_k t^(k a), rescaled to the variable y = sigma t^a
        final Complex b1 = c0.multiply(1.0 / g.value(1.0 + a));
        final Complex b2 = c1.multiply(b1)
                .multiply(g.value(1.0 + a) / g.value(1.0 + 2.0 * a));
        final Complex b3 = c1.multiply(b2)
                .add(b1.multiply(b1).multiply(c2))
                .multiply(g.value(1.0 + 2.0 * a)
                        / g.value(1.0 + 3.0 * a));

        final Complex beta1 = b1.divide(sigma);
        final Complex beta2 = b2.divide(sigma * sigma);
        final Complex beta3 = b3.divide(sigma * sigma * sigma);

        // Long-time coefficients gamma_k = g_k sigma^k: h -> r_- / sigma,
        // the attracting root of the Riccati quadratic, with algebraic
        // corrections in 1 / y.
        final Complex w = new Complex(kappa / sigma, 0.0)
                .subtract(i.multiply(z).multiply(rho));
        final Complex A = z.multiply(z.add(i)).add(w.multiply(w)).sqrt();

        // A is the discriminant root separating the two fixed points of the
        // Riccati quadratic; near z where they coincide (A -> 0), gamma_1 and
        // gamma_2 below are ill-conditioned and the (3, 3) approximant is not
        // reliable here.
        QL_REQUIRE(A.abs() > 1e-8,
                "Pade approximation is singular at z = " + z
                        + "; use the Adams "
                        + "predictor-corrector engine for this contour point");

        final Complex rMinus = w.subtract(A);

        final Complex gamma0 = rMinus.divide(sigma);
        final Complex gamma1 = gamma0.negate()
                .multiply(1.0 / g.value(1.0 - a)).divide(A);
        final Complex gamma2 = gamma0
                .multiply(1.0 / g.value(1.0 - 2.0 * a))
                .divide(A.multiply(A))
                .add(gamma1.multiply(gamma1).multiply(0.5 * sigma).divide(A));

        // The two matching sets pin down the denominator (q_1, q_2, q_3)
        // through a 3x3 complex linear system, expanded in closed-form
        // cofactors.
        final Complex det = det3(
                gamma0, gamma1, gamma2,
                beta1.negate(), gamma0, gamma1,
                beta2.negate(), beta1.negate(), gamma0);

        QL_REQUIRE(det.abs() > 1e-12,
                "Pade denominator system is ill-conditioned at z = " + z
                        + "; use the Adams predictor-corrector engine for this "
                        + "contour point");

        final Complex q1 = det3(
                beta1, gamma1, gamma2,
                beta2, gamma0, gamma1,
                beta3, beta1.negate(), gamma0).divide(det);
        final Complex q2 = det3(
                gamma0, beta1, gamma2,
                beta1.negate(), beta2, gamma1,
                beta2.negate(), beta3, gamma0).divide(det);
        final Complex q3 = det3(
                gamma0, gamma1, beta1,
                beta1.negate(), gamma0, beta2,
                beta2.negate(), beta1.negate(), beta3).divide(det);

        final Complex p1 = beta1;
        final Complex p2 = beta2.add(beta1.multiply(q1));
        final Complex p3 = beta3.add(beta2.multiply(q1))
                .add(beta1.multiply(q2));

        return new PadeCoefficients(p1, p2, p3, q1, q2, q3);
    }

    private static Complex det3(Complex a11, Complex a12, Complex a13,
                                Complex a21, Complex a22, Complex a23,
                                Complex a31, Complex a32, Complex a33) {
        return a11.multiply(a22.multiply(a33).subtract(a23.multiply(a32)))
                .subtract(a12.multiply(a21.multiply(a33)
                        .subtract(a23.multiply(a31))))
                .add(a13.multiply(a21.multiply(a32)
                        .subtract(a22.multiply(a31))));
    }

    private static Complex evaluatePade(PadeCoefficients c, double y) {
        final Complex num = c.p1.add(c.p2.add(c.p3.multiply(y)).multiply(y))
                .multiply(y);
        final Complex den = Complex.ONE.add(
                c.q1.add(c.q2.add(c.q3.multiply(y)).multiply(y))
                        .multiply(y));

        return num.divide(den);
    }

    private Complex padeRiccati(Complex z, double t) {
        final double sigma = model_.sigma();
        final double a = model_.hurst() + 0.5;

        return evaluatePade(padeCoefficients(z), sigma * Math.pow(t, a));
    }

    /** psi_p(theta) = sum_{m >= 0} (-theta)^m / (m + p)!. */
    private static double phiFunction(int p, double theta) {
        if (theta < 1.0) {
            double term = 1.0;
            for (int k = 2; k <= p; ++k)
                term /= k;

            double sum = term;

            // Safety cap, not the operative stopping rule: the tolerance
            // break below always fires first. Consecutive terms shrink
            // by a factor theta / (m + p) < 1 / (m + p), so the
            // slowest case (theta -> 1, p = 1) reaches the 1e-18
            // relative tolerance by m = 19.
            final int maxTerms = 24;
            for (int m = 1; m < maxTerms; ++m) {
                term *= -theta / (m + p);
                sum += term;

                if (Math.abs(term) <= 1e-18 * Math.abs(sum))
                    break;
            }
            return sum;
        }

        final double e = Math.expm1(-theta);
        switch (p) {
            case 1:
                return -e / theta;
            case 2:
                return (theta + e) / (theta * theta);
            case 3:
                return (0.5 * theta * theta - theta - e)
                        / (theta * theta * theta);
            default:
                QL_FAIL("unsupported phi function order " + p);
                return 0.0;
        }
    }

    /*
        Kernel nodes and step weights of the lifted route. The linear part
        -x_i psi_i is integrated exactly through its integrating factor and
        F as its linear interpolant over the step. None of it depends on
        the frequency z, hence the per-maturity cache.
    */
    private LiftedGrid liftedGrid(double t) {
        final LiftedGrid cached = liftedGridCache_.get(t);
        if (cached != null)
            return cached;

        final double a = model_.hurst() + 0.5;
        final jquant.math.ode.FractionalKernelApproximation kernel =
                new jquant.math.ode.FractionalKernelApproximation(
                        a, nFactors_, t);

        final double dt = t / timeSteps_;
        final int n = kernel.size();

        final LiftedGrid g = new LiftedGrid();
        g.c = new double[n];
        for (int i = 0; i < n; ++i)
            g.c[i] = kernel.weights().get(i);

        g.expDt = new double[n];
        g.cExpDt = new double[n];
        g.w0 = new double[n];
        g.w1 = new double[n];
        g.cA = new double[n];
        g.cASum = 0.0;
        g.cP = 0.0;
        g.cQ = 0.0;

        for (int i = 0; i < n; ++i) {
            final double theta = kernel.rates().get(i) * dt;

            final double p1 = phiFunction(1, theta);
            final double p2 = phiFunction(2, theta);
            final double p3 = phiFunction(3, theta);

            g.expDt[i] = Math.exp(-theta);
            g.cExpDt[i] = g.c[i] * g.expDt[i];

            // The two sum to dt * p1, the weight of a constant F
            g.w0[i] = dt * (p1 - p2);
            g.w1[i] = dt * p2;

            g.cA[i] = g.c[i] * dt * p1;
            g.cASum += g.cA[i];
            g.cP += g.c[i] * dt * dt * p2;
            g.cQ += g.c[i] * dt * dt * p3;
        }

        liftedGridCache_.put(t, g);
        return g;
    }

    private LiftedSolution solveLiftedRiccati(Complex z, double t) {
        final LiftedGrid g = liftedGrid(t);
        final int n = g.c.length;

        final RiccatiCoefficients c = riccatiCoefficients(z);

        // RHS of the (non-fractional) Riccati equation, evaluated
        // at the lifted state psi^n in place of the true h
        final java.util.function.UnaryOperator<Complex> fct =
                p -> c.c0.add(c.c1.add(c.c2.multiply(p)).multiply(p));

        final double dt = t / timeSteps_;

        final double[] psiRe = new double[n];
        final double[] psiIm = new double[n];
        Complex psiN = Complex.ZERO;
        Complex f = fct.apply(psiN);
        Complex fIntegral = Complex.ZERO;
        Complex psiIntegral = Complex.ZERO;

        for (int k = 0; k < timeSteps_; ++k) {
            // Predictor: decay of the factors plus the response to a
            // constant F over the step
            Complex predicted = f.multiply(g.cASum);
            for (int i = 0; i < n; ++i) {
                predicted = predicted.add(
                        new Complex(psiRe[i], psiIm[i])
                                .multiply(g.cExpDt[i]));
            }

            final Complex fPredicted = fct.apply(predicted);

            // Accumulated before the factors advance: it weights psi(t_k)
            Complex stepIntegral = Complex.ZERO;
            double psiNextRe = 0.0;
            double psiNextIm = 0.0;
            for (int i = 0; i < n; ++i) {
                stepIntegral = stepIntegral.add(new Complex(psiRe[i], psiIm[i])
                        .multiply(g.cA[i]));

                final double nextRe = g.expDt[i] * psiRe[i]
                        + g.w0[i] * f.getReal()
                        + g.w1[i] * fPredicted.getReal();
                final double nextIm = g.expDt[i] * psiIm[i]
                        + g.w0[i] * f.getImaginary()
                        + g.w1[i] * fPredicted.getImaginary();
                psiRe[i] = nextRe;
                psiIm[i] = nextIm;

                psiNextRe += g.c[i] * psiRe[i];
                psiNextIm += g.c[i] * psiIm[i];
            }

            final Complex psiNext = new Complex(psiNextRe, psiNextIm);
            final Complex fNext = fct.apply(psiNext);

            fIntegral = fIntegral.add(
                    f.add(fNext).multiply(0.5 * dt));
            psiIntegral = psiIntegral.add(stepIntegral
                    .add(f.multiply(g.cP))
                    .add(fNext.subtract(f).multiply(g.cQ)));

            psiN = psiNext;
            f = fNext;
        }

        return new LiftedSolution(psiN, fIntegral, psiIntegral);
    }

    /*
        The lifted characteristic function is
        ln phi = V_0 int_0^t F(z, psi^n) + kappa theta int_0^t psi^n,
        the exact counterpart of the fractional
        kappa theta I^1 h + V_0 I^(1-a) h because
        I^(1-a) h = I^(1-a) I^a F(z, h) = I^1 F(z, h).
    */
    private Complex lnChFLifted(Complex z, double t) {
        final LiftedSolution s = solveLiftedRiccati(z, t);

        return s.fIntegral.multiply(model_.v0())
                .add(s.psiIntegral.multiply(
                        model_.kappa() * model_.theta()));
    }

    /**
     * Solution h(z, t) of the fractional Riccati equation, computed by the
     * route configured at construction.
     */
    public Complex riccatiSolution(Complex z, double t) {
        QL_REQUIRE(t > 0.0, "maturity must be positive");

        switch (approximation_) {
            case AdamsPredictorCorrector: {
                final Complex[] h = solveAdamsRiccati(z, t);
                return h[h.length - 1];
            }
            case Pade:
                return padeRiccati(z, t);
            case Lifted:
                return solveLiftedRiccati(z, t).psi;
            default:
                QL_FAIL("unknown approximation: " + approximation_);
                return Complex.ZERO;
        }
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff, Date maturity) {
        final HestonProcess process = model_.process();
        final double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff,
                process.riskFreeRate().getValue().timeFromReference(maturity),
                fwd);
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff,
                                     double maturity) {
        QL_REQUIRE(maturity > 0.0, "maturity must be positive");

        final HestonProcess process = model_.process();
        final double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff, maturity, fwd);
    }

    private double priceVanillaPayoff(PlainVanillaPayoff payoff,
                                      double maturity, double fwd) {
        QL_REQUIRE(maturity > 0.0, "maturity must be positive");

        final HestonProcess process = model_.process();
        final double spot = process.s0().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        final double dr = process.riskFreeRate().getValue()
                .discount(maturity);
        final double strike = payoff.strike();

        final double kappa = model_.kappa();
        final double theta = model_.theta();
        final double sigma = model_.sigma();
        final double rho = model_.rho();
        final double v0 = model_.v0();
        final double a = model_.hurst() + 0.5;

        evaluations_ = 0;

        // Decay estimate of the characteristic function along the
        // integration contour; recovers the classical Heston engine value
        // sqrt(1 - rho^2)(v0 + kappa theta t) / sigma for alpha = 1
        final double c_inf = Math.sqrt(1.0 - rho * rho) / sigma
                * (v0 * Math.pow(maturity, 1.0 - a)
                / new GammaFunction().value(2.0 - a)
                + kappa * theta * maturity);

        final double epsilon = andersenPiterbargEpsilon_
                * M_PI / (Math.sqrt(strike * fwd) * dr);

        final DoubleSupplier uM = () ->
                FourierIntegration.andersenPiterbargIntegrationLimit(
                        c_inf, epsilon, v0, maturity);

        final AP_Helper cvHelper =
                new AP_Helper(maturity, fwd, strike, alpha_, this);

        final double cvValue = cvHelper.controlVariateValue();

        final double scalingFactor = Math.max(0.25, Math.min(1000.0,
                0.25 / Math.sqrt(0.5 * cvHelper.vAvg() * maturity)));

        final double h_cv = fwd / M_PI
                * integration_.calculate(c_inf, cvHelper, uM, scalingFactor);
        evaluations_ += integration_.numberOfEvaluations();

        switch (payoff.optionType()) {
            case Call:
                return (cvValue + h_cv) * dr;
            case Put:
                return (cvValue + h_cv - (fwd - strike)) * dr;
            default:
                QL_FAIL("unknown option type");
                return 0.0;
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        results_.value =
                priceVanillaPayoff(payoff, arguments_.exercise.lastDate());
    }

    public int numberOfEvaluations() {
        return evaluations_;
    }

    /**
     * Andersen-Piterbarg integrand with Black-Scholes control variate,
     * following {@code AnalyticHestonEngine::AP_Helper}.
     */
    private static final class AP_Helper implements DoubleUnaryOperator {
        private final double term_;
        private final double fwd_, strike_, alpha_, freq_, s_alpha_;
        private final AnalyticRoughHestonEngine enginePtr_;
        private double vAvg_;

        AP_Helper(double term, double fwd, double strike, double alpha,
                  AnalyticRoughHestonEngine enginePtr) {
            term_ = term;
            fwd_ = fwd;
            strike_ = strike;
            alpha_ = alpha;
            freq_ = Math.log(fwd / strike);
            s_alpha_ = Math.exp(alpha * freq_);
            enginePtr_ = enginePtr;

            QL_REQUIRE(enginePtr != null, "pricing engine required");

            final double moment = enginePtr.chF(
                    new Complex(0.0, alpha_), term).getReal();
            QL_REQUIRE(moment > 0.0,
                    "invalid characteristic function value; try to "
                            + "increase the number of fractional Riccati time steps");
            vAvg_ = -8.0 * Math.log(moment) / term;
        }

        @Override
        public double applyAsDouble(double u) {
            final Complex z = new Complex(u, -alpha_);
            final Complex zPrime = new Complex(u, -alpha_ - 1.0);

            final Complex iZPrime = new Complex(-zPrime.getImaginary(),
                    zPrime.getReal());
            final Complex exponent = zPrime.multiply(zPrime)
                    .add(iZPrime)
                    .multiply(-0.5 * vAvg_ * term_);
            final Complex phiBS = exponent.exp();

            Complex phi = enginePtr_.chF(zPrime, term_);

            // The fractional Adams scheme diverges once the oscillation is
            // under-resolved by the time grid. The exact characteristic
            // function satisfies |phi| <= 1 and decays exponentially
            // in u, so far-tail values are truncated to zero.
            final double norm = phi.getReal() * phi.getReal()
                    + phi.getImaginary() * phi.getImaginary();
            if (!Double.isFinite(phi.getReal())
                    || !Double.isFinite(phi.getImaginary())
                    || norm > 1.0)
                phi = Complex.ZERO;

            return new Complex(0.0, u * freq_).exp()
                    .multiply(phiBS.subtract(phi))
                    .divide(z.multiply(zPrime))
                    .getReal() * s_alpha_;
        }

        double controlVariateValue() {
            return new BlackCalculator(Option.Type.Call, strike_, fwd_,
                    Math.sqrt(vAvg_ * term_), 1.0).value();
        }

        double vAvg() {
            return vAvg_;
        }
    }
}
