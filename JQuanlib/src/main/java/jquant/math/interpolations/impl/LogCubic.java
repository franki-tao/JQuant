package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.CubicInterpolation;
import jquant.math.interpolations.LogCubicInterpolation;

public class LogCubic extends Interpolator {
    public static final boolean global = true;
    public static final int requiredPoints = 2;
    private CubicInterpolation.DerivativeApprox da_;
    private boolean monotonic_;
    private CubicInterpolation.BoundaryCondition leftType_, rightType_;
    private double leftValue_, rightValue_;

    /*
        monotonic = true
        leftCondition= CubicInterpolation::SecondDerivative
        leftConditionValue = 0.0
        rightCondition= CubicInterpolation::SecondDerivative
        rightConditionValue = 0.0
     */
    public LogCubic(CubicInterpolation.DerivativeApprox da,
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


    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new LogCubicInterpolation(x, y,
                da_, monotonic_,
                leftType_, leftValue_,
                rightType_, rightValue_);
    }

    @Override
    public int getRequiredPoints() {
        return requiredPoints;
    }
}
