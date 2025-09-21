package jquant.math.interpolations.impl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Kruger;

public class KrugerLog extends LogCubic {
    public KrugerLog() {
        super(Kruger, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
