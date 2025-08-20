package math.copulas;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class PlackettCopula {
    public double theta_;

    public PlackettCopula(double theta) {
        if (theta < 0 || theta == 1) {
            throw new IllegalArgumentException("theta must be >= 0 and != 1.");
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
        return ((1.0 + (theta_ - 1.0) * (x + y)) - sqrt(pow(1.0 + (theta_ - 1.0) * (x + y), 2.0) - 4.0
                * x * y * theta_ * (theta_ - 1.0))) / (2.0 * (theta_ - 1.0));
    }
}
