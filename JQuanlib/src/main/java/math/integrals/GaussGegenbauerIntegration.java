package math.integrals;

//! Gauss-Gegenbauer integration
    /*! This class performs a 1-dimensional Gauss-Gegenbauer integration.
        \f[
        \int_{-1}^{1} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x)=(1-x^2)^{\lambda-1/2}
        \f]
    */
public class GaussGegenbauerIntegration extends GaussianQuadrature{
    public GaussGegenbauerIntegration(int n, double lambda) {
        super(n, new GaussJacobiPolynomial(lambda - 0.5, lambda - 0.5));
    }
}
