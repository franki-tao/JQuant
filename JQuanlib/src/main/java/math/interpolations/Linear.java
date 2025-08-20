package math.interpolations;

import math.Interpolation;

public class Linear {
    public static final int requiredPoints = 2;
    public static final boolean global = false;

    public Interpolation interpolate(double[] x, double[] y) {
        return new LinearInterpolation(x, y);
    }
}
