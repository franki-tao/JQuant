package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Cubic;
import jquant.math.interpolations.impl.LogInterpolationImpl;

//! %log-cubic interpolation between discrete points
/*! \ingroup interpolations */
public class LogCubicInterpolation extends Interpolation {
    public LogCubicInterpolation(double[] x, double[] y,
                                 CubicInterpolation.DerivativeApprox da,
                                 boolean monotonic,
                                 CubicInterpolation.BoundaryCondition leftC,
                                 double leftConditionValue,
                                 CubicInterpolation.BoundaryCondition rightC,
                                 double rightConditionValue) {
        impl_ = new LogInterpolationImpl(x, y, new Cubic(da, monotonic,
                leftC, leftConditionValue,
                rightC, rightConditionValue));
        impl_.update();
    }
}
