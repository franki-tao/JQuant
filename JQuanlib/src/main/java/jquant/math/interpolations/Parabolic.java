package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;

public class Parabolic extends CubicInterpolation {
    public Parabolic(double[] x, double[] y) {
        super(x, y, CubicInterpolation.DerivativeApprox.Parabolic, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
