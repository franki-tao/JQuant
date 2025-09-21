package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class MonotonicLogCubicNaturalSpline extends LogCubicInterpolation {
    /*! \pre the \f$ x \f$ values must be sorted. */
    public MonotonicLogCubicNaturalSpline(double[] x, double[] y) {
        super(x, y, Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
