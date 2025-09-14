package jquant.math.distributions;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.incompleteBetaFunction;

//! Cumulative Student t-distribution
    /*! Cumulative distribution function for \f$ n \f$ degrees of freedom
        (see mathworld.wolfram.com):
        \f[
        F(x) = \int_{-\infty}^x\,f(y)\,dy
        = \frac{1}{2}\,
        +\,\frac{1}{2}\,sgn(x)\,
        \left[ I\left(1,\frac{n}{2},\frac{1}{2}\right)
        - I\left(\frac{n}{n+y^2}, \frac{n}{2},\frac{1}{2}\right)\right]
        \f]
        where \f$ I(z; a, b) \f$ is the regularized incomplete beta function.
    */
public class CumulativeStudentDistribution {
    private int n_;

    public CumulativeStudentDistribution(int n) {
        QL_REQUIRE(n > 0, "invalid parameter for t-distribution");
        this.n_ = n;
    }

    public double value(double x) {
        double xx = 1.0 * n_ / (x * x + n_);
        double sig = (x > 0 ? 1.0 : -1.0);

        return 0.5 + 0.5 * sig * (incompleteBetaFunction(0.5 * n_, 0.5, 1.0)
                - incompleteBetaFunction(0.5 * n_, 0.5, xx));
    }
}
