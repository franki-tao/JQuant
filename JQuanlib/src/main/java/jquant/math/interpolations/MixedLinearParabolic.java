package jquant.math.interpolations;

import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class MixedLinearParabolic extends MixedLinearCubicInterpolation {
    //behavior = ShareRanges
    public MixedLinearParabolic(double[] x, double[] y, int n,
                                MixedInterpolationImpl.Behavior behavior) {
        super(x, y, n, behavior,
                CubicInterpolation.DerivativeApprox.Parabolic, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
