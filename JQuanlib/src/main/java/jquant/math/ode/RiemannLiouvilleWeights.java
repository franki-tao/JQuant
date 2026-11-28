package jquant.math.ode;

import jquant.math.distributions.GammaFunction;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Quadrature weights of the product-trapezoidal Riemann-Liouville integral.
 *
 * <p>Official QuantLib {@code ql/math/ode/fractionaladams.hpp} port. The
 * weights depend only on the integration order {@code alpha} and on the
 * number of grid steps. Callers that integrate repeatedly on the same grid,
 * as the rough Heston characteristic function does once per quadrature node,
 * can build once and reuse.
 */
public class RiemannLiouvilleWeights {

    private final double alpha_;
    private final int steps_;
    private final double gamma_;
    private final double[] w_;

    public RiemannLiouvilleWeights(double alpha, int steps) {
        QL_REQUIRE(alpha >= 0.0,
                "integration order alpha (" + alpha + ") must be non-negative");
        QL_REQUIRE(steps > 0, "at least one grid step required");

        alpha_ = alpha;
        steps_ = steps;
        gamma_ = new GammaFunction().value(alpha + 2.0);
        w_ = new double[steps];

        // powers m^(alpha + 1) for m = 0, ..., steps + 1, reused across the
        // shifted (k - 1, k, k + 1) terms in the weights below
        final double[] powAlphaPlus1 = new double[steps + 2];
        for (int m = 0; m <= steps + 1; ++m) {
            powAlphaPlus1[m] = Math.pow((double) m, alpha + 1.0);
        }

        // weight of y_0: (n - 1)^(alpha + 1) - (n - 1 - alpha) n^alpha
        w_[0] = powAlphaPlus1[steps - 1]
                - (steps - 1 - alpha) * Math.pow((double) steps, alpha);

        for (int j = 1; j < steps; ++j) {
            final int k = steps - j;
            w_[j] = powAlphaPlus1[k + 1] + powAlphaPlus1[k - 1]
                    - 2.0 * powAlphaPlus1[k];
        }
    }

    public double alpha() {
        return alpha_;
    }

    /**
     * {@code I^alpha y(t_N)} for real grid values {@code y} of spacing
     * {@code dt}.
     */
    public double integrate(double[] y, double dt) {
        checkGrid(y.length, dt);

        // y[steps_] carries weight exactly 1 and is not stored, so w_ is
        // one shorter than y
        double sum = 0.0;
        for (int j = 0; j < steps_; ++j) {
            sum += w_[j] * y[j];
        }
        sum += y[steps_];

        return Math.pow(dt, alpha_) / gamma_ * sum;
    }

    /**
     * {@code I^alpha y(t_N)} for complex grid values {@code y} of spacing
     * {@code dt}.
     */
    public Complex integrate(Complex[] y, double dt) {
        checkGrid(y.length, dt);

        double sumRe = 0.0;
        double sumIm = 0.0;
        for (int j = 0; j < steps_; ++j) {
            final double weight = w_[j];
            sumRe += weight * y[j].getReal();
            sumIm += weight * y[j].getImaginary();
        }
        sumRe += y[steps_].getReal();
        sumIm += y[steps_].getImaginary();

        final double factor = Math.pow(dt, alpha_) / gamma_;
        return new Complex(factor * sumRe, factor * sumIm);
    }

    private void checkGrid(int size, double dt) {
        QL_REQUIRE(size == steps_ + 1,
                "grid size (" + size + ") does not match the weights ("
                        + (steps_ + 1) + " values expected)");
        QL_REQUIRE(dt > 0.0,
                "grid spacing dt (" + dt + ") must be positive");
    }
}
