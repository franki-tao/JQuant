package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Cubic;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.MixedInterpolationImpl;


//! mixed linear/cubic interpolation between discrete points
/*! \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class MixedLinearCubicInterpolation extends Interpolation {
    public MixedLinearCubicInterpolation(double[] x, double[] y, int n,
                                         MixedInterpolationImpl.Behavior behavior,
                                         CubicInterpolation.DerivativeApprox da,
                                         boolean monotonic,
                                         CubicInterpolation.BoundaryCondition leftC,
                                         double leftConditionValue,
                                         CubicInterpolation.BoundaryCondition rightC,
                                         double rightConditionValue) {
        impl_ = new MixedInterpolationImpl(x, y, n, behavior, new Linear(),
                new Cubic(da, monotonic, leftC, leftConditionValue, rightC, rightConditionValue));
        impl_.update();
    }
}
