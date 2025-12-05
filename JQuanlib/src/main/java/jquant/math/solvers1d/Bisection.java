package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.close;

/**
 * ! %Bisection 1-D solver
 * ! \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class Bisection extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
               Press, Teukolsky, Vetterling, and Flannery,
               "Numerical Recipes in C", 2nd edition, Cambridge
               University Press
            */

        double dx, xMid, fMid;

        // Orient the search so that f>0 lies at root_+dx
        if (fxMin_ < 0.0) {
            dx = xMax_ - xMin_;
            root_ = xMin_;
        } else {
            dx = xMin_ - xMax_;
            root_ = xMax_;
        }

        while (evaluationNumber_ <= maxEvaluations_) {
            dx /= 2.0;
            xMid = root_ + dx;
            fMid = f.value(xMid);
            ++evaluationNumber_;
            if (fMid <= 0.0)
                root_ = xMid;
            if (Math.abs(dx) < xAccuracy || (close(fMid, 0.0))) {
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
