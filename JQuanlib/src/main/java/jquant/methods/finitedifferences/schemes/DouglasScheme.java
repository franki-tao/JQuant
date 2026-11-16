package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Douglas operator-splitting scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/douglasscheme.{hpp,cpp}}.
 */
public class DouglasScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private final double theta_;
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;

    public DouglasScheme(double theta,
                         FdmLinearOpComposite map,
                         FdmBoundaryConditionSet bcSet) {
        theta_ = theta;
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

        for (int i = 0; i < map_.size(); ++i) {
            final Array rhs = y.subtract(map_.apply_direction(i, a).mutiply(theta_ * dt_));
            y = map_.solve_splitting(i, rhs, -theta_ * dt_);
        }
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(y);
        }

        return y;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
    }
}
