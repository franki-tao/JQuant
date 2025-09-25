package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.LogInterpolationImpl;
import jquant.math.interpolations.impl.MixedInterpolationImpl;
import jquant.math.interpolations.impl.MixedLinearCubic;

//! %log-mixedlinearcubic interpolation between discrete points
/*! \ingroup interpolations */
public class LogMixedLinearCubicInterpolation extends Interpolation {
    public LogMixedLinearCubicInterpolation(double[] x, double[] y, int n,
                                            MixedInterpolationImpl.Behavior behavior,
                                            CubicInterpolation.DerivativeApprox da,
                                            boolean monotonic,
                                            CubicInterpolation.BoundaryCondition leftC,
                                            double leftConditionValue,
                                            CubicInterpolation.BoundaryCondition rightC,
                                            double rightConditionValue) {
        impl_ = new LogInterpolationImpl(x, y, new MixedLinearCubic(n, behavior, da, monotonic,
                leftC, leftConditionValue,
                rightC, rightConditionValue));
        impl_.update();
    }
}
