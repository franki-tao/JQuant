package jquant.math.interpolations.impl;

import jquant.math.interpolations.BilinearInterpolation;
import jquant.math.interpolations.Interpolation2D;

//! bilinear-interpolation factory
public class Bilinear {
    public Interpolation2D interpolate(double[] x, double[] y, double[][] z) {
        return new BilinearInterpolation(x, y, z);
    }
}
