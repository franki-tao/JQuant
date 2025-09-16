package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class MonotonicCubicNaturalSpline extends CubicInterpolation {
    public MonotonicCubicNaturalSpline(double[] x, double[] y) {
        super(x, y, Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
