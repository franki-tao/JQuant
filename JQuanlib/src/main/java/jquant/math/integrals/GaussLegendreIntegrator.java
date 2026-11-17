package jquant.math.integrals;

/**
 * Gauss-Legendre integrator.
 *
 * <p>Java counterpart of the official typedef
 * {@code typedef detail::GaussianQuadratureIntegrator<GaussLegendreIntegration>
 * GaussLegendreIntegrator;} in {@code ql/math/integrals/gaussianquadratures.hpp}.
 */
public class GaussLegendreIntegrator extends GaussianQuadratureIntegrator {
    public GaussLegendreIntegrator(int n) {
        super(n, new GaussLegendreIntegration(n));
    }
}
