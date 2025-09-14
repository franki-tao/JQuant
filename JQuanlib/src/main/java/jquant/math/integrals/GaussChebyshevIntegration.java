package jquant.math.integrals;

//! Gauss-Chebyshev integration
    /*! This class performs a 1-dimensional Gauss-Chebyshev integration.
        \f[
        \int_{-1}^{1} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x)=(1-x^2)^{-1/2}
        \f]
    */
public class GaussChebyshevIntegration extends GaussianQuadrature{
    public GaussChebyshevIntegration(int n) {
        super(n, new GaussJacobiPolynomial(-0.5, -0.5));
    }
}
