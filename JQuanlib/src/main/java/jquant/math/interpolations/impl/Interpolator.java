package jquant.math.interpolations.impl;

import jquant.math.Interpolation;

public abstract class Interpolator {
    public abstract Interpolation interpolate(double[] x, double[] y);

    public abstract int getRequiredPoints();

    /** True when every node depends on the whole data set, as a cubic spline does. */
    public boolean global() {
        return false;
    }
}
