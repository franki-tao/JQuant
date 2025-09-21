package jquant.math.interpolations.impl;

import jquant.math.Interpolation;

public abstract class Interpolator {
    public abstract Interpolation interpolate(double[] x, double[] y);

    public abstract int getRequiredPoints();
}
