package jquant.math.copulas;

import jquant.math.distributions.BivariateCumulativeNormalDistributionWe04DP;
import jquant.math.distributions.InverseCumulativeNormal;

public class GaussianCopula {
    private double rho_;
    private BivariateCumulativeNormalDistributionWe04DP bivariate_normal_cdf_;
    private InverseCumulativeNormal invCumNormal_;


    public GaussianCopula(double rho_) {
        if (rho_ < -1 || rho_>1) {
            throw new IllegalArgumentException("rho must be in [-1,1].");
        }
        this.bivariate_normal_cdf_ = new BivariateCumulativeNormalDistributionWe04DP(rho_);

        this.invCumNormal_ = new InverseCumulativeNormal();

        this.rho_ = rho_;
    }

    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }
        return bivariate_normal_cdf_.value(invCumNormal_.value(x), invCumNormal_.value(y));
    }

    public static void main(String[] args) {
        GaussianCopula gaussianCopula = new GaussianCopula(0.5);
        System.out.println(gaussianCopula.value(0.1, 0.3)); //0.06534332061695469 quantlib answer = 0.0653433
    }

}
