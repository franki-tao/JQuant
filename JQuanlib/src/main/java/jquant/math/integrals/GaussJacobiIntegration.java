package jquant.math.integrals;

//! Gauss-Jacobi integration
    /*! This class performs a 1-dimensional Gauss-Jacobi integration.
        \f[
        \int_{-1}^{1} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x;\alpha,\beta)=(1-x)^\alpha (1+x)^\beta
        \f]
    */
public class GaussJacobiIntegration extends GaussianQuadrature{
    public GaussJacobiIntegration(int n, double alpha, double beta) {
        super(n, new GaussJacobiPolynomial(alpha, beta));
    }
}
