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
 * Trapezoidal BDF2 scheme.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/trbdf2scheme.hpp}.
 */
public class TrBDF2Scheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private double beta_ = Double.NaN;
    private int iterations_ = 0;
    private final double alpha_;
    private final FdmLinearOpComposite map_;
    private final FdmScheme trapezoidalScheme_;
    private final FdmBoundaryConditionSet bcSet_;
    private final double relTol_;

    public TrBDF2Scheme(double alpha,
                        FdmLinearOpComposite map,
                        FdmScheme trapezoidalScheme,
                        FdmBoundaryConditionSet bcSet) {
        this(alpha, map, trapezoidalScheme, bcSet, 1e-8);
    }

    public TrBDF2Scheme(double alpha,
                        FdmLinearOpComposite map,
                        FdmScheme trapezoidalScheme,
                        FdmBoundaryConditionSet bcSet,
                        double relTol) {
        alpha_ = alpha;
        map_ = map;
        trapezoidalScheme_ = trapezoidalScheme;
        bcSet_ = bcSet;
        relTol_ = relTol;
    }

    public int numberOfIterations() {
        return iterations_;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
        beta_ = (1.0 - alpha_) / (2.0 - alpha_) * dt_;
    }

    protected Array apply(Array r) {
        return r.subtract(map_.apply(r).mutiply(beta_));
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

        if (map_.size() == 1) {
            fn = map_.solve_splitting(0, f, -beta_);
        } else {
            final FunctionN applyF = r -> apply(r);
            final FunctionN preconditioner = r -> map_.preconditioner(r, -beta_);

            final BiCGstab biCGstab = new BiCGstab(applyF,
                    Math.max(10, fn.size()), relTol_, preconditioner);
            final BiCGStabResult result = biCGstab.solve(f, f);
            iterations_ += result.iterations;
            fn = result.x;
        }

        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(fn);
        }
        return fn;
    }
}
