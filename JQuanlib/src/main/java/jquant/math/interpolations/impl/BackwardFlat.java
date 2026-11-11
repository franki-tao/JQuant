package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.BackwardFlatInterpolation;

/**
 * Backward-flat interpolation factory and traits.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/math/interpolations/backwardflatinterpolation.hpp
 */
public class BackwardFlat extends Interpolator {
    public static final boolean global = false;
    public static final int requiredPoints = 1;

    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new BackwardFlatInterpolation(x, y);
    }

    @Override
    public int getRequiredPoints() {
        return requiredPoints;
    }

    @Override
    public boolean global() {
        return false;
    }
}
