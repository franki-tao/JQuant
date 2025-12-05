package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! safe %Newton 1-D solver
 * ! \note This solver requires that the passed function object
 * implement a method <tt>Real derivative(Real)</tt>.
 * <p>
 * \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class NewtonSafe extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
           Press, Teukolsky, Vetterling, and Flannery,
           "Numerical Recipes in C", 2nd edition,
           Cambridge University Press
        */

        double froot, dfroot, dx, dxold;
        double xh, xl;

        // Orient the search so that f(xl) < 0
        if (fxMin_ < 0.0) {
            xl = xMin_;
            xh = xMax_;
        } else {
            xh = xMin_;
            xl = xMax_;
        }

        // the "stepsize before last"
        dxold = xMax_ - xMin_;
        // it was dxold=std::fabs(xMax_-xMin_); in Numerical Recipes
        // here (xMax_-xMin_ > 0) is verified in the constructor

        // and the last step
        dx = dxold;

        froot = f.value(root_);
        dfroot = f.derivative(root_);
        QL_REQUIRE(!Double.isNaN(dfroot),
                "NewtonSafe requires function's derivative");
        ++evaluationNumber_;

        while (evaluationNumber_ <= maxEvaluations_) {
            // Bisect if (out of range || not decreasing fast enough)
            if ((((root_ - xh) * dfroot - froot) *
                    ((root_ - xl) * dfroot - froot) > 0.0)
                    || (Math.abs(2.0 * froot) > Math.abs(dxold * dfroot))) {

                dxold = dx;
                dx = (xh - xl) / 2.0;
                root_ = xl + dx;
            } else {
                dxold = dx;
                dx = froot / dfroot;
                root_ -= dx;
            }
            // Convergence criterion
            if (Math.abs(dx) < xAccuracy) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }
            froot = f.value(root_);
            dfroot = f.derivative(root_);
            ++evaluationNumber_;
            if (froot < 0.0)
                xl = root_;
            else
                xh = root_;
        }

        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }
}
