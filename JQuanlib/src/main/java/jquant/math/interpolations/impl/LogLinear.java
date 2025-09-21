package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.LogLinearInterpolation;

//! log-linear interpolation factory and traits
/*! \ingroup interpolations */
public class LogLinear extends Interpolator{
    public static final boolean global = false;
    public static final int requiredPoints = 2;
    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new LogLinearInterpolation(x, y);
    }

    @Override
    public int getRequiredPoints() {
        return 2;
    }
}
