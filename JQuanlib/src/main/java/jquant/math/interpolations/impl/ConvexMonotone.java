package jquant.math.interpolations.impl;


import jquant.math.Interpolation;
import jquant.math.interpolations.ConvexMonotoneInterpolation;

import java.util.TreeMap;

//! Convex-monotone interpolation factory and traits
/*! \ingroup interpolations */
public class ConvexMonotone {
    private double quadraticity_, monotonicity_;
    private boolean forcePositive_;
    public static final boolean global = true;
    public static final int requiredPoints = 2;
    public static final int dataSizeAdjustment = 1;

    // 默认quadraticity = 0.3， monotonicity = 0.7， forcePositive = true
    public ConvexMonotone(double quadraticity,
                          double monotonicity,
                          boolean forcePositive) {
        quadraticity_ = (quadraticity);
        monotonicity_ = (monotonicity);
        forcePositive_ = (forcePositive);
    }

    public Interpolation interpolate(double[] x, double[] y) {
        return new ConvexMonotoneInterpolation(x, y,
                quadraticity_,
                monotonicity_,
                forcePositive_,
                false,
                new TreeMap<>());
    }

    public Interpolation localInterpolate(double[] x,
                                          double[] y, int localisation,
                                          ConvexMonotoneInterpolation prevInterpolation,
                                          int finalSize) {
        int length = x.length;
        if (length - localisation == 1) { // the first time this
            // function is called
            if (length == finalSize) {
                return new ConvexMonotoneInterpolation(x,
                        y,
                        quadraticity_,
                        monotonicity_,
                        forcePositive_,
                        false,
                        new TreeMap<>());
            } else {
                return new ConvexMonotoneInterpolation(x,
                        y,
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
                    x, y,
                    quadraticity_,
                    monotonicity_,
                    forcePositive_,
                    false,
                    interp.getExistingHelpers());
        } else {
            return new ConvexMonotoneInterpolation(
                    x, y,
                    quadraticity_,
                    monotonicity_,
                    forcePositive_,
                    true,
                    interp.getExistingHelpers());
        }
    }
}
