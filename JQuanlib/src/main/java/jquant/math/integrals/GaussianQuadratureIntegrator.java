package jquant.math.integrals;

import jquant.math.Function;

import static jquant.math.MathUtils.NULL_REAL;

/**
 * Integrator based on a fixed Gaussian quadrature rule.
 *
 * <p>Ported from {@code detail::GaussianQuadratureIntegrator} in
 * {@code ql/math/integrals/gaussianquadratures.hpp/.cpp}. The type parameter
 * of the C++ template is replaced by the concrete quadrature passed to the
 * constructor.
 */
public class GaussianQuadratureIntegrator extends Integrator {
    private final GaussianQuadrature integration_;

    public GaussianQuadratureIntegrator(int n, GaussianQuadrature integration) {
        super(NULL_REAL, n);
        integration_ = integration;
    }

    public GaussianQuadrature getIntegration() {
        return integration_;
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        final double c1 = 0.5 * (b - a);
        final double c2 = 0.5 * (a + b);

        return c1 * integration_.value(x -> f.value(c1 * x + c2));
    }
}
