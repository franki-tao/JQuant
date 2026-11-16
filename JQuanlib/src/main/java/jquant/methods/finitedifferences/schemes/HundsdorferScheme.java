package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Hundsdorfer operator-splitting scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/hundsdorferscheme.{hpp,cpp}}.
 */
public class HundsdorferScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private final double theta_;
    private final double mu_;
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;

    public HundsdorferScheme(double theta, double mu,
                             FdmLinearOpComposite map,
                             FdmBoundaryConditionSet bcSet) {
        theta_ = theta;
        mu_ = mu;
        map_ = map;
        bcSet_ = bcSet;
    }

    @Override
    public Array step(Array a, double t) {
        QL_REQUIRE(t - dt_ > -1e-8, "a step towards negative time given");

        map_.setTime(Math.max(0.0, t - dt_), t);
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.setTime(Math.max(0.0, t - dt_));
        }

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyBeforeApplying(map_);
        }
        Array y = a.add(map_.apply(a).mutiply(dt_));
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterApplying(y);
        }

        final Array y0 = new Array(y);

        for (int i = 0; i < map_.size(); ++i) {
            final Array rhs = y.subtract(
                    map_.apply_direction(i, a).mutiply(theta_ * dt_));
            y = map_.solve_splitting(i, rhs, -theta_ * dt_);
        }

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyBeforeApplying(map_);
        }
        Array yt = y0.add(map_.apply(y.subtract(a)).mutiply(mu_ * dt_));
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterApplying(yt);
        }

        for (int i = 0; i < map_.size(); ++i) {
            final Array rhs = yt.subtract(
                    map_.apply_direction(i, y).mutiply(theta_ * dt_));
            yt = map_.solve_splitting(i, rhs, -theta_ * dt_);
        }
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(yt);
        }

        return yt;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
    }
}
