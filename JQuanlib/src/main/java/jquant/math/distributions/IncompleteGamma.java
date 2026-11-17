package jquant.math.distributions;

import org.apache.commons.math3.special.Gamma;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Regularized incomplete gamma function and its inverse, with the same
 * semantics as Boost's {@code boost::math::gamma_q} / {@code gamma_q_inv}
 * used by the official no-arbitrage SABR implementation.
 *
 * <p>The forward function is taken from commons-math3.  Commons-math has no
 * inverse, so a bracketed Newton iteration on the forward function is used;
 * the inversion is accurate to (close to) machine precision, which is needed
 * because the official no-arbitrage SABR test compares implied volatilities
 * with an absolute tolerance of {@code 1.0e-12}.</p>
 */
public final class IncompleteGamma {
    private static final int MAX_NEWTON_ITERATIONS = 300;

    private IncompleteGamma() {
    }

    /** Boost {@code gamma_q(a, x)}: the regularized upper incomplete gamma. */
    public static double gammaQ(double a, double x) {
        QL_REQUIRE(a > 0.0, "invalid parameter for gamma distribution");
        if (Double.isNaN(x))
            return Double.NaN;
        if (x <= 0.0)
            return 1.0;
        if (Double.isInfinite(x))
            return 0.0;
        return Gamma.regularizedGammaQ(a, x);
    }

    /** Alias matching the Boost name. */
    public static double gamma_q(double a, double x) {
        return gammaQ(a, x);
    }

    /**
     * Boost {@code gamma_q_inv(a, q)}: the value {@code x} such that
     * {@code gamma_q(a, x) == q}, with {@code 0 < q < 1}.
     */
    public static double gammaQInv(double a, double q) {
        QL_REQUIRE(a > 0.0, "invalid parameter for gamma distribution");
        QL_REQUIRE(q >= 0.0 && q <= 1.0, "gamma_q_inv: probability out of range");
        if (q <= 0.0)
            return Double.POSITIVE_INFINITY;
        if (q >= 1.0)
            return 0.0;

        final double logGamma = Gamma.logGamma(a);

        // initial guess
        double x0;
        if (q < 0.5) {
            // Leading term of the upper incomplete gamma for large x:
            // Q(a,x) ~ x^(a-1) exp(-x) / Gamma(a), i.e.
            // x - (a-1) log x = -log(q) - log Gamma(a).
            final double target = -Math.log(q) - logGamma;
            double x = Math.max(1.0, target);
            for (int i = 0; i < 100; ++i) {
                double next = target + (a - 1.0) * Math.log(x);
                if (!(next > 0.0))
                    next = 0.5 * x;
                if (Math.abs(next - x) <= 1.0e-14 * Math.max(1.0, x)) {
                    x = next;
                    break;
                }
                x = next;
            }
            x0 = x;
        } else {
            x0 = 1.0;
        }
        if (!(x0 > 0.0) || Double.isInfinite(x0))
            x0 = 1.0;

        // bracket Q(lo) >= q >= Q(hi)
        double lo = 0.5 * x0;
        int guard = 0;
        while (gammaQ(a, lo) < q && guard++ < 4000)
            lo *= 0.5;
        if (!(lo > 0.0))
            lo = Double.MIN_VALUE;

        double hi = Math.max(1.0, 2.0 * x0);
        guard = 0;
        while (gammaQ(a, hi) > q && guard++ < 4000 && !Double.isInfinite(hi))
            hi *= 2.0;
        if (Double.isInfinite(hi))
            hi = Double.MAX_VALUE;

        double x = Math.min(Math.max(x0, lo), hi);
        for (int it = 0; it < MAX_NEWTON_ITERATIONS; ++it) {
            double f = gammaQ(a, x) - q;
            if (f == 0.0)
                return x;
            if (f > 0.0)
                lo = x;
            else
                hi = x;

            // -dQ/dx
            double density = Math.exp((a - 1.0) * Math.log(x) - x - logGamma);
            double next = (density > 0.0 && !Double.isInfinite(density))
                    ? x + f / density
                    : 0.5 * (lo + hi);
            if (!(next > lo && next < hi))
                next = 0.5 * (lo + hi);

            if (Math.abs(next - x) <= 4.0 * Math.ulp(Math.max(1.0, Math.abs(x))))
                return next;
            x = next;
        }
        return x;
    }

    /** Alias matching the Boost name. */
    public static double gamma_q_inv(double a, double q) {
        return gammaQInv(a, q);
    }
}
