package jquant.math.integrals;

import jquant.math.Function;
import jquant.math.solvers1d.Brent;

import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * Official QuantLib {@code ql/math/integrals/fourierintegration.hpp/.cpp} port.
 *
 * <p>Fourier integration used by characteristic-function pricing engines.
 * Wraps a quadrature or an adaptive integrator together with the change of
 * variables that maps the semi-infinite Fourier integration range onto the
 * domain the chosen algorithm expects. The decay rate {@code c_inf} of the
 * characteristic function along the integration contour drives that mapping.
 */
public class FourierIntegration {

    private enum Algorithm {
        GaussLobatto, GaussKronrod, Simpson, Trapezoid,
        DiscreteTrapezoid, DiscreteSimpson,
        GaussLaguerre, GaussLegendre, GaussChebyshev, GaussChebyshev2nd,
        ExpSinh, TanhSinh
    }

    private final Algorithm intAlgo_;
    private final Integrator integrator_;
    private final GaussianQuadrature gaussianQuadrature_;

    private FourierIntegration(Algorithm intAlgo, Integrator integrator) {
        intAlgo_ = intAlgo;
        integrator_ = integrator;
        gaussianQuadrature_ = null;
    }

    private FourierIntegration(Algorithm intAlgo, GaussianQuadrature gaussianQuadrature) {
        intAlgo_ = intAlgo;
        integrator_ = null;
        gaussianQuadrature_ = gaussianQuadrature;
    }

    // non-adaptive routines

    public static FourierIntegration gaussLaguerre() {
        return gaussLaguerre(128);
    }

    public static FourierIntegration gaussLaguerre(int integrationOrder) {
        return new FourierIntegration(Algorithm.GaussLaguerre,
                new GaussLaguerreIntegration(integrationOrder));
    }

    public static FourierIntegration gaussLegendre() {
        return gaussLegendre(128);
    }

    public static FourierIntegration gaussLegendre(int integrationOrder) {
        return new FourierIntegration(Algorithm.GaussLegendre,
                new GaussLegendreIntegration(integrationOrder));
    }

    public static FourierIntegration gaussChebyshev() {
        return gaussChebyshev(128);
    }

    public static FourierIntegration gaussChebyshev(int integrationOrder) {
        return new FourierIntegration(Algorithm.GaussChebyshev,
                new GaussChebyshevIntegration(integrationOrder));
    }

    public static FourierIntegration gaussChebyshev2nd() {
        return gaussChebyshev2nd(128);
    }

    public static FourierIntegration gaussChebyshev2nd(int integrationOrder) {
        return new FourierIntegration(Algorithm.GaussChebyshev2nd,
                new GaussChebyshev2ndIntegration(integrationOrder));
    }

    // adaptive routines

    public static FourierIntegration gaussLobatto(double relTolerance,
                                                  double absTolerance) {
        return gaussLobatto(relTolerance, absTolerance, 1000, false);
    }

    public static FourierIntegration gaussLobatto(double relTolerance,
                                                  double absTolerance,
                                                  int maxEvaluations) {
        return gaussLobatto(relTolerance, absTolerance, maxEvaluations,
                false);
    }

    public static FourierIntegration gaussLobatto(double relTolerance,
                                                  double absTolerance,
                                                  int maxEvaluations,
                                                  boolean useConvergenceEstimate) {
        return new FourierIntegration(Algorithm.GaussLobatto,
                new GaussLobattoIntegral(maxEvaluations, absTolerance,
                        relTolerance, useConvergenceEstimate));
    }

    public static FourierIntegration gaussKronrod(double absTolerance,
                                                  int maxEvaluations) {
        return new FourierIntegration(Algorithm.GaussKronrod,
                new GaussKronrodAdaptive(absTolerance, maxEvaluations));
    }

    public static FourierIntegration gaussKronrod(double absTolerance) {
        return gaussKronrod(absTolerance, 1000);
    }

    public static FourierIntegration simpson(double absTolerance,
                                             int maxEvaluations) {
        return new FourierIntegration(Algorithm.Simpson,
                new SimpsonIntegral(absTolerance, maxEvaluations));
    }

    public static FourierIntegration simpson(double absTolerance) {
        return simpson(absTolerance, 1000);
    }

    public static FourierIntegration trapezoid(double absTolerance,
                                               int maxEvaluations) {
        return new FourierIntegration(Algorithm.Trapezoid,
                new TrapezoidIntegral(absTolerance, maxEvaluations,
                        TrapezoidIntegral.POLICY.Default));
    }

    public static FourierIntegration trapezoid(double absTolerance) {
        return trapezoid(absTolerance, 1000);
    }

    public static FourierIntegration discreteSimpson(int evaluations) {
        return new FourierIntegration(Algorithm.DiscreteSimpson,
                new DiscreteSimpsonIntegrator(evaluations));
    }

    public static FourierIntegration discreteSimpson() {
        return discreteSimpson(1000);
    }

    public static FourierIntegration discreteTrapezoid(int evaluations) {
        return new FourierIntegration(Algorithm.DiscreteTrapezoid,
                new DiscreteTrapezoidIntegrator(evaluations));
    }

    public static FourierIntegration discreteTrapezoid() {
        return discreteTrapezoid(1000);
    }

    public static FourierIntegration expSinh(double relTolerance) {
        return new FourierIntegration(Algorithm.ExpSinh,
                new ExpSinhIntegral(relTolerance));
    }

    public static FourierIntegration expSinh() {
        return expSinh(1e-8);
    }

    public static FourierIntegration tanhSinh(double relTolerance) {
        return new FourierIntegration(Algorithm.TanhSinh,
                new TanhSinhIntegral(relTolerance));
    }

    public static FourierIntegration tanhSinh() {
        return tanhSinh(1e-8);
    }

    public double calculate(double c_inf, DoubleUnaryOperator f) {
        return calculate(c_inf, f, null, 1.0);
    }

    public double calculate(double c_inf,
                            DoubleUnaryOperator f,
                            double maxBound) {
        return calculate(c_inf, f, () -> maxBound, 1.0);
    }

    public double calculate(double c_inf,
                            DoubleUnaryOperator f,
                            DoubleSupplier maxBound,
                            double scaling) {
        double retVal;

        switch (intAlgo_) {
            case GaussLaguerre:
                // Official GaussianQuadrature::operator() sums
                // i = Integer(order())-1 downto 0 (gaussianquadratures.hpp).
                retVal = gaussianQuadrature_.value(x -> f.applyAsDouble(x));
                break;
            case GaussLegendre:
            case GaussChebyshev:
            case GaussChebyshev2nd:
                retVal = gaussianQuadrature_.value(
                        new Integrand1(c_inf, f));
                break;
            case ExpSinh:
                retVal = scaling * integrator_.value(
                        x -> f.applyAsDouble(scaling * x), 0.0, QL_MAX_REAL);
                break;
            case TanhSinh:
                retVal = integrator_.value(x -> f.applyAsDouble(x),
                        0.0, QL_MAX_REAL);
                break;
            case Simpson:
            case Trapezoid:
            case GaussLobatto:
            case GaussKronrod:
                if (maxBound != null && maxBound.getAsDouble() != NULL_REAL)
                    retVal = integrator_.value(x -> f.applyAsDouble(x),
                            0.0, maxBound.getAsDouble());
                else
                    retVal = integrator_.value(
                            new Integrand2(c_inf, f), 0.0, 1.0);
                break;
            case DiscreteTrapezoid:
            case DiscreteSimpson:
                if (maxBound != null && maxBound.getAsDouble() != NULL_REAL)
                    retVal = integrator_.value(x -> f.applyAsDouble(x),
                            0.0, maxBound.getAsDouble());
                else
                    retVal = integrator_.value(
                            new Integrand3(c_inf, f), 0.0, 1.0);
                break;
            default:
                QL_FAIL("unknwon integration algorithm");
                retVal = 0.0;
        }

        return retVal;
    }

    public int numberOfEvaluations() {
        if (integrator_ != null)
            return integrator_.numberOfEvaluations();
        else if (gaussianQuadrature_ != null)
            return gaussianQuadrature_.order();
        else {
            QL_FAIL("neither Integrator nor GaussianQuadrature given");
            return 0;
        }
    }

    public boolean isAdaptiveIntegration() {
        return intAlgo_ == Algorithm.GaussLobatto
                || intAlgo_ == Algorithm.GaussKronrod
                || intAlgo_ == Algorithm.Simpson
                || intAlgo_ == Algorithm.Trapezoid
                || intAlgo_ == Algorithm.ExpSinh
                || intAlgo_ == Algorithm.TanhSinh;
    }

    /**
     * Official {@code integrand1}: change of variables used by the
     * Gauss-Legendre / Gauss-Chebyshev rules.
     */
    private static class Integrand1 implements Function {
        private final double c_inf_;
        private final DoubleUnaryOperator f_;

        Integrand1(double c_inf, DoubleUnaryOperator f) {
            c_inf_ = c_inf;
            f_ = f;
        }

        @Override
        public double value(double x) {
            if ((1.0 - x) * c_inf_ > QL_EPSILON)
                return f_.applyAsDouble(-Math.log(0.5 - 0.5 * x) / c_inf_)
                        / ((1.0 - x) * c_inf_);
            else
                return 0.0;
        }
    }

    /**
     * Official {@code integrand2}: change of variables
     * {@code x -> -log(x)/c_inf} used when no explicit integration bound is
     * given.
     */
    private static class Integrand2 implements Function {
        private final double c_inf_;
        private final DoubleUnaryOperator f_;

        Integrand2(double c_inf, DoubleUnaryOperator f) {
            c_inf_ = c_inf;
            f_ = f;
        }

        @Override
        public double value(double x) {
            if (x * c_inf_ > QL_EPSILON)
                return f_.applyAsDouble(-Math.log(x) / c_inf_) / (x * c_inf_);
            else
                return 0.0;
        }
    }

    /** Official {@code integrand3}: {@code integrand2(1-x)}. */
    private static class Integrand3 implements Function {
        private final Integrand2 int_;

        Integrand3(double c_inf, DoubleUnaryOperator f) {
            int_ = new Integrand2(c_inf, f);
        }

        @Override
        public double value(double x) {
            return int_.value(1.0 - x);
        }
    }

    private static class UMax implements Function {
        private final double c_inf_, logEpsilon_;

        UMax(double c_inf, double epsilon) {
            c_inf_ = c_inf;
            logEpsilon_ = Math.log(epsilon);
        }

        @Override
        public double value(double u) {
            return c_inf_ * u + Math.log(u) + logEpsilon_;
        }
    }

    private static class UHatMax implements Function {
        private final double v0T2_, logEpsilon_;

        UHatMax(double v0T2, double epsilon) {
            v0T2_ = v0T2;
            logEpsilon_ = Math.log(epsilon);
        }

        @Override
        public double value(double u) {
            return v0T2_ * u * u + Math.log(u) + logEpsilon_;
        }
    }

    public static double andersenPiterbargIntegrationLimit(double c_inf,
                                                           double epsilon,
                                                           double v0,
                                                           double t) {
        double uMaxGuess = -Math.log(epsilon) / c_inf;
        double uMaxStep = 0.1 * uMaxGuess;

        Brent brent = new Brent();
        double uMax = brent.solve(new UMax(c_inf, epsilon),
                QL_EPSILON * uMaxGuess, uMaxGuess, uMaxStep);

        try {
            double uHatMaxGuess = Math.sqrt(-Math.log(epsilon) / (0.5 * v0 * t));
            double uHatMax = new Brent().solve(
                    new UHatMax(0.5 * v0 * t, epsilon),
                    QL_EPSILON * uHatMaxGuess, uHatMaxGuess,
                    0.001 * uHatMaxGuess);

            return Math.max(uMax, uHatMax);
        } catch (IllegalArgumentException e) {
            return uMax;
        }
    }
}
