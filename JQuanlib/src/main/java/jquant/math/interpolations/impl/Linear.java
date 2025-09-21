package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.LinearInterpolation;

public class Linear extends Interpolator{
    public static final int requiredPoints = 2;
    public static final boolean global = false;

    public Interpolation interpolate(double[] x, double[] y) {
        return new LinearInterpolation(x, y);
    }

    @Override
    public int getRequiredPoints() {
        return requiredPoints;
    }
}
