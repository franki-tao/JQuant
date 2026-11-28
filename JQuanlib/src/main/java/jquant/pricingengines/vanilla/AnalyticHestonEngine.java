package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.integrals.ExponentialIntegral;
import jquant.math.integrals.FourierIntegration;
import jquant.models.equity.HestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Official QuantLib {@code ql/pricingengines/vanilla/analytichestonengine.hpp/.cpp} port.
 *
 * <p>Integration detail: Two algebraically equivalent formulations of the
 * complex logarithm of the Heston model exist. Gatherals [2005] (also
 * Duffie, Pan and Singleton [2000], and Schoutens, Simons and Tistaert
 * [2004]) version does not cause discoutinuities whereas the original
 * version (e.g. Heston [1993]) needs some sort of "branch correction" to
 * work properly. Gatheral's version does also work with adaptive
 * integration routines and should be preferred over the original Heston
 * version.
 *
 * <p>The engine implements all official {@link ComplexLogFormula} paths:
 * Gatheral and branch correction without control variate, the
 * Andersen-Piterbarg control variates, the asymptotic characteristic
 * function control variate, the angled contour shifts and the automatic
 * {@link ComplexLogFormula#OptimalCV} selection.
 */
public class AnalyticHestonEngine implements PricingEngine {

    public enum ComplexLogFormula {
        // Gatheral form of characteristic function w/o control variate
        Gatheral,
        // old branch correction form of the characteristic function w/o control variate
        BranchCorrection,
        // Gatheral form with Andersen-Piterbarg control variate
        AndersenPiterbarg,
        // same as AndersenPiterbarg, but a slightly better control variate
        AndersenPiterbargOptCV,
        // Gatheral form with asymptotic expansion of the characteristic function as control variate
        AsymptoticChF,
        // angled contour shift integral with control variate
        AngledContour,
        // angled contour shift integral w/o control variate
        AngledContourNoCV,
        // auto selection of best control variate algorithm from above
        OptimalCV
    }

    protected final HestonModel model_;
    private int evaluations_;
    private final ComplexLogFormula cpxLog_;
    private final FourierIntegration integration_;
    private final double andersenPiterbargEpsilon_;
    private final double alpha_;

    protected final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    // Simple to use constructor: Using adaptive
    // Gauss-Lobatto integration and Gatheral's version of complex log.
    // Be aware: using a too large number for maxEvaluations might result
    // in a stack overflow as the Lobatto integration is a recursive
    // algorithm.
    public AnalyticHestonEngine(HestonModel model,
                                double relTolerance,
                                int maxEvaluations) {
        this(model, ComplexLogFormula.OptimalCV,
                FourierIntegration.gaussLobatto(
                        relTolerance, NULL_REAL, maxEvaluations, false),
                1e-40, -0.5);
    }

    // Constructor using Laguerre integration
    // and Gatheral's version of complex log.
    public AnalyticHestonEngine(HestonModel model, int integrationOrder) {
        this(model, ComplexLogFormula.OptimalCV,
                FourierIntegration.gaussLaguerre(integrationOrder),
                NULL_REAL, -0.5);
    }

    public AnalyticHestonEngine(HestonModel model) {
        this(model, 144);
    }

    // Constructor giving full control
    // over the Fourier integration algorithm
    public AnalyticHestonEngine(HestonModel model,
                                ComplexLogFormula cpxLog,
                                FourierIntegration integration) {
        this(model, cpxLog, integration, 1e-25, -0.5);
    }

    // Official four-argument constructor: the alpha parameter defaults to
    // -0.5, as in ql/pricingengines/vanilla/analytichestonengine.hpp.
    public AnalyticHestonEngine(HestonModel model,
                                ComplexLogFormula cpxLog,
                                FourierIntegration integration,
                                double andersenPiterbargEpsilon) {
        this(model, cpxLog, integration, andersenPiterbargEpsilon, -0.5);
    }

    public AnalyticHestonEngine(HestonModel model,
                                ComplexLogFormula cpxLog,
                                FourierIntegration integration,
                                double andersenPiterbargEpsilon,
                                double alpha) {
        model_ = model;
        evaluations_ = 0;
        cpxLog_ = cpxLog;
        integration_ = integration;
        andersenPiterbargEpsilon_ = andersenPiterbargEpsilon;
        alpha_ = alpha;
        QL_REQUIRE(cpxLog_ != ComplexLogFormula.BranchCorrection
                        || !integration.isAdaptiveIntegration(),
                "Branch correction does not work in conjunction "
                        + "with adaptive integration methods");

        results_.additionalResults = new HashMap<>();
        model_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    @Override
    public Option.arguments getArguments() {
        return arguments_;
    }

    @Override
    public EuropeanOption.results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        results_.strikeSensitivity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        // this is a european option pricer
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        // plain vanilla
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        Date exerciseDate = arguments_.exercise.lastDate();

        results_.value = priceVanillaPayoff(payoff, exerciseDate);
    }

    public int numberOfEvaluations() {
        return evaluations_;
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff, Date maturity) {
        HestonProcess process = model_.process();
        // Official uses YieldTermStructure::discount(const Date&), not discount(time(date)).
        double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff, process.time(maturity), fwd);
    }

    public double priceVanillaPayoff(PlainVanillaPayoff payoff, double maturity) {
        HestonProcess process = model_.process();
        double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(maturity)
                / process.riskFreeRate().getValue().discount(maturity);

        return priceVanillaPayoff(payoff, maturity, fwd);
    }

    private double priceVanillaPayoff(PlainVanillaPayoff payoff,
                                      double maturity, double fwd) {
        double value;

        HestonProcess process = model_.process();
        double dr = process.riskFreeRate().getValue().discount(maturity);

        double strike = payoff.strike();
        double spot = process.s0().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        double df = spot / fwd;
        double dd = dr / df;

        double kappa = model_.kappa();
        double sigma = model_.sigma();
        double theta = model_.theta();
        double rho = model_.rho();
        double v0 = model_.v0();

        evaluations_ = 0;

        switch (cpxLog_) {
            case Gatheral:
            case BranchCorrection: {
                double c_inf = Math.min(0.2, Math.max(0.0001,
                        Math.sqrt(1.0 - rho * rho) / sigma)) * (v0 + kappa * theta * maturity);

                double p1 = integration_.calculate(c_inf,
                        new Fj_Helper(kappa, theta, sigma, v0, spot, rho, this,
                                cpxLog_, maturity, strike, df, 1)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                double p2 = integration_.calculate(c_inf,
                        new Fj_Helper(kappa, theta, sigma, v0, spot, rho, this,
                                cpxLog_, maturity, strike, df, 2)) / M_PI;
                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        value = spot * dd * (p1 + 0.5)
                                - strike * dr * (p2 + 0.5);
                        break;
                    case Put:
                        value = spot * dd * (p1 - 0.5)
                                - strike * dr * (p2 - 0.5);
                        break;
                    default:
                        QL_FAIL("unknown option type");
                        value = 0.0;
                }
            }
            break;
            case AndersenPiterbarg:
            case AndersenPiterbargOptCV:
            case AsymptoticChF:
            case AngledContour:
            case AngledContourNoCV:
            case OptimalCV: {
                double c_inf = Math.sqrt(1.0 - rho * rho)
                        * (v0 + kappa * theta * maturity) / sigma;

                double epsilon = andersenPiterbargEpsilon_
                        * M_PI / (Math.sqrt(strike * fwd) * dr);

                DoubleSupplier uM = () ->
                        FourierIntegration.andersenPiterbargIntegrationLimit(
                                c_inf, epsilon, v0, maturity);

                ComplexLogFormula finalLog = (cpxLog_ == ComplexLogFormula.OptimalCV)
                        ? optimalControlVariate(maturity, v0, kappa, theta, sigma, rho)
                        : cpxLog_;

                AP_Helper cvHelper = new AP_Helper(
                        maturity, fwd, strike, finalLog, this, alpha_);

                double cvValue = cvHelper.controlVariateValue();

                double vAvg = (1 - Math.exp(-kappa * maturity))
                        * (v0 - theta) / (kappa * maturity) + theta;

                double scalingFactor = (cpxLog_ != ComplexLogFormula.OptimalCV
                        && cpxLog_ != ComplexLogFormula.AsymptoticChF)
                        ? Math.max(0.25, Math.min(1000.0,
                        0.25 / Math.sqrt(0.5 * vAvg * maturity)))
                        : 1.0;

                double h_cv = fwd / M_PI
                        * integration_.calculate(c_inf, cvHelper, uM, scalingFactor);

                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        value = (cvValue + h_cv) * dr;
                        break;
                    case Put:
                        value = (cvValue + h_cv - (fwd - strike)) * dr;
                        break;
                    default:
                        QL_FAIL("unknown option type");
                        value = 0.0;
                }
            }
            break;
            default:
                QL_FAIL("unknown complex log formula");
                value = 0.0;
        }

        return value;
    }

    /**
     * Official {@code AnalyticHestonEngine::OptimalAlpha}. Computes the
     * optimal contour shift alpha for the Fourier inversion.
     */
    public static class OptimalAlpha {
        public enum Method { AvoidCancellation, OptimalValue }

        /** Pair of (alpha, target function value), official {@code std::pair}. */
        public static class AlphaBounds {
            public final double first;
            public final double second;

            AlphaBounds(double first, double second) {
                this.first = first;
                this.second = second;
            }
        }

        private final double t_, fwd_, kappa_, theta_, sigma_, rho_;
        private final double eps_;
        private final AnalyticHestonEngine enginePtr_;
        private final Method method_;
        private double km_, kp_;
        private int evaluations_ = 0;

        public OptimalAlpha(double t, AnalyticHestonEngine enginePtr) {
            this(t, enginePtr, Method.OptimalValue);
        }

        public OptimalAlpha(double t, AnalyticHestonEngine enginePtr, Method method) {
            t_ = t;
            fwd_ = enginePtr.model_.process().s0().getValue().value()
                    * enginePtr.model_.process().dividendYield().getValue().discount(t)
                    / enginePtr.model_.process().riskFreeRate().getValue().discount(t);
            kappa_ = enginePtr.model_.kappa();
            theta_ = enginePtr.model_.theta();
            sigma_ = enginePtr.model_.sigma();
            rho_ = enginePtr.model_.rho();
            eps_ = Math.pow(2.0, -26);
            enginePtr_ = enginePtr;
            method_ = method;

            km_ = k(0.0, -1);
            kp_ = k(0.0, 1);
        }

        public double alphaMax(double strike) {
            final double eps = 1e-8;
            final java.util.function.DoubleUnaryOperator cm = k -> M(k) - t_;

            double alpha_max;
            final double adx = kappa_ - sigma_ * rho_;
            if (adx > 0.0) {
                final double kp_2pi = k(2 * M_PI, 1);

                alpha_max = solve(cm, eps_, 0.5 * (kp_ + kp_2pi),
                        (1 + eps) * kp_, (1 - eps) * kp_2pi) - 1.0;
            } else if (adx < 0.0) {
                final double tCut = -2 / (kappa_ - sigma_ * rho_ * kp_);
                if (t_ < tCut) {
                    final double kp_pi = k(M_PI, 1);
                    alpha_max = solve(cm, eps_, 0.5 * (kp_ + kp_pi),
                            (1 + eps) * kp_, (1 - eps) * kp_pi) - 1.0;
                } else {
                    alpha_max = solve(cm, eps_, 0.5 * (1.0 + kp_),
                            1 + eps, (1 - eps) * kp_) - 1.0;
                }
            } else { // adx == 0.0
                final double kp_pi = k(M_PI, 1);
                alpha_max = solve(cm, eps_, 0.5 * (kp_ + kp_pi),
                        (1 + eps) * kp_, (1 - eps) * kp_pi) - 1.0;
            }

            QL_REQUIRE(alpha_max >= 0.0, "alpha max must be larger than zero");

            return alpha_max;
        }

        public AlphaBounds alphaGreaterZero(double strike) {
            final double alpha_max = alphaMax(strike);

            return findMinima(eps_, Math.max(2 * eps_, (1.0 - 1e-6) * alpha_max), strike);
        }

        public double alphaMin(double strike) {
            final java.util.function.DoubleUnaryOperator cm = k -> M(k) - t_;

            final double km_2pi = k(2 * M_PI, -1);

            final double alpha_min = solve(cm, eps_, 0.5 * (km_2pi + km_),
                    (1 - 1e-8) * km_2pi, (1 + 1e-8) * km_) - 1.0;

            QL_REQUIRE(alpha_min <= -1.0, "alpha min must be smaller than minus one");

            return alpha_min;
        }

        public AlphaBounds alphaSmallerMinusOne(double strike) {
            final double alpha_min = alphaMin(strike);

            return findMinima(
                    Math.min(-1.0 - 1e-6, -1.0 + (1.0 - 1e-6) * (alpha_min + 1.0)),
                    -1.0 - eps_, strike);
        }

        /** Official {@code operator()(strike)}. */
        public double value(double strike) {
            switch (method_) {
                case OptimalValue:
                    try {
                        final AlphaBounds minusOne = alphaSmallerMinusOne(strike);
                        final AlphaBounds greaterZero = alphaGreaterZero(strike);

                        if (minusOne.second < greaterZero.second) {
                            return minusOne.first;
                        } else {
                            return greaterZero.first;
                        }
                    } catch (RuntimeException e) {
                        return -0.5;
                    }
                case AvoidCancellation: {
                    final double omega = Math.log(fwd_ / strike);
                    if (omega >= 0.0)
                        return alphaSmallerMinusOne(strike).first;
                    else
                        return alphaGreaterZero(strike).first;
                }
                default:
                    QL_FAIL("unknown calculation method");
                    return -0.5;
            }
        }

        public int numberOfEvaluations() {
            return evaluations_;
        }

        private AlphaBounds findMinima(double lower, double upper, double strike) {
            final double freq = Math.log(fwd_ / strike);

            // Brent's method for the minimum, as boost::math::tools::brent_find_minima.
            double a = lower;
            double b = upper;
            final double golden = 0.3819660112501051;
            double x = a + golden * (b - a);
            double w = x;
            double v = x;
            double fx = target(x, freq);
            double fw = fx;
            double fv = fx;
            double d = 0.0;
            double e = 0.0;

            final int maxIter = 53;
            for (int iter = 0; iter < maxIter; ++iter) {
                final double xm = 0.5 * (a + b);
                final double tol1 = Math.sqrt(QL_EPSILON) * Math.abs(x)
                        + 1e-12;
                final double tol2 = 2.0 * tol1;

                if (Math.abs(x - xm) <= (tol2 - 0.5 * (b - a))) {
                    break;
                }

                double p = 0.0;
                double q = 0.0;
                double r = 0.0;
                boolean parabolic = false;
                if (Math.abs(e) > tol1) {
                    r = (x - w) * (fx - fv);
                    q = (x - v) * (fx - fw);
                    p = (x - v) * q - (x - w) * r;
                    q = 2.0 * (q - r);
                    if (q > 0.0) {
                        p = -p;
                    } else {
                        q = -q;
                    }
                    r = e;
                    e = d;
                    if (Math.abs(p) < Math.abs(0.5 * q * r)
                            && p > q * (a - x) && p < q * (b - x)) {
                        d = p / q;
                        final double u = x + d;
                        if (u - a < tol2 || b - u < tol2) {
                            d = (xm >= x) ? tol1 : -tol1;
                        }
                        parabolic = true;
                    }
                }
                if (!parabolic) {
                    e = (x >= xm) ? a - x : b - x;
                    d = golden * e;
                }

                final double u = x + ((Math.abs(d) >= tol1) ? d
                        : ((d > 0.0) ? tol1 : -tol1));
                final double fu = target(u, freq);

                if (fu <= fx) {
                    if (u >= x) {
                        a = x;
                    } else {
                        b = x;
                    }
                    v = w;
                    fv = fw;
                    w = x;
                    fw = fx;
                    x = u;
                    fx = fu;
                } else {
                    if (u < x) {
                        a = u;
                    } else {
                        b = u;
                    }
                    if (fu <= fw || w == x) {
                        v = w;
                        fv = fw;
                        w = u;
                        fw = fu;
                    } else if (fu <= fv || v == x || v == w) {
                        v = u;
                        fv = fu;
                    }
                }
            }

            return new AlphaBounds(x, fx);
        }

        private double target(double alpha, double freq) {
            ++evaluations_;
            final Complex z = new Complex(0, -(alpha + 1));
            return enginePtr_.lnChF(z, t_).getReal()
                    - Math.log(alpha * (alpha + 1)) + alpha * freq;
        }

        private double solve(java.util.function.DoubleUnaryOperator f,
                             double accuracy, double guess,
                             double xMin, double xMax) {
            return new jquant.math.solvers1d.Brent().solve(
                    x -> f.applyAsDouble(x), accuracy, guess, xMin, xMax);
        }

        public double M(double k) {
            final double beta = kappa_ - sigma_ * rho_ * k;

            if (k >= km_ && k <= kp_) {
                final double D = Math.sqrt(beta * beta - sigma_ * sigma_ * k * (k - 1));
                return Math.log((beta - D) / (beta + D)) / D;
            } else {
                final double D_imag =
                        Math.sqrt(-(beta * beta - sigma_ * sigma_ * k * (k - 1)));

                return 2 / D_imag
                        * (((beta > 0.0) ? M_PI : 0.0) - Math.atan(D_imag / beta));
            }
        }

        public double k(double x, int sgn) {
            return ((sigma_ - 2 * rho_ * kappa_)
                    + sgn * Math.sqrt(
                    Math.pow(sigma_ - 2 * rho_ * kappa_, 2)
                            + 4 * (kappa_ * kappa_ + x * x / (t_ * t_))
                            * (1 - rho_ * rho_)))
                    / (2 * sigma_ * (1 - rho_ * rho_));
        }
    }

    public static ComplexLogFormula optimalControlVariate(
            double t, double v0, double kappa, double theta,
            double sigma, double rho) {
        if (t > 0.15
                && (v0 + t * kappa * theta) / sigma
                * Math.sqrt(1 - rho * rho) < 0.15
                && ((kappa - 0.5 * rho * sigma) * (v0 + t * kappa * theta)
                + kappa * theta * Math.log(4 * (1 - rho * rho)))
                / (sigma * sigma) < 0.1) {
            return ComplexLogFormula.AsymptoticChF;
        } else {
            return ComplexLogFormula.AngledContour;
        }
    }

    // normalized characteristic function
    public Complex chF(Complex z, double t) {
        if (model_.sigma() > 1e-6 || model_.kappa() < 1e-8) {
            Complex v = lnChF(z, t);
            if (v.getReal() != Double.NEGATIVE_INFINITY)
                return v.exp();
            else
                return Complex.ZERO;
        } else {
            double kappa = model_.kappa();
            double sigma = model_.sigma();
            double theta = model_.theta();
            double rho = model_.rho();
            double v0 = model_.v0();

            double sigma2 = sigma * sigma;

            double kt = kappa * t;
            double ekt = Math.exp(kt);
            double e2kt = Math.exp(2 * kt);
            double rho2 = rho * rho;
            Complex zpi = z.add(new Complex(0.0, 1.0));

            double c0 = theta - v0 + ekt * ((-1 + kt) * theta + v0);
            double c1 = 2 * theta + kt * theta - v0 - kt * v0
                    + ekt * ((-2 + kt) * theta + v0);

            Complex firstExponent = z.multiply(zpi)
                    .multiply(-c0 / (2.0 * kappa * ekt));

            Complex term0 = firstExponent.exp();

            Complex term1 = firstExponent.subtract(kt).exp()
                    .multiply(rho * c1 / (2.0 * kappa * kappa) * sigma)
                    .multiply(Complex.ONE.subtract(
                            new Complex(-z.getImaginary(), z.getReal())))
                    .multiply(z).multiply(z);

            Complex kz = new Complex(kappa, 0.0);

            Complex bracket =
                    z.multiply(z).multiply(zpi)
                            .multiply(-2 * rho2 * c1 * c1)
                            .add(kz.multiply(2.0 * v0).multiply(
                                    zpi.negate()
                                            .add(zpi.add(z.multiply(4 * rho2)).multiply(e2kt))
                                            .subtract(z.multiply(2 * rho2)
                                                    .add(zpi.add(z.multiply(rho2 * (2 + kt)))
                                                            .multiply(kt))
                                                    .multiply(2 * ekt))))
                            .add(new Complex(kappa * theta, 0.0).multiply(
                                    zpi.add(
                                                    zpi.negate().multiply(5.0)
                                                            .subtract(z.multiply(24 * rho2))
                                                            .add(zpi.add(z.multiply(4 * rho2))
                                                                    .multiply(2 * kt))
                                                            .multiply(e2kt))
                                            .add(zpi.add(z.multiply(6 * rho2))
                                                            .add(zpi.add(z.multiply(rho2 * (4 + kt)))
                                                                    .multiply(kt))
                                                            .multiply(4 * ekt))));

            Complex term2 = firstExponent.subtract(2 * kt).exp()
                    .multiply(z).multiply(z).multiply(zpi)
                    .multiply(bracket)
                    .divide(16.0 * kappa * kappa * kappa * kappa)
                    .multiply(sigma2);

            return term0.add(term1).add(term2);
        }
    }

    // log of the normalized characteristic function
    public Complex lnChF(Complex z, double t) {
        double kappa = model_.kappa();
        double sigma = model_.sigma();
        double theta = model_.theta();
        double rho = model_.rho();
        double v0 = model_.v0();

        double sigma2 = sigma * sigma;

        Complex g = new Complex(kappa, 0.0)
                .add(new Complex(rho * sigma * z.getImaginary(),
                        -rho * sigma * z.getReal()));

        Complex D = g.multiply(g)
                .add(z.multiply(z)
                        .add(new Complex(-z.getImaginary(), z.getReal()))
                        .multiply(sigma2))
                .sqrt();

        // reduce cancelation errors, see. L. Andersen and M. Lake
        Complex r = g.subtract(D);
        if (g.getReal() * D.getReal() + g.getImaginary() * D.getImaginary() > 0.0) {
            r = new Complex(-sigma2, 0.0)
                    .multiply(z)
                    .multiply(new Complex(z.getReal(), z.getImaginary() + 1.0))
                    .divide(g.add(D));
        }

        Complex y;
        if (D.getReal() != 0.0 || D.getImaginary() != 0.0)
            y = complexExpm1(D.multiply(-t)).divide(D.multiply(2.0));
        else
            y = new Complex(-0.5 * t, 0.0);

        Complex ry = r.multiply(y);

        Complex A = new Complex(kappa * theta / sigma2, 0.0)
                .multiply(r.multiply(t)
                        .subtract(complexLog1p(ry.negate()).multiply(2.0)));
        Complex B = z.multiply(new Complex(z.getReal(), z.getImaginary() + 1.0))
                .multiply(y)
                .divide(Complex.ONE.subtract(ry));

        return A.add(B.multiply(v0));
    }

    // std::complex versions of expm1 and log1p, ql/math/expm1.cpp
    private static Complex complexExpm1(Complex z) {
        if (z.abs() < 1.0) {
            double a = z.getReal(), b = z.getImaginary();
            double exp_1 = Math.expm1(a);
            double cos_1 = -2.0 * Math.sin(0.5 * b) * Math.sin(0.5 * b);

            return new Complex(
                    exp_1 * cos_1 + exp_1 + cos_1,
                    Math.sin(b) * Math.exp(a));
        } else {
            return z.exp().subtract(1.0);
        }
    }

    private static Complex complexLog1p(Complex z) {
        double a = z.getReal(), b = z.getImaginary();
        if (Math.abs(a) < 0.5 && Math.abs(b) < 0.5) {
            return new Complex(
                    0.5 * Math.log1p(a * a + 2 * a + b * b),
                    Math.atan2(b, 1.0 + a));
        } else {
            return Complex.ONE.add(z).log();
        }
    }

    // call back for extended stochastic volatility
    // plus jump diffusion engines like bates model
    protected Complex addOnTerm(double phi, double t, int j) {
        return new Complex(0.0, 0.0);
    }

    // helper class for integration
    private class Fj_Helper implements java.util.function.DoubleUnaryOperator {
        private final int j_;
        private final double kappa_, theta_, sigma_, v0_;
        private final ComplexLogFormula cpxLog_;

        // helper variables
        private final double term_;
        private final double x_, sx_, dd_;
        private final double sigma2_, rsigma_;
        private final double t0_;

        // log branch counter
        private int b_ = 0;     // log branch counter
        private double g_km1_ = 0; // imag part of last log value

        private final AnalyticHestonEngine engine_;

        Fj_Helper(double kappa, double theta,
                  double sigma, double v0, double s0, double rho,
                  AnalyticHestonEngine engine,
                  ComplexLogFormula cpxLog,
                  double term, double strike, double ratio, int j) {
            j_ = j;
            kappa_ = kappa;
            theta_ = theta;
            sigma_ = sigma;
            v0_ = v0;
            cpxLog_ = cpxLog;
            term_ = term;
            x_ = Math.log(s0);
            sx_ = Math.log(strike);
            dd_ = x_ - Math.log(ratio);
            sigma2_ = sigma_ * sigma_;
            rsigma_ = rho * sigma_;
            t0_ = kappa - ((j == 1) ? rho * sigma : 0.0);

            engine_ = engine;
        }

        @Override
        public double applyAsDouble(double phi) {
            double rpsig = rsigma_ * phi;

            Complex t1 = new Complex(t0_, -rpsig);
            Complex d = t1.multiply(t1)
                    .subtract(new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                            .multiply(sigma2_ * phi))
                    .sqrt();
            Complex ex = d.multiply(-term_).exp();
            Complex addOnTerm =
                    engine_ != null ? engine_.addOnTerm(phi, term_, j_) : new Complex(0.0);

            if (cpxLog_ == ComplexLogFormula.Gatheral) {
                if (phi != 0.0) {
                    if (sigma_ > 1e-5) {
                        Complex p = t1.subtract(d).divide(t1.add(d));
                        Complex g = new Complex(1.0).subtract(p.multiply(ex))
                                .divide(new Complex(1.0).subtract(p))
                                .log();

                        return t1.subtract(d).multiply(new Complex(1.0).subtract(ex))
                                .multiply(v0_)
                                .divide(new Complex(1.0).subtract(ex.multiply(p))
                                        .multiply(sigma2_))
                                .add(t1.subtract(d).multiply(term_)
                                        .subtract(g.multiply(2.0))
                                        .multiply(kappa_ * theta_ / sigma2_))
                                .add(new Complex(0.0, phi * (dd_ - sx_)))
                                .add(addOnTerm)
                                .exp().getImaginary() / phi;
                    } else {
                        Complex td = new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                                .multiply(phi)
                                .divide(t1.multiply(2.0));
                        Complex p = td.multiply(sigma2_).divide(t1.add(d));
                        Complex g = p.multiply(new Complex(1.0).subtract(ex));

                        return td.multiply(new Complex(1.0).subtract(ex))
                                .multiply(v0_)
                                .divide(new Complex(1.0).subtract(p.multiply(ex)))
                                .add(td.multiply(term_)
                                        .subtract(g.multiply(2.0 / sigma2_))
                                        .multiply(kappa_ * theta_))
                                .add(new Complex(0.0, phi * (dd_ - sx_)))
                                .add(addOnTerm)
                                .exp().getImaginary() / phi;
                    }
                } else {
                    // use l'Hospital's rule to get lim_{phi->0}
                    if (j_ == 1) {
                        double kmr = rsigma_ - kappa_;
                        if (Math.abs(kmr) > 1e-7) {
                            return dd_ - sx_
                                    + (Math.exp(kmr * term_) * kappa_ * theta_
                                    - kappa_ * theta_ * (kmr * term_ + 1.0)) / (2 * kmr * kmr)
                                    - v0_ * (1.0 - Math.exp(kmr * term_)) / (2.0 * kmr);
                        } else
                            // \kappa = \rho * \sigma
                            return dd_ - sx_ + 0.25 * kappa_ * theta_ * term_ * term_
                                    + 0.5 * v0_ * term_;
                    } else {
                        return dd_ - sx_
                                - (Math.exp(-kappa_ * term_) * kappa_ * theta_
                                + kappa_ * theta_ * (kappa_ * term_ - 1.0)) / (2 * kappa_ * kappa_)
                                - v0_ * (1.0 - Math.exp(-kappa_ * term_)) / (2 * kappa_);
                    }
                }
            } else if (cpxLog_ == ComplexLogFormula.BranchCorrection) {
                Complex p = t1.add(d).divide(t1.subtract(d));

                // next term: g = std::log((1.0 - p*std::exp(d*term_))/(1.0 - p))
                Complex g;

                // the exp of the following expression is needed.
                Complex e = p.log().add(d.multiply(term_));

                // does it fit to the machine precision?
                if (Math.exp(-e.getReal()) > QL_EPSILON) {
                    g = new Complex(1.0).subtract(p.divide(ex))
                            .divide(new Complex(1.0).subtract(p))
                            .log();
                } else {
                    // use a "big phi" approximation
                    g = d.multiply(term_)
                            .add(p.divide(p.subtract(new Complex(1.0))).log());

                    if (g.getImaginary() > M_PI || g.getImaginary() <= -M_PI) {
                        // get back to principal branch of the complex logarithm
                        double im = g.getImaginary() % (2 * M_PI);
                        if (im > M_PI)
                            im -= 2 * M_PI;
                        else if (im <= -M_PI)
                            im += 2 * M_PI;

                        g = new Complex(g.getReal(), im);
                    }
                }

                // be careful here as we have to use a log branch correction
                // to deal with the discontinuities of the complex logarithm.
                // the principal branch is not always the correct one.
                // (s. A. Sepp, chapter 4)
                // remark: there is still the change that we miss a branch
                // if the order of the integration is not high enough.
                double tmp = g.getImaginary() - g_km1_;
                if (tmp <= -M_PI)
                    ++b_;
                else if (tmp > M_PI)
                    --b_;

                g_km1_ = g.getImaginary();
                g = g.add(new Complex(0.0, 2 * b_ * M_PI));

                return t1.add(d).multiply(ex.subtract(new Complex(1.0)))
                        .multiply(v0_)
                        .divide(ex.subtract(p).multiply(sigma2_))
                        .add(t1.add(d).multiply(term_)
                                .subtract(g.multiply(2.0))
                                .multiply(kappa_ * theta_ / sigma2_))
                        .add(new Complex(0.0, phi * (dd_ - sx_)))
                        .add(addOnTerm)
                        .exp().getImaginary() / phi;
            } else {
                QL_FAIL("unknown complex logarithm formula");
                return 0.0;
            }
        }
    }

    /**
     * Control variate helper for the Andersen-Piterbarg, asymptotic
     * characteristic function and angled-contour Fourier paths.
     * Official {@code AnalyticHestonEngine::AP_Helper}.
     */
    class AP_Helper implements java.util.function.DoubleUnaryOperator {
        private final double term_;
        private final double fwd_, strike_, freq_;
        private final ComplexLogFormula cpxLog_;
        private final AnalyticHestonEngine enginePtr_;
        private final double alpha_, s_alpha_;
        private double vAvg_, tanPhi_;
        private Complex phi_, psi_;

        AP_Helper(double term, double fwd, double strike,
                  ComplexLogFormula cpxLog,
                  AnalyticHestonEngine enginePtr,
                  double alpha) {
            term_ = term;
            fwd_ = fwd;
            strike_ = strike;
            freq_ = Math.log(fwd / strike);
            cpxLog_ = cpxLog;
            enginePtr_ = enginePtr;
            alpha_ = alpha;
            s_alpha_ = Math.exp(alpha * freq_);
            phi_ = Complex.ZERO;
            psi_ = Complex.ZERO;

            QL_REQUIRE(enginePtr != null, "pricing engine required");

            double v0 = enginePtr_.model_.v0();
            double kappa = enginePtr_.model_.kappa();
            double theta = enginePtr_.model_.theta();
            double sigma = enginePtr_.model_.sigma();
            double rho = enginePtr_.model_.rho();

            if (cpxLog_ == ComplexLogFormula.AndersenPiterbarg) {
                vAvg_ = (1 - Math.exp(-kappa * term)) * (v0 - theta)
                        / (kappa * term) + theta;
            } else if (cpxLog_ == ComplexLogFormula.AndersenPiterbargOptCV) {
                double b = 1.0 + alpha_;
                vAvg_ = -2.0 * Math.log(
                        enginePtr_.chF(new Complex(0, -b), term).getReal())
                        / (term * (b - b * b));
            } else if (cpxLog_ == ComplexLogFormula.AsymptoticChF) {
                double s = Math.sqrt(1 - rho * rho);
                phi_ = new Complex(-(v0 + term * kappa * theta) / sigma * s,
                        -(v0 + term * kappa * theta) / sigma * rho);

                double a = sigma * s;
                double b = (0.5 * sigma - rho * kappa) / s;
                Complex l = new Complex(0.5, -0.5 * rho / s);
                Complex B = new Complex(0.0, b - a)
                        .subtract(new Complex(rho * sigma * b - kappa * a, 0.0)
                                .divide(l.multiply(2.0 * a)))
                        .divide(l.multiply(2.0 * a * a));

                psi_ = new Complex(kappa * theta / (sigma * sigma), 0.0)
                        .multiply(new Complex(kappa * term, -b * term)
                                .subtract(l.log().multiply(2.0)))
                        .add(B.multiply(v0))
                        .subtract(new Complex(0.0, alpha_ + 1.0).multiply(phi_));

                vAvg_ = (1 - Math.exp(-kappa * term)) * (v0 - theta)
                        / (kappa * term) + theta;

                double rr = rho - sigma * freq_ / (v0 + kappa * theta * term);
                tanPhi_ = (rr * freq_ < 0.0)
                        ? Math.tan(M_PI / 12 * Math.signum(freq_))
                        : 0.0;
            } else if (cpxLog_ == ComplexLogFormula.AngledContour) {
                vAvg_ = (1 - Math.exp(-kappa * term)) * (v0 - theta)
                        / (kappa * term) + theta;

                double rr = rho - sigma * freq_ / (v0 + kappa * theta * term);
                tanPhi_ = (rr * freq_ < 0.0)
                        ? Math.tan(M_PI / 12 * Math.signum(freq_))
                        : 0.0;
            } else if (cpxLog_ == ComplexLogFormula.AngledContourNoCV) {
                double rr = rho - sigma * freq_ / (v0 + kappa * theta * term);
                tanPhi_ = (rr * freq_ < 0.0)
                        ? Math.tan(M_PI / 12 * Math.signum(freq_))
                        : 0.0;
            } else {
                QL_FAIL("unknown control variate");
            }
        }

        @Override
        public double applyAsDouble(double u) {
            QL_REQUIRE(enginePtr_.addOnTerm(u, term_, 1).equals(Complex.ZERO)
                            && enginePtr_.addOnTerm(u, term_, 2).equals(Complex.ZERO),
                    "only Heston model is supported");

            Complex i = new Complex(0.0, 1.0);

            if (cpxLog_ == ComplexLogFormula.AngledContour
                    || cpxLog_ == ComplexLogFormula.AngledContourNoCV
                    || cpxLog_ == ComplexLogFormula.AsymptoticChF) {
                Complex h_u = new Complex(u, u * tanPhi_ - alpha_);
                Complex hPrime = h_u.subtract(i);

                Complex phiBS = Complex.ZERO;
                if (cpxLog_ == ComplexLogFormula.AngledContour)
                    phiBS = new Complex(-u * tanPhi_ * freq_, 0.0)
                            .subtract(new Complex(0.5 * vAvg_ * term_, 0.0)
                                    .multiply(hPrime.multiply(hPrime)
                                            .add(new Complex(-hPrime.getImaginary(),
                                                    hPrime.getReal()))))
                            .exp();
                else if (cpxLog_ == ComplexLogFormula.AsymptoticChF)
                    phiBS = new Complex(-u * tanPhi_ * freq_, 0.0)
                            .add(new Complex(u, 0.0)
                                    .multiply(new Complex(1, tanPhi_))
                                    .multiply(phi_))
                            .add(psi_)
                            .exp();

                Complex v = enginePtr_.lnChF(hPrime, term_);
                Complex chf_v = (v.getReal() == Double.NEGATIVE_INFINITY)
                        ? Complex.ZERO
                        : new Complex(-u * tanPhi_ * freq_, 0.0).add(v).exp();

                return new Complex(0.0, u * freq_).exp()
                        .multiply(new Complex(1, tanPhi_))
                        .multiply(phiBS.subtract(chf_v))
                        .divide(h_u.multiply(hPrime))
                        .getReal() * s_alpha_;
            } else if (cpxLog_ == ComplexLogFormula.AndersenPiterbarg
                    || cpxLog_ == ComplexLogFormula.AndersenPiterbargOptCV) {
                Complex z = new Complex(u, -alpha_);
                Complex zPrime = new Complex(u, -alpha_ - 1);
                Complex phiBS = new Complex(-0.5 * vAvg_ * term_, 0.0)
                        .multiply(zPrime.multiply(zPrime)
                                .add(new Complex(-zPrime.getImaginary(),
                                        zPrime.getReal())))
                        .exp();

                return new Complex(0.0, u * freq_).exp()
                        .multiply(phiBS.subtract(enginePtr_.chF(zPrime, term_)))
                        .divide(z.multiply(zPrime))
                        .getReal() * s_alpha_;
            } else {
                QL_FAIL("unknown control variate");
                return 0.0;
            }
        }

        double controlVariateValue() {
            if (cpxLog_ == ComplexLogFormula.AngledContour
                    || cpxLog_ == ComplexLogFormula.AndersenPiterbarg
                    || cpxLog_ == ComplexLogFormula.AndersenPiterbargOptCV) {
                return new BlackCalculator(Option.Type.Call, strike_, fwd_,
                        Math.sqrt(vAvg_ * term_), 1.0).value();
            } else if (cpxLog_ == ComplexLogFormula.AsymptoticChF) {
                Complex phiFreq = new Complex(phi_.getReal(),
                        phi_.getImaginary() + freq_);
                Complex half = phiFreq.multiply(0.5);

                if (alpha_ == -0.5) {
                    Complex expr = ExponentialIntegral.Ci(half.negate())
                            .multiply(half.sin()).multiply(-2.0)
                            .add(half.cos().multiply(
                                    new Complex(M_PI, 0.0)
                                            .add(ExponentialIntegral.Si(half)
                                                    .multiply(2.0))));

                    return fwd_ - Math.sqrt(strike_ * fwd_) / M_PI
                            * psi_.exp().multiply(expr).getReal();
                } else {
                    // Re[Integrate[Exp[phi*u + psi]/((u - I*alpha)*(u - I*(alpha + 1))),
                    //     {u, 0, Infinity},
                    //     Assumptions -> Re[phi] < 0 && alpha \[Element] Reals
                    //         && alpha != 0 && alpha != -1]]

                    QL_REQUIRE(alpha_ != -1.0 && alpha_ != 0.0,
                            "alpha != 1.0 && alpha != 0.0");

                    Complex ii = new Complex(0.0, 1.0);
                    Complex phiAlpha = phiFreq.multiply(alpha_);
                    Complex phiOnePlusAlpha = phiFreq.multiply(1.0 + alpha_);

                    Complex first = psi_.exp().multiply(
                            ii.multiply(phiAlpha).exp().multiply(
                                    ii.multiply(new Complex(M_PI, 0.0))
                                            .subtract(ExponentialIntegral.Ci(
                                                    phiAlpha.negate()).multiply(2.0))
                                            .subtract(new Complex(alpha_, 0.0)
                                                    .log().multiply(2.0))
                                            .subtract(phiFreq.negate().log().multiply(2.0))
                                            .add(phiAlpha.negate().log().multiply(2.0))
                                            .add(ii.multiply(ExponentialIntegral.Si(phiAlpha))
                                                    .multiply(2.0))));

                    Complex second = psi_.exp().multiply(
                            ii.multiply(phiOnePlusAlpha).exp().multiply(
                                    ii.multiply(new Complex(M_PI, 0.0)).negate()
                                            .add(ExponentialIntegral.Ci(
                                                    phiOnePlusAlpha.negate()).multiply(2.0))
                                            .add(new Complex(1.0 + alpha_, 0.0)
                                                    .log().multiply(2.0))
                                            .add(phiFreq.negate().log().multiply(2.0))
                                            .subtract(phiOnePlusAlpha.negate().log()
                                                    .multiply(2.0))
                                            .subtract(ii.multiply(ExponentialIntegral.Si(
                                                            phiFreq.multiply(1.0 + alpha_)))
                                                    .multiply(2.0))));

                    Complex sum = first.add(second);

                    return ((alpha_ <= 0.0) ? fwd_ : 0.0)
                            - ((alpha_ <= -1.0) ? strike_ : 0.0)
                            + fwd_ * s_alpha_ / M_PI * 0.5 * sum.getImaginary();
                }
            } else if (cpxLog_ == ComplexLogFormula.AngledContourNoCV) {
                return ((alpha_ <= 0.0) ? fwd_ : 0.0)
                        - ((alpha_ <= -1.0) ? strike_ : 0.0)
                        - 0.5 * ((alpha_ == 0.0) ? fwd_ : 0.0)
                        + 0.5 * ((alpha_ == -1.0) ? strike_ : 0.0);
            } else {
                QL_FAIL("unknown control variate");
                return 0.0;
            }
        }
    }

    @Override
    public void registerObserver(Observer observer) {
        observers_.add(observer);
    }

    @Override
    public void unregisterObserver(Observer observer) {
        observers_.remove(observer);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }
}
