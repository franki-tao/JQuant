package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.close;

/**
 * ! False position 1-D solver
 * ! \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class FalsePosition extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
           Press, Teukolsky, Vetterling, and Flannery,
           "Numerical Recipes in C", 2nd edition,
           Cambridge University Press
        */

        double fl, fh, xl, xh;
        // Identify the limits so that xl corresponds to the low side
        if (fxMin_ < 0.0) {
            xl = xMin_;
            fl = fxMin_;
            xh = xMax_;
            fh = fxMax_;
        } else {
            xl = xMax_;
            fl = fxMax_;
            xh = xMin_;
            fh = fxMin_;
        }

        double del, froot;
        while (evaluationNumber_ <= maxEvaluations_) {
            // Increment with respect to latest value
            root_ = xl + (xh - xl) * fl / (fl - fh);
            froot = f.value(root_);
            ++evaluationNumber_;
            if (froot < 0.0) {       // Replace appropriate limit
                del = xl - root_;
                xl = root_;
                fl = froot;
            } else {
                del = xh - root_;
                xh = root_;
                fh = froot;
            }
            // Convergence criterion
            if (Math.abs(del) < xAccuracy || (close(froot, 0.0)))
                return root_;
        }

        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }
}
