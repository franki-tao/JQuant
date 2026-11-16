package jquant.math.interpolations.impl;

import jquant.math.interpolations.BicubicSpline;
import jquant.math.interpolations.Interpolation2D;
import jquant.math.interpolations.Interpolator2D;

//! bicubic-spline-interpolation factory
public class Bicubic implements Interpolator2D {
    @Override
    public Interpolation2D interpolate(double[] x, double[] y, double[][] z) {
        return new BicubicSpline(x, y, z);
    }
}
