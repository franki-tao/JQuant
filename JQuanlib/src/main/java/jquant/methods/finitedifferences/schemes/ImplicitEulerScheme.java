package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.math.FunctionN;
import jquant.math.matrixutilities.BiCGstab;
import jquant.math.matrixutilities.impl.BiCGStabResult;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Implicit-Euler scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/impliciteulerscheme.{hpp,cpp}}.
 */
public class ImplicitEulerScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private int iterations_ = 0;
    private final double relTol_;
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;

    public ImplicitEulerScheme(FdmLinearOpComposite map,
                               FdmBoundaryConditionSet bcSet) {
        this(map, bcSet, 1e-8);
    }

    public ImplicitEulerScheme(FdmLinearOpComposite map,
                               FdmBoundaryConditionSet bcSet,
                               double relTol) {
        relTol_ = relTol;
        map_ = map;
        bcSet_ = bcSet;
    }

    protected Array apply(Array r, double theta) {
        return r.subtract(map_.apply(r).mutiply(theta * dt_));
    }

    public int numberOfIterations() {
        return iterations_;
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

        if (map_.size() == 1) {
            a = map_.solve_splitting(0, a, -theta * dt_);
        } else {
            final FunctionN applyF = r -> apply(r, theta);
            final FunctionN preconditioner =
                    r -> map_.preconditioner(r, -theta * dt_);

            final BiCGstab biCGstab = new BiCGstab(applyF,
                    Math.max(10, a.size()), relTol_, preconditioner);
            final BiCGStabResult result = biCGstab.solve(a, a);
            iterations_ += result.iterations;
            a = result.x;
        }

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
