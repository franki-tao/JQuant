package jquant.math.interpolations.impl;


import jquant.math.Interpolation;
import jquant.math.interpolations.ConvexMonotoneInterpolation;

import java.util.TreeMap;

//! Convex-monotone interpolation factory and traits
/*! \ingroup interpolations */
public class ConvexMonotone extends Interpolator {
    private double quadraticity_, monotonicity_;
    private boolean forcePositive_;
    public static final boolean global = true;
    public static final int requiredPoints = 2;
    public static final int dataSizeAdjustment = 1;

    // 默认quadraticity = 0.3， monotonicity = 0.7， forcePositive = true
    public ConvexMonotone() {
        this(0.3, 0.7, true);
    }

    public ConvexMonotone(double quadraticity,
                          double monotonicity,
                          boolean forcePositive) {
        quadraticity_ = (quadraticity);
        monotonicity_ = (monotonicity);
        forcePositive_ = (forcePositive);
    }

    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new ConvexMonotoneInterpolation(x, y,
                quadraticity_,
                monotonicity_,
                forcePositive_,
                false,
                new TreeMap<>());
    }

    @Override
    public int getRequiredPoints() {
        return requiredPoints;
    }

    @Override
    public boolean global() {
        return true;
    }

    public Interpolation localInterpolate(double[] x,
                                          double[] y, int localisation,
                                          ConvexMonotoneInterpolation prevInterpolation,
                                          int finalSize) {
        return localInterpolate(x, y, x.length, localisation, prevInterpolation, finalSize);
    }

    /**
     * QuantLib passes iterator bounds {@code [xBegin, xBegin+length)} into the
     * full node arrays ({@code localbootstrap.hpp}). The explicit {@code length}
     * reproduces that range while keeping the arrays by reference.
     */
    public Interpolation localInterpolate(double[] x,
                                          double[] y, int length, int localisation,
                                          ConvexMonotoneInterpolation prevInterpolation,
                                          int finalSize) {
        if (length - localisation == 1) { // the first time this
            // function is called
            if (length == finalSize) {
                return new ConvexMonotoneInterpolation(x,
                        y, length,
                        quadraticity_,
                        monotonicity_,
                        forcePositive_,
                        false,
                        new TreeMap<>());
            } else {
                return new ConvexMonotoneInterpolation(x,
                        y, length,
                        quadraticity_,
                        monotonicity_,
                        forcePositive_,
                        true,
                        new TreeMap<>());
            }
        }

        ConvexMonotoneInterpolation interp = (prevInterpolation);
        if (length == finalSize) {
            return new ConvexMonotoneInterpolation(
                    x, y, length,
                    quadraticity_,
                    monotonicity_,
                    forcePositive_,
                    false,
                    interp.getExistingHelpers());
        } else {
            return new ConvexMonotoneInterpolation(
                    x, y, length,
                    quadraticity_,
                    monotonicity_,
                    forcePositive_,
                    true,
                    interp.getExistingHelpers());
        }
    }
}
