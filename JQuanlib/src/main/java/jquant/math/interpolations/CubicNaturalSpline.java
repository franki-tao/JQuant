package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class CubicNaturalSpline extends CubicInterpolation {
    public CubicNaturalSpline(double[] x, double[] y) {
        super(x, y, Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
