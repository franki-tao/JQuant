package jquant.math.interpolations.impl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Kruger;

public class KrugerLogMixedLinearCubic extends LogMixedLinearCubic {
    // behavior = MixedInterpolation::ShareRanges
    public KrugerLogMixedLinearCubic(int n, MixedInterpolationImpl.Behavior behavior) {
        super(n, behavior,
                Kruger, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
