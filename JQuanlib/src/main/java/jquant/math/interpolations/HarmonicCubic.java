package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Harmonic;

public class HarmonicCubic extends CubicInterpolation {
    public HarmonicCubic(double[] x, double[] y) {
        super(x, y, Harmonic, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
