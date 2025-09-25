package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.CubicInterpolation;
import jquant.math.interpolations.LogMixedLinearCubicInterpolation;

public class LogMixedLinearCubic extends Interpolator {
    public static final boolean global = true;
    public static final int requiredPoints = 3;

    private int n_;
    private MixedInterpolationImpl.Behavior behavior_;
    private CubicInterpolation.DerivativeApprox da_;
    private boolean monotonic_;
    private CubicInterpolation.BoundaryCondition leftType_, rightType_;
    private double leftValue_, rightValue_;

    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new LogMixedLinearCubicInterpolation(x, y,
                n_, behavior_,
                da_, monotonic_,
                leftType_, leftValue_,
                rightType_, rightValue_);
    }

    //monotonic = true
    //leftCondition = CubicInterpolation::SecondDerivative
    //leftConditionValue = 0.0
    //rightCondition = CubicInterpolation::SecondDerivative
    //rightConditionValue = 0.0
    public LogMixedLinearCubic(int n,
                               MixedInterpolationImpl.Behavior behavior,
                               CubicInterpolation.DerivativeApprox da,
                               boolean monotonic,
                               CubicInterpolation.BoundaryCondition leftCondition,
                               double leftConditionValue,
                               CubicInterpolation.BoundaryCondition rightCondition,
                               double rightConditionValue) {
        n_ = (n);
        behavior_ = (behavior);
        da_ = (da);
        monotonic_ = (monotonic);
        leftType_ = (leftCondition);
        rightType_ = (rightCondition);
        leftValue_ = (leftConditionValue);
        rightValue_ = (rightConditionValue);
    }

    @Override
    public int getRequiredPoints() {
        return requiredPoints;
    }
}
