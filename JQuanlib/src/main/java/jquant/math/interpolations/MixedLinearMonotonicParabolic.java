package jquant.math.interpolations;

import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class MixedLinearMonotonicParabolic extends MixedLinearCubicInterpolation {
    //behavior = ShareRanges
    public MixedLinearMonotonicParabolic(double[] x, double[] y, int n,
                                         MixedInterpolationImpl.Behavior behavior) {
        super(x, y, n, behavior,
                CubicInterpolation.DerivativeApprox.Parabolic, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
