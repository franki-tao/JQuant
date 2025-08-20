package math.copulas;

public class MinCopula {
    public MinCopula() {
    }
    public double value(double x, double y) {
        if (x < 0 || x > 1) {
            throw new IllegalArgumentException("1st argument x must be in [0,1]");
        }
        if (y < 0 || y > 1) {
            throw new IllegalArgumentException("2nd argument y must be in [0,1]");
        }
        return Math.max(x+y-1.0, 0.0);
    }
}
