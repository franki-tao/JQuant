package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.FritschButland;

public class FritschButlandCubic extends CubicInterpolation {
    public FritschButlandCubic(double[] x, double[] y) {
        super(x, y, FritschButland, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
