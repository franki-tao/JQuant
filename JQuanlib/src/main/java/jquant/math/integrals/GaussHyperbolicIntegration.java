package jquant.math.integrals;

//! Gauss-Hyperbolic integration
    /*! This class performs a 1-dimensional Gauss-Hyperbolic integration.
        \f[
        \int_{-\inf}^{\inf} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x)=1/cosh(x)
        \f]
    */
public class GaussHyperbolicIntegration extends GaussianQuadrature{
    public GaussHyperbolicIntegration(int n) {
        super(n, new GaussHyperbolicPolynomial());
    }
}
