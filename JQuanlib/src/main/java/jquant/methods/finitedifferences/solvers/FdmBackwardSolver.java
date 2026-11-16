package jquant.methods.finitedifferences.solvers;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.schemes.DouglasScheme;
import jquant.methods.finitedifferences.schemes.FdmScheme;
import jquant.methods.finitedifferences.schemes.ImplicitEulerScheme;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Backward solver for finite-difference schemes.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdmbackwardsolver.{hpp,cpp}}.
 * Douglas is the supported scheme for this port; damping, when requested,
 * uses implicit Euler as in QuantLib.
 */
public class FdmBackwardSolver {
    private final FdmLinearOpComposite map_;
    private final FdmBoundaryConditionSet bcSet_;
    private final FdmStepConditionComposite condition_;
    private final FdmSchemeDesc schemeDesc_;

    public FdmBackwardSolver(FdmLinearOpComposite map,
                             FdmBoundaryConditionSet bcSet,
                             FdmStepConditionComposite condition,
                             FdmSchemeDesc schemeDesc) {
        map_ = map;
        bcSet_ = bcSet;
        condition_ = (condition != null)
                ? condition
                : new FdmStepConditionComposite(Collections.emptyList(),
                        Collections.emptyList());
        schemeDesc_ = schemeDesc;
    }

    public void rollback(Array a, double from, double to,
                         int steps, int dampingSteps) {
        final double deltaT = from - to;
        final int allSteps = steps + dampingSteps;
        final double dampingTo = from - (deltaT * dampingSteps) / allSteps;

        Array x = a;

        if (dampingSteps != 0
                && schemeDesc_.type != FdmSchemeDesc.FdmSchemeType.ImplicitEulerType) {
            final ImplicitEulerScheme implicitEvolver =
                    new ImplicitEulerScheme(map_, bcSet_);
            x = rollbackImpl(implicitEvolver, x, from, dampingTo, dampingSteps);
        }

        switch (schemeDesc_.type) {
            case DouglasType:
                final DouglasScheme dsEvolver =
                        new DouglasScheme(schemeDesc_.theta, map_, bcSet_);
                x = rollbackImpl(dsEvolver, x, dampingTo, to, steps);
                break;
            case ImplicitEulerType:
                final ImplicitEulerScheme implicitEvolver =
                        new ImplicitEulerScheme(map_, bcSet_);
                x = rollbackImpl(implicitEvolver, x, from, to, allSteps);
                break;
            default:
                QL_FAIL("scheme type " + schemeDesc_.type
                        + " is not supported by this port");
        }

        a.swap(x);
    }

    private Array rollbackImpl(FdmScheme evolver, Array a,
                               double from, double to, int steps) {
        QL_REQUIRE(from >= to, "trying to roll back from " + from + " to " + to);

        Array x = new Array(a);
        final double dt = (from - to) / steps;
        double t = from;
        evolver.setStep(dt);

        final List<Double> stoppingTimes = condition_.stoppingTimes();

        if (!stoppingTimes.isEmpty()
                && stoppingTimes.get(stoppingTimes.size() - 1) == from) {
            condition_.applyTo(x, from);
        }

        for (int i = 0; i < steps; ++i, t -= dt) {
            double now = t;
            // make sure the last step ends exactly on "to" so that a
            // stopping time at "to" is not missed due to numerical issues
            double next = (i < steps - 1) ? t - dt : to;

            if (Math.abs(to - next) < Math.sqrt(QL_EPSILON)) {
                next = to;
            }

            boolean hit = false;
            for (int j = stoppingTimes.size() - 1; j >= 0; --j) {
                final double stoppingTime = stoppingTimes.get(j);
                if (next <= stoppingTime && stoppingTime < now) {
                    // a stopping time was hit
                    hit = true;

                    // perform a small step to the stopping time...
                    evolver.setStep(now - stoppingTime);
                    x = evolver.step(x, now);
                    condition_.applyTo(x, stoppingTime);
                    // ...and continue the cycle
                    now = stoppingTime;
                }
            }

            if (hit) {
                // ...we might have to make a small step to complete the big one...
                if (now > next) {
                    evolver.setStep(now - next);
                    x = evolver.step(x, now);
                    condition_.applyTo(x, next);
                }
                // ...and in any case, reset the evolver to the default step.
                evolver.setStep(dt);
            } else {
                // the evolver is already set to the default step
                x = evolver.step(x, now);
                condition_.applyTo(x, next);
            }
        }

        return x;
    }
}
