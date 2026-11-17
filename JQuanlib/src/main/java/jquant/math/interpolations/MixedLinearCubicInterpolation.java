package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Cubic;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;


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
        boolean matchDerivatives = leftC == CubicInterpolation.BoundaryCondition.FirstDerivative &&
                leftConditionValue == NULL_REAL;
        QL_REQUIRE(!matchDerivatives || behavior == MixedInterpolationImpl.Behavior.SplitRanges,
                "matching derivatives is only supported with SplitRanges");

        impl_ = new MixedInterpolationImpl(x, y, n, behavior, new Linear(),
                new Cubic(da, monotonic, leftC, leftConditionValue, rightC, rightConditionValue),
                matchDerivatives);
        impl_.update();
    }
}
