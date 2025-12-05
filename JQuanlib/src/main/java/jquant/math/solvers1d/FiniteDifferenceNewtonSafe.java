package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.close;

/**
 * ! safe %Newton 1-D solver with finite difference derivatives
 * !
 * \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class FiniteDifferenceNewtonSafe extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        // Orient the search so that f(xl) < 0
        double xh, xl;
        if (fxMin_ < 0.0) {
            xl = xMin_;
            xh = xMax_;
        } else {
            xh = xMin_;
            xl = xMax_;
        }

        double froot = f.value(root_);
        ++evaluationNumber_;
        // first order finite difference derivative
        double dfroot = xMax_ - root_ < root_ - xMin_ ?
                (fxMax_ - froot) / (xMax_ - root_) :
                (fxMin_ - froot) / (xMin_ - root_);

        // xMax_-xMin_>0 is verified in the constructor
        double dx = xMax_ - xMin_;
        while (evaluationNumber_ <= maxEvaluations_) {
            double frootold = froot;
            double rootold = root_;
            double dxold = dx;
            // Bisect if (out of range || not decreasing fast enough)
            if ((((root_ - xh) * dfroot - froot) *
                    ((root_ - xl) * dfroot - froot) > 0.0)
                    || (Math.abs(2.0 * froot) > Math.abs(dxold * dfroot))) {
                dx = (xh - xl) / 2.0;
                root_ = xl + dx;
                // if the root estimate just computed is close to the
                // previous one, we should calculate dfroot at root and
                // xh rather than root and rootold (xl instead of xh would
                // be just as good)
                if (close(root_, rootold, 2500)) {
                    rootold = xh;
                    frootold = f.value(xh);
                }
            } else { // Newton
                dx = froot / dfroot;
                root_ -= dx;
            }

            // Convergence criterion
            if (Math.abs(dx) < xAccuracy)
                return root_;

            froot = f.value(root_);
            ++evaluationNumber_;
            dfroot = (frootold - froot) / (rootold - root_);

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
