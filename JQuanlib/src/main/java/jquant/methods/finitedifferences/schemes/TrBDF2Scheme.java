package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Trapezoidal BDF2 scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/trbdf2scheme.hpp}.
 * The iterative multi-dimensional solver is not needed by this port.
 */
public class TrBDF2Scheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private double beta_ = Double.NaN;
    private final double alpha_;
    private final FdmLinearOpComposite map_;
    private final FdmScheme trapezoidalScheme_;
    private final FdmBoundaryConditionSet bcSet_;

    public TrBDF2Scheme(double alpha,
                        FdmLinearOpComposite map,
                        FdmScheme trapezoidalScheme,
                        FdmBoundaryConditionSet bcSet) {
        alpha_ = alpha;
        map_ = map;
        trapezoidalScheme_ = trapezoidalScheme;
        bcSet_ = bcSet;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
        beta_ = (1.0 - alpha_) / (2.0 - alpha_) * dt_;
    }

    @Override
    public Array step(Array fn, double t) {
        QL_REQUIRE(t - dt_ > -1e-8, "a step towards negative time given");

        final double intermediateTimeStep = dt_ * alpha_;

        Array fStar = new Array(fn);
        trapezoidalScheme_.setStep(intermediateTimeStep);
        fStar = trapezoidalScheme_.step(fStar, t);

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.setTime(Math.max(0.0, t - dt_));
        }
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyBeforeSolving(map_, fn);
        }

        final Array f = fStar.mutiply(1.0 / alpha_)
                .subtract(fn.mutiply((1.0 - alpha_) * (1.0 - alpha_) / alpha_))
                .div(2.0 - alpha_);

        if (map_.size() != 1) {
            QL_FAIL("multi-dimensional TR-BDF2 is not supported by this port");
        }
        fn = map_.solve_splitting(0, f, -beta_);

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(fn);
        }
        return fn;
    }
}
