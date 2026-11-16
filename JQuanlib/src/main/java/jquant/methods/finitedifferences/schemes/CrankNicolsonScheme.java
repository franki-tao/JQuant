package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Crank-Nicolson scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/cranknicolsonscheme.{hpp,cpp}}.
 * In one dimension Crank-Nicolson is equivalent to the Douglas scheme.
 */
public class CrankNicolsonScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private final double theta_;
    private final ExplicitEulerScheme explicit_;
    private final ImplicitEulerScheme implicit_;

    public CrankNicolsonScheme(double theta,
                               FdmLinearOpComposite map,
                               FdmBoundaryConditionSet bcSet) {
        theta_ = theta;
        explicit_ = new ExplicitEulerScheme(map, bcSet);
        implicit_ = new ImplicitEulerScheme(map, bcSet);
    }

    @Override
    public Array step(Array a, double t) {
        QL_REQUIRE(t - dt_ > -1e-8, "a step towards negative time given");

        if (theta_ != 1.0) {
            a = explicit_.step(a, t, 1.0 - theta_);
        }

        if (theta_ != 0.0) {
            a = implicit_.step(a, t, theta_);
        }
        return a;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
        explicit_.setStep(dt_);
        implicit_.setStep(dt_);
    }
}
