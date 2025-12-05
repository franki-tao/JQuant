package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * ! %Halley 1-D solver
 * ! \note This solver requires that the passed function object
 * implement a method <tt>Real derivative(Real)</tt>
 * and <tt> Real secondDerivative(Real></tt>
 * <p>
 * \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class Halley extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        while (++evaluationNumber_ <= maxEvaluations_) {
            final double fx = f.value(root_);
            final double fPrime = f.derivative(root_);
            final double lf = fx * f.secondDerivative(root_) / (fPrime * fPrime);
            final double step = 1.0 / (1.0 - 0.5 * lf) * fx / fPrime;
            root_ -= step;

            // jumped out of brackets, switch to NewtonSafe
            if ((xMin_ - root_) * (root_ - xMax_) < 0.0) {
                NewtonSafe s = new NewtonSafe();
                s.setMaxEvaluations(maxEvaluations_ - evaluationNumber_);
                return s.solve(f, xAccuracy, root_ + step, xMin_, xMax_);
            }

            if (Math.abs(step) < xAccuracy) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }

        }

        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }
}
