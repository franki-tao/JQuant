package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.SplineOM1;

public class CubicSplineOvershootingMinimization1 extends CubicInterpolation {
    public CubicSplineOvershootingMinimization1(double[] x, double[] y) {
        super(x, y, SplineOM1, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
