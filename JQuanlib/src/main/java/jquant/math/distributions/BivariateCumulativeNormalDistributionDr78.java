package jquant.math.distributions;


import jquant.math.CommonUtil;

import java.util.Arrays;
import java.util.List;

import static jquant.math.MathUtils.M_PI;

//! Cumulative bivariate normal distribution function
    /*! Drezner (1978) algorithm, six decimal places accuracy.

        For this implementation see
       "Option pricing formulas", E.G. Haug, McGraw-Hill 1998

        \todo check accuracy of this algorithm and compare with:
              1) Drezner, Z, (1978),
                 Computation of the bivariate normal integral,
                 Mathematics of Computation 32, pp. 277-279.
              2) Drezner, Z. and Wesolowsky, G. O. (1990)
                 `On the Computation of the Bivariate Normal Integral',
                 Journal of Statistical Computation and Simulation 35,
                 pp. 101-107.
              3) Drezner, Z (1992)
                 Computation of the Multivariate Normal Integral,
                 ACM Transactions on Mathematics Software 18, pp. 450-460.
              4) Drezner, Z (1994)
                 Computation of the Trivariate Normal Integral,
                 Mathematics of Computation 62, pp. 289-294.
              5) Genz, A. (1992)
                `Numerical Computation of the Multivariate Normal
                 Probabilities', J. Comput. Graph. Stat. 1, pp. 141-150.

        \test the correctness of the returned value is tested by
              checking it against known good results.
    */
public class BivariateCumulativeNormalDistributionDr78 {
    private static final List<Double> x_ = Arrays.asList(0.24840615,
            0.39233107,
            0.21141819,
            0.03324666,
            0.00082485334);

    private static final List<Double> y_ = Arrays.asList(0.10024215,
            0.48281397,
            1.06094980,
            1.77972940,
            2.66976040000);
    private double rho_;
    private double rho2_;

    public BivariateCumulativeNormalDistributionDr78(double rho) {
        CommonUtil.QL_REQUIRE(rho >= -1.0, "rho must be >= -1.0");
        CommonUtil.QL_REQUIRE(rho <= 1.0, "rho must be <= 1.0");
        rho_ = rho;
        rho2_ = rho * rho;
    }

    public double value(double a, double b) {
        CumulativeNormalDistribution cumNormalDist = new CumulativeNormalDistribution();
        double CumNormDistA = cumNormalDist.value(a);
        double CumNormDistB = cumNormalDist.value(b);
        double MaxCumNormDistAB = Math.max(CumNormDistA, CumNormDistB);
        double MinCumNormDistAB = Math.min(CumNormDistA, CumNormDistB);

        if (1.0 - MaxCumNormDistAB < 1e-15)
            return MinCumNormDistAB;

        if (MinCumNormDistAB < 1e-15)
            return MinCumNormDistAB;

        double a1 = a / Math.sqrt(2.0 * (1.0 - rho2_));
        double b1 = b / Math.sqrt(2.0 * (1.0 - rho2_));

        double result = -1.0;

        if (a <= 0.0 && b <= 0 && rho_ <= 0) {
            double sum = 0.0;
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    sum += x_.get(i) * x_.get(j) *
                            Math.exp(a1 * (2.0 * y_.get(i) - a1) + b1 * (2.0 * y_.get(j) - b1)
                                    + 2.0 * rho_ * (y_.get(i) - a1) * (y_.get(j) - b1));
                }
            }
            result = Math.sqrt(1.0 - rho2_) / M_PI * sum;
        } else if (a <= 0 && b >= 0 && rho_ >= 0) {
            BivariateCumulativeNormalDistributionDr78 bivCumNormalDist = new BivariateCumulativeNormalDistributionDr78(-rho_);
            result = CumNormDistA - bivCumNormalDist.value(a, -b);
        } else if (a >= 0.0 && b <= 0.0 && rho_ >= 0.0) {
            BivariateCumulativeNormalDistributionDr78 bivCumNormalDist = new BivariateCumulativeNormalDistributionDr78(-rho_);
            result = CumNormDistB - bivCumNormalDist.value(-a, b);
        } else if (a >= 0.0 && b >= 0.0 && rho_ <= 0.0) {
            result = CumNormDistA + CumNormDistB - 1.0 + value(-a, -b);
        } else if (a * b * rho_ > 0.0) {
            double rho1 = (rho_ * a - b) * (a > 0.0 ? 1.0 : -1.0) /
                    Math.sqrt(a * a - 2.0 * rho_ * a * b + b * b);
            BivariateCumulativeNormalDistributionDr78 bivCumNormalDist = new BivariateCumulativeNormalDistributionDr78(rho1);

            double rho2 = (rho_ * b - a) * (b > 0.0 ? 1.0 : -1.0) /
                    Math.sqrt(a * a - 2.0 * rho_ * a * b + b * b);
            BivariateCumulativeNormalDistributionDr78 CBND2 = new BivariateCumulativeNormalDistributionDr78(rho2);

            double delta = (1.0 - (a > 0.0 ? 1.0 : -1.0) * (b > 0.0 ? 1.0 : -1.0)) / 4.0;

            result = bivCumNormalDist.value(a, 0.0) + CBND2.value(b, 0.0) - delta;
        } else {
            CommonUtil.QL_FAIL("case not handled");
        }

        return result;
    }

}



























