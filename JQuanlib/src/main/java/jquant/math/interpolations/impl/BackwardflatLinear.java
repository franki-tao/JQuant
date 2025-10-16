package jquant.math.interpolations.impl;

import jquant.math.interpolations.BackwardflatLinearInterpolation;
import jquant.math.interpolations.Interpolation2D;

public class BackwardflatLinear {
    public Interpolation2D interpolate(double[] x, double[] y, double[][] z) {
        return new BackwardflatLinearInterpolation(x, y, z);
    }
}
