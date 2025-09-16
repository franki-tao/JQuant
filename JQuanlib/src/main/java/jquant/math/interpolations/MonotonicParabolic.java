package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class MonotonicParabolic extends CubicInterpolation {
    public MonotonicParabolic(double[] x, double[] y) {
        super(x, y, CubicInterpolation.DerivativeApprox.Parabolic, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
