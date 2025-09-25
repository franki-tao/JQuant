package jquant.math.interpolations;

import jquant.math.interpolations.impl.MixedInterpolationImpl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class LogMixedLinearCubicNaturalSpline extends LogMixedLinearCubicInterpolation {
    // behavior = MixedInterpolation::ShareRanges
    public LogMixedLinearCubicNaturalSpline(double[] x, double[] y, int n, MixedInterpolationImpl.Behavior behavior) {
        super(x, y, n, behavior,
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
