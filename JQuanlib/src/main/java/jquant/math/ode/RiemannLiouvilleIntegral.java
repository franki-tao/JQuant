package jquant.math.ode;

import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Product-trapezoidal Riemann-Liouville fractional integral.
 *
 * <p>Official QuantLib free function {@code riemannLiouvilleIntegral} from
 * {@code ql/math/ode/fractionaladams.hpp}. Approximates
 * <pre>
 *     I^alpha y(t_N) = 1 / Gamma(alpha) * int_0^{t_N} (t_N - s)^(alpha-1) y(s) ds,
 *     alpha &gt;= 0,
 * </pre>
 * given values of {@code y} on a uniform grid, by integrating the piecewise
 * linear interpolant of {@code y} against the kernel. For {@code alpha = 1}
 * this is the trapezoidal rule; {@code alpha -&gt; 0} recovers the identity.
 *
 * <p>The weights are rebuilt on every call. Callers integrating repeatedly
 * on the same {@code (alpha, N)} grid should hold a
 * {@link RiemannLiouvilleWeights} instead.
 */
public final class RiemannLiouvilleIntegral {

    private RiemannLiouvilleIntegral() {
    }

    /** Real-valued overload of the official templated free function. */
    public static double integrate(double[] y, double alpha, double dt) {
        checkArguments(y.length, alpha, dt);
        return new RiemannLiouvilleWeights(alpha, y.length - 1).integrate(y, dt);
    }

    /** Complex-valued overload of the official templated free function. */
    public static Complex integrate(Complex[] y, double alpha, double dt) {
        checkArguments(y.length, alpha, dt);
        return new RiemannLiouvilleWeights(alpha, y.length - 1).integrate(y, dt);
    }

    private static void checkArguments(int size, double alpha, double dt) {
        QL_REQUIRE(alpha >= 0.0,
                "integration order alpha (" + alpha + ") must be non-negative");
        QL_REQUIRE(size >= 2, "at least two grid values required");
        QL_REQUIRE(dt > 0.0,
                "grid spacing dt (" + dt + ") must be positive");
    }
}
