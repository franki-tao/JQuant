package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.SplineOM2;

public class CubicSplineOvershootingMinimization2 extends CubicInterpolation {
    public CubicSplineOvershootingMinimization2(double[] x, double[] y) {
        super(x, y, SplineOM2, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
