package jquant.math.interpolations;

import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.FritschButland;

public class MixedLinearFritschButlandCubic extends MixedLinearCubicInterpolation {
    //behavior = ShareRanges
    public MixedLinearFritschButlandCubic(double[] x, double[] y, int n,
                                  MixedInterpolationImpl.Behavior behavior) {
        super(x, y, n, behavior,
                FritschButland, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
