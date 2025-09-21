package jquant.math.interpolations;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Harmonic;

public class HarmonicLogCubic extends LogCubicInterpolation {
    public HarmonicLogCubic(double[] x, double[] y) {
        super(x, y, Harmonic, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
