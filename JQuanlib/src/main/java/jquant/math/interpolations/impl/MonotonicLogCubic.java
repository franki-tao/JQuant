package jquant.math.interpolations.impl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

public class MonotonicLogCubic extends LogCubic{
    public MonotonicLogCubic() {
        super(Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
