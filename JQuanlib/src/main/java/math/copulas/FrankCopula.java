package math.copulas;

import static java.lang.Math.exp;
import static java.lang.Math.log;

public class FrankCopula {
    public double theta_;

    public FrankCopula(double theta) {
        if (theta == 0) {
            throw new IllegalArgumentException("theta must be different from 0.");
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
        return -1.0 / theta_ * log(1 + (exp(-theta_ * x) - 1) * (exp(-theta_ * y) - 1) / (exp(-theta_) - 1));
    }
}
