package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Implicit-Euler scheme.
 *
 * <p>Port of the one-dimensional path of
 * {@code ql/methods/finitedifferences/schemes/impliciteulerscheme.{hpp,cpp}}.
 * The iterative multi-dimensional solvers are not needed by this port.
 */
public class ImplicitEulerScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;

    public ImplicitEulerScheme(FdmLinearOpComposite map,
                               FdmBoundaryConditionSet bcSet) {
        map_ = map;
        bcSet_ = bcSet;
    }

    @Override
    public Array step(Array a, double t) {
        return step(a, t, 1.0);
    }

    //! Visible to CrankNicolsonScheme, as in the official friend declaration.
    Array step(Array a, double t, double theta) {
        QL_REQUIRE(t - dt_ > -1e-8, "a step towards negative time given");
        map_.setTime(Math.max(0.0, t - dt_), t);
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.setTime(Math.max(0.0, t - dt_));
        }

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyBeforeSolving(map_, a);
        }

        if (map_.size() != 1) {
            QL_FAIL("multi-dimensional implicit Euler is not supported by this port");
        }
        a = map_.solve_splitting(0, a, -theta * dt_);

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(a);
        }
        return a;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
    }
}
