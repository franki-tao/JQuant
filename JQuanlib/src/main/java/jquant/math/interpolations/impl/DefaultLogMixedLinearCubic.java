package jquant.math.interpolations.impl;

import jquant.math.interpolations.CubicInterpolation;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class DefaultLogMixedLinearCubic extends LogMixedLinearCubic {
    //behavior = MixedInterpolation::ShareRanges
    public DefaultLogMixedLinearCubic(int n, MixedInterpolationImpl.Behavior behavior) {
        super(n, behavior, CubicInterpolation.DerivativeApprox.Kruger,
                true, SecondDerivative, 0, SecondDerivative, 0);
    }
}
