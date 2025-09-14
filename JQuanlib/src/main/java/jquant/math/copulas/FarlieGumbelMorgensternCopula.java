package jquant.math.copulas;

public class FarlieGumbelMorgensternCopula {
    public double theta;

    public FarlieGumbelMorgensternCopula(double theta) {
        if (theta < -1 || theta > 1) {
            throw new IllegalArgumentException("theta must be in [-1,1].");
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
        return x * y + theta * x * y * (1.0 - x) * (1.0 - y);
    }
}
