package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.close;

/**
 * ! %Secant 1-D solver
 * ! \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class Secant extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
           Press, Teukolsky, Vetterling, and Flannery,
           "Numerical Recipes in C", 2nd edition,
           Cambridge University Press
        */

        double fl, froot, dx, xl;

        // Pick the bound with the smaller function value
        // as the most recent guess
        if (Math.abs(fxMin_) < Math.abs(fxMax_)) {
            root_ = xMin_;
            froot = fxMin_;
            xl = xMax_;
            fl = fxMax_;
        } else {
            root_ = xMax_;
            froot = fxMax_;
            xl = xMin_;
            fl = fxMin_;
        }
        while (evaluationNumber_ <= maxEvaluations_) {
            dx = (xl - root_) * froot / (froot - fl);
            xl = root_;
            fl = froot;
            root_ += dx;
            froot = f.value(root_);
            ++evaluationNumber_;
            if (Math.abs(dx) < xAccuracy || (close(froot, 0.0)))
                return root_;
        }
        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }
}
