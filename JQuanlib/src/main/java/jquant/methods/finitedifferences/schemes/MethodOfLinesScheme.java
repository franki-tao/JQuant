package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryCondition;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Method-of-Lines scheme, integrating the semi-discrete PDE with an adaptive
 * Runge-Kutta solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/schemes/methodoflinesscheme.{hpp,cpp}}.
 */
public class MethodOfLinesScheme implements FdmScheme {
    private double dt_ = Double.NaN;
    private final double eps_;
    private final double relInitStepSize_;
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;

    public MethodOfLinesScheme(double eps, double relInitStepSize,
                               FdmLinearOpComposite map,
                               FdmBoundaryConditionSet bcSet) {
        eps_ = eps;
        relInitStepSize_ = relInitStepSize;
        map_ = map;
        bcSet_ = bcSet;
    }

    private List<Double> apply(double t, List<Double> u) {
        map_.setTime(t, t + 0.0001);
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyBeforeApplying(map_);
        }

        final Array dxdt = map_.apply(new Array(toArray(u))).mutiply(-1.0);
        return dxdt.getList();
    }

    @Override
    public Array step(Array a, double t) {
        QL_REQUIRE(t - dt_ > -1e-8, "a step towards negative time given");

        final OdeFct ode = this::apply;
        final List<Double> v = new AdaptiveRungeKutta(
                eps_, relInitStepSize_ * dt_, 0.0).value(
                ode, a.getList(), t, Math.max(0.0, t - dt_));

        final Array y = new Array(toArray(v));
        for (FdmBoundaryCondition bc : bcSet_) {
            bc.applyAfterSolving(y);
        }
        return y;
    }

    @Override
    public void setStep(double dt) {
        dt_ = dt;
    }

    private static double[] toArray(List<Double> values) {
        final double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }
}
