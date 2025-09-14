package jquant.math.copulas;

import static java.lang.Math.min;
import static java.lang.Math.pow;

public class MarshallOlkinCopula {
    public double a1_;
    public double a2_;

    public MarshallOlkinCopula(double a1, double a2) {
        if (a1 < 0 || a2 < 0) {
            throw new IllegalArgumentException("a1, a2 must be non-negative.");
        }
        this.a1_ = a1;
        this.a2_ = a2;
    }

    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }
        return min(y * pow(x, a1_), x * pow(y, a2_));
    }
}
