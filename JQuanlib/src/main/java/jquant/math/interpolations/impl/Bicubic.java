package jquant.math.interpolations.impl;

import jquant.math.interpolations.BicubicSpline;
import jquant.math.interpolations.Interpolation2D;

//! bicubic-spline-interpolation factory
public class Bicubic {
    public Interpolation2D interpolate(double[] x, double[] y, double[][] z) {
        return new BicubicSpline(x, y, z);
    }
}
