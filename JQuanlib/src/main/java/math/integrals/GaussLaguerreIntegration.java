package math.integrals;

//! generalized Gauss-Laguerre integration
    /*! This class performs a 1-dimensional Gauss-Laguerre integration.
        \f[
        \int_{0}^{\inf} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x;s)=x^s \exp{-x}
        \f]
        and \f[ s > -1 \f]
    */
public class GaussLaguerreIntegration extends GaussianQuadrature{
    public GaussLaguerreIntegration(int n) {
        super(n, new GaussLaguerrePolynomial(0));
    }

    public GaussLaguerreIntegration(int n, double s) {
        super(n, new GaussLaguerrePolynomial(s));
    }
}
