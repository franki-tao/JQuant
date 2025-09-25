package jquant.math.interpolations.impl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class MonotonicLogMixedLinearCubic extends LogMixedLinearCubic {
    //behavior = MixedInterpolation::ShareRanges
    public MonotonicLogMixedLinearCubic(int n, MixedInterpolationImpl.Behavior behavior) {
        super(n, behavior,
                Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
