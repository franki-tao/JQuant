package jquant.math.integrals;

//! Gauss-Legendre integration
    /*! This class performs a 1-dimensional Gauss-Legendre integration.
        \f[
        \int_{-1}^{1} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x)=1
        \f]
    */
public class GaussLegendreIntegration extends GaussianQuadrature{
    public GaussLegendreIntegration(int n) {
        super(n, new GaussJacobiPolynomial(0.0, 0.0));
    }
}
