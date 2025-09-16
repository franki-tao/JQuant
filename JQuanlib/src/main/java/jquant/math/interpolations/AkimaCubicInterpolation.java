package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Akima;

public class AkimaCubicInterpolation extends CubicInterpolation {
    public AkimaCubicInterpolation(double[] x, double[] y) {
        super(x, y, Akima, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
