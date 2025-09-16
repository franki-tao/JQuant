package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Kruger;

public class KrugerCubic extends CubicInterpolation {
    public KrugerCubic(double[] x, double[] y) {
        super(x, y, Kruger, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
