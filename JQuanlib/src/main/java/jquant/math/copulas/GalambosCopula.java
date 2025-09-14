package jquant.math.copulas;

import static java.lang.Math.*;

public class GalambosCopula {
    public double theta_;

    public GalambosCopula(double theta) {
        if (theta < 0) {
            throw new IllegalArgumentException("theta must be greater or equal 0.");
        }
        this.theta_ = theta;
    }

    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }
        return x * y * exp(pow(pow(-log(x), -theta_) + pow(-log(y), -theta_), -1 / theta_));
    }
}
