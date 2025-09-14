package jquant.math.copulas;

import jquant.math.distributions.CumulativeNormalDistribution;

import static java.lang.Math.*;

public class HuslerReissCopula {
    public double theta_;
    public CumulativeNormalDistribution cumNormal_;

    public HuslerReissCopula(double theta) {
        if (theta < 0) {
            throw new IllegalArgumentException("theta must be greater or equal 0.");
        }
        this.theta_ = theta;
        cumNormal_ = new CumulativeNormalDistribution();
    }

    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }
        return pow(x, cumNormal_.value(1.0 / theta_ + 0.5 * theta_ * log(-log(x) / -log(y)))) *
                pow(y, cumNormal_.value(1.0 / theta_ + 0.5 * theta_ * log(-log(y) / -log(x))));

    }
}
