package jquant.math.interpolations.impl;

import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Kruger;

public class DefaultLogCubic extends LogCubic{
    public DefaultLogCubic() {
        super(Kruger,true, SecondDerivative, 0, SecondDerivative, 0);
    }
}
