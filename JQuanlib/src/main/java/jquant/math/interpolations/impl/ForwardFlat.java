package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.ForwardFlatInterpolation;

//! Forward-flat interpolation factory and traits
/*! \ingroup interpolations */
public class ForwardFlat {
    public static final boolean global = false;
    public static final int requiredPoints = 2;

    public Interpolation interpolate(double[] x, double[] y) {
        return new ForwardFlatInterpolation(x, y);
    }
}
