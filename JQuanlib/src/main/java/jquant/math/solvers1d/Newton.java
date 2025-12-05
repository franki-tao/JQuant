package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! %Newton 1-D solver
 * ! \note This solver requires that the passed function object
 * implement a method <tt>Real derivative(Real)</tt>.
 * <p>
 * \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class Newton extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
               Press, Teukolsky, Vetterling, and Flannery,
               "Numerical Recipes in C", 2nd edition,
               Cambridge University Press
            */

        double froot, dfroot, dx;

        froot = f.value(root_);
        dfroot = f.derivative(root_);
        QL_REQUIRE(!Double.isNaN(dfroot),
                "Newton requires function's derivative");
        ++evaluationNumber_;

        while (evaluationNumber_ <= maxEvaluations_) {
            dx = froot / dfroot;
            root_ -= dx;
            // jumped out of brackets, switch to NewtonSafe
            if ((xMin_ - root_) * (root_ - xMax_) < 0.0) {
                NewtonSafe s = new NewtonSafe();
                s.setMaxEvaluations(maxEvaluations_ - evaluationNumber_);
                return s.solve(f, xAccuracy, root_ + dx, xMin_, xMax_);
            }
            if (Math.abs(dx) < xAccuracy) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }
            froot = f.value(root_);
            dfroot = f.derivative(root_);
            ++evaluationNumber_;
        }

        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }
}
