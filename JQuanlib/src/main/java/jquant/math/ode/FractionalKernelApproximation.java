package jquant.math.ode;

import jquant.math.Array;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Sum-of-exponentials approximation of the fractional kernel.
 *
 * <p>Official QuantLib {@code ql/math/ode/fractionalkernelapproximation.hpp}
 * port. Approximates the power-law kernel
 * <pre>
 *     K(t) = t^(alpha - 1) / Gamma(alpha),  alpha in (0, 1],
 * </pre>
 * by {@code K^n(t) = sum_{i=1}^n c_i exp(-x_i t)}. Following Abi Jaber, K is
 * completely monotone with Bernstein representation
 * <pre>
 *     K(t) = int_0^inf exp(-x t) mu(dx),
 *     mu(dx) = x^(-alpha) / (Gamma(alpha) Gamma(1 - alpha)) dx,
 * </pre>
 * and a partition {@code 0 = eta_0 < eta_1 < ... < eta_n} gives non-negative
 * {@code c_i, x_i} by collapsing each interval's mass onto its barycentre.
 *
 * <p>References:
 * E. Abi Jaber, Lifting the Heston model, Quantitative Finance 19(12),
 * 1995-2013 (2019).
 *
 * <p>E. Abi Jaber and O. El Euch, Multifactor approximation of rough
 * volatility models, SIAM Journal on Financial Mathematics 10(2), 309-349
 * (2019).
 */
public class FractionalKernelApproximation {

    /**
     * Official {@code detail::expm1OverX}: {@code (exp(y) - 1) / y},
     * continuous at the origin.
     */
    public static double expm1OverX(double y) {
        if (Math.abs(y) < 1e-8)
            return 1.0 + 0.5 * y;
        return Math.expm1(y) / y;
    }

    /**
     * Official {@code detail::fractionalKernelNorm}:
     * {@code sin(pi b) / (pi b)}, the reflection-formula rewriting of
     * {@code 1 / ((1 - alpha) Gamma(alpha) Gamma(1 - alpha))} for
     * {@code b = 1 - alpha}. {@code Gamma(1 - alpha)} overflows as
     * {@code alpha -> 1} whereas this form tends to one.
     */
    public static double fractionalKernelNorm(double b) {
        final double y = Math.PI * b;

        if (Math.abs(y) < 1e-8)
            return 1.0 - y * y / 6.0;
        return Math.sin(y) / y;
    }

    private final Array c_;
    private final Array x_;

    public FractionalKernelApproximation(double alpha, int n, double tMax) {
        QL_REQUIRE(tMax > 0.0, "tMax (" + tMax + ") must be positive");
        QL_REQUIRE(n > 0, "at least one factor required");

        final double etaLo = 1.0 / (Math.sqrt((double) n) * tMax);
        final double etaHi = 4.0 * Math.pow((double) n, 2.5) / tMax;

        QL_REQUIRE(alpha > 0.0 && alpha <= 1.0,
                "fractional order alpha (" + alpha + ") must be in (0, 1]");
        QL_REQUIRE(etaLo > 0.0 && etaHi >= etaLo,
                "partition endpoints (" + etaLo + ", " + etaHi
                        + ") must satisfy 0 < etaLo <= etaHi");

        final double b = 1.0 - alpha;
        final double norm = fractionalKernelNorm(b);

        c_ = new Array(n);
        x_ = new Array(n);

        // A one-factor partition has to put its single node at the fast end,
        // so that the whole mass still sits on [0, eta_1]
        final double eta1 = (n == 1) ? etaHi : etaLo;

        // The first interval is [0, eta_1], whose mass tends to the total
        // mass as alpha -> 1; this is what recovers the constant kernel
        c_.set(0, Math.pow(eta1, b) * norm);
        x_.set(0, b / (1.0 + b) * eta1);

        if (n == 1)
            return;

        final double dL = Math.log(etaHi / etaLo) / (n - 1);
        final double r = Math.exp(dL);

        // Both weight and rate go through expm1, so that the differences
        // eta_i^b - eta_{i-1}^b, which suffer catastrophic cancellation as
        // alpha -> 1, are never formed explicitly
        final double weightFactor = Math.expm1(b * dL) * norm;
        final double rateFactor =
                expm1OverX((1.0 + b) * dL) / expm1OverX(b * dL);

        double eta = eta1;
        for (int i = 1; i < n; ++i) {
            c_.set(i, Math.pow(eta, b) * weightFactor);
            x_.set(i, eta * rateFactor);

            eta *= r;
        }
    }

    /** Weights c_i. */
    public Array weights() {
        return c_;
    }

    /** Mean-reversion rates x_i. */
    public Array rates() {
        return x_;
    }

    public int size() {
        return c_.size();
    }

    /** {@code K^n(t) = sum_i c_i exp(-x_i t)}. */
    public double value(double t) {
        double value = 0.0;
        for (int i = 0; i < c_.size(); ++i)
            value += c_.get(i) * Math.exp(-x_.get(i) * t);

        return value;
    }
}
