package jquant.math.integrals;

import jquant.math.Function;

import static jquant.math.MathUtils.NULL_SIZE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * Exp-sinh quadrature.
 *
 * <p>Port of the official {@code ql/math/integrals/expsinhintegral.hpp},
 * which wraps Boost's {@code boost::math::quadrature::exp_sinh}. The
 * double-exponential transformation
 * {@code x = exp(pi/2 sinh(t))} maps the half-infinite interval
 * {@code [0, infinity)} onto the whole real line and the resulting
 * integrand is integrated with the trapezoidal rule.
 */
public class ExpSinhIntegral extends Integrator {
    private final double relTolerance_;
    private final int maxRefinements_;

    public ExpSinhIntegral() {
        this(Math.sqrt(QL_EPSILON), 9);
    }

    public ExpSinhIntegral(double relTolerance) {
        this(relTolerance, 9);
    }

    public ExpSinhIntegral(double relTolerance, int maxRefinements) {
        super(QL_MAX_REAL, NULL_SIZE);
        relTolerance_ = relTolerance;
        maxRefinements_ = Math.max(1, maxRefinements);
    }

    /** Integration over the half-infinite interval {@code [0, infinity)}. */
    public double integrate(Function f) {
        setNumberOfEvaluations(0);

        // Double-exponential transformation x = exp(pi/2 sinh(t)),
        // dx = x * pi/2 cosh(t) dt.  The trapezoidal rule in t converges
        // double-exponentially, so the step is halved until two successive
        // levels agree to the requested relative tolerance.
        double h = 1.0;
        double previous = Double.NaN;
        double value = Double.NaN;

        for (int refinement = 1; refinement <= maxRefinements_; ++refinement) {
            value = integrateLevel(f, h);

            if (refinement > 1
                    && Math.abs(value - previous)
                    <= relTolerance_ * Math.max(Math.abs(value),
                    Math.abs(previous))) {
                setAbsoluteError(Math.abs(value - previous));
                return value;
            }
            previous = value;
            h *= 0.5;
        }

        setAbsoluteError(Math.abs(value - previous));
        return value;
    }

    private double integrateLevel(Function f, double h) {
        double sum = 0.0;

        // The transformed integrand is negligible beyond |t| ~ 5 for any
        // integrand that makes the original integral converge; the loop is
        // also stopped once both tail terms are below the requested
        // tolerance relative to the partial sum.
        final int maxSteps = (int) Math.min(1 << 20, 64.0 / h);
        for (int k = 0; k <= maxSteps; ++k) {
            final double t = k * h;

            final double positive = transformedTerm(f, t);
            final double negative = (k == 0) ? 0.0 : transformedTerm(f, -t);
            final double term = positive + negative;
            sum += term;

            if (k > 0 && t > 1.0 && sum != 0.0
                    && Math.abs(positive) + Math.abs(negative)
                    < 1e-3 * relTolerance_ * Math.abs(sum)) {
                break;
            }
        }

        return h * sum;
    }

    private double transformedTerm(Function f, double t) {
        final double exponent = M_PI_2 * Math.sinh(t);
        if (exponent > 700.0) {
            return 0.0;
        }

        final double x = Math.exp(exponent);
        final double w = x * M_PI_2 * Math.cosh(t);

        if (!Double.isFinite(x) || !Double.isFinite(w)) {
            return 0.0;
        }

        final double fx;
        try {
            fx = f.value(x);
            increaseNumberOfEvaluations(1);
        } catch (RuntimeException e) {
            return 0.0;
        }

        final double term = w * fx;
        return Double.isFinite(term) ? term : 0.0;
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        // FourierIntegration calls this with b = QL_MAX_REAL to represent
        // the half-infinite interval [a, infinity).
        if (Double.isInfinite(b) || b >= 0.5 * QL_MAX_REAL) {
            final boolean shifted = (a != 0.0);
            if (!shifted) {
                return integrate(f);
            }
            return integrate(x -> f.value(x + a));
        }

        // Finite interval: fall back to the existing tanh-sinh routine.
        return new TanhSinhIntegral(relTolerance_, maxRefinements_,
                Double.MIN_VALUE * 4).value(f, a, b);
    }

    private static final double M_PI_2 = Math.PI / 2.0;
}
