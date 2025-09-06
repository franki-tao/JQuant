package math.interpolations.impl;

import math.Interpolation;
import math.interpolations.BackwardFlatInterpolation;

public class BackwardFlat {
    public static final boolean global = false;
    public static final int requiredPoints = 1;
    Interpolation interpolate(double[] x, double[] y) {
        return new BackwardFlatInterpolation(x, y);
    }
}
