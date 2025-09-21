package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class LogCubicNaturalSpline extends LogCubicInterpolation {
    /*! \pre the \f$ x \f$ values must be sorted. */
    public LogCubicNaturalSpline(double[] x, double[] y) {
        super(x, y, Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
