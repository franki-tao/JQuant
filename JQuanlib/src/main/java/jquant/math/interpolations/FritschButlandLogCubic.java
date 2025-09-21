package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.FritschButland;

public class FritschButlandLogCubic extends LogCubicInterpolation {
    public FritschButlandLogCubic(double[] x, double[] y) {
        super(x, y, FritschButland, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
