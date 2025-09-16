package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.CubicInterpolation;

public class Cubic {
    public static final boolean global = true;
    public static final int requiredPoints = 2;
    private CubicInterpolation.DerivativeApprox da_;
    private boolean monotonic_;
    private CubicInterpolation.BoundaryCondition leftType_, rightType_;
    private double leftValue_, rightValue_;

    /*
        默认值
        da = CubicInterpolation::Kruger
        monotonic = false
        leftCondition= CubicInterpolation::SecondDerivative
        leftConditionValue = 0.0
        rightCondition = CubicInterpolation::SecondDerivative
        rightConditionValue = 0.0
     */
    public Cubic(CubicInterpolation.DerivativeApprox da,
                 boolean monotonic,
                 CubicInterpolation.BoundaryCondition leftCondition,
                 double leftConditionValue,
                 CubicInterpolation.BoundaryCondition rightCondition,
                 double rightConditionValue) {
        da_ = (da);
        monotonic_ = (monotonic);
        leftType_ = (leftCondition);
        rightType_ = (rightCondition);
        leftValue_ = (leftConditionValue);
        rightValue_ = (rightConditionValue);
    }

    public Interpolation interpolate(double[] x, double[] y) {
        return new CubicInterpolation(x, y,
                da_, monotonic_,
                leftType_, leftValue_,
                rightType_, rightValue_);

    }
}
