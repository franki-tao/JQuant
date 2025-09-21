package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class LogParabolic extends LogCubicInterpolation {
    public LogParabolic(double[] x, double[] y) {
        super(x, y, CubicInterpolation.DerivativeApprox.Parabolic, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
