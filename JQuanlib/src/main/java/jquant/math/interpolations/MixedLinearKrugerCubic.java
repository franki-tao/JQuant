package jquant.math.interpolations;

import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Kruger;

public class MixedLinearKrugerCubic extends MixedLinearCubicInterpolation {
    //behavior = ShareRanges
    public MixedLinearKrugerCubic(double[] x, double[] y, int n,
                                  MixedInterpolationImpl.Behavior behavior) {
        super(x, y, n, behavior,
                Kruger, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
