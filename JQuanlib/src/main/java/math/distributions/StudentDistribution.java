package math.distributions;

import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.M_PI;


//! Student t-distribution
    /*! Probability density function for \f$ n \f$ degrees of freedom
        (see mathworld.wolfram.com or wikipedia.org):
        \f[
        f(x) = \frac {\Gamma\left(\frac{n+1}{2}\right)} {\sqrt{n\pi}
        \, \Gamma\left(\frac{n}{2}\right)}\:
        \frac {1} {\left(1+\frac{x^2}{n}\right)^{(n+1)/2}}
        \f]
    */
public class StudentDistribution {
    private int n_;

    public StudentDistribution(int n) {
        QL_REQUIRE(n > 0, "invalid parameter for t-distribution");
        this.n_ = n;
    }

    public double value(double x) {
        GammaFunction G = new GammaFunction();
        double g1 = Math.exp(G.logValue(0.5 * (n_ + 1)));
        double g2 = Math.exp(G.logValue(0.5 * n_));

        double power = Math.pow(1. + x * x / n_, 0.5 * (n_ + 1));

        return g1 / (g2 * power * Math.sqrt(M_PI * n_));
    }
}
