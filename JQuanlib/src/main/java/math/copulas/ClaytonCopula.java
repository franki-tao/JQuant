package math.copulas;

import static java.lang.Math.max;
import static java.lang.Math.pow;

public class ClaytonCopula {

    private double theta;

    public ClaytonCopula(double theta) {
        if (theta < -1 || theta == 0) {
            throw new IllegalArgumentException("theta must be greater or equal-1 and not equal 0.");
        }
        this.theta = theta;
    }

    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }

        return max(pow(pow(x, -theta) + pow(y, -theta) - 1.0, -1.0 / theta), 0.0);
    }
}
