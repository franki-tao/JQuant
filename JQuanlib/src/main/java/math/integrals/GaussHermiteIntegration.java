package math.integrals;

//! generalized Gauss-Hermite integration
    /*! This class performs a 1-dimensional Gauss-Hermite integration.
        \f[
        \int_{-\inf}^{\inf} f(x) \mathrm{d}x
        \f]
        The weighting function is
        \f[
            w(x;\mu)=|x|^{2\mu} \exp{-x*x}
        \f]
        and \f[ \mu > -0.5 \f]
    */
public class GaussHermiteIntegration extends GaussianQuadrature{
    public GaussHermiteIntegration(int n) {
        super(n, new GaussHermitePolynomial(0));
    }
    public GaussHermiteIntegration(int n, double mu) {
        super(n, new GaussHermitePolynomial(mu));
    }
}
