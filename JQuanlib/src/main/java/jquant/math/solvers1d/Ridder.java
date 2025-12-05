package jquant.math.solvers1d;

import jquant.math.Function;
import jquant.math.Solver1D;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.math.MathUtils.close;

/**
 * ! %Ridder 1-D solver
 * ! \test the correctness of the returned values is tested by
 * checking them against known good results.
 * <p>
 * \ingroup solvers
 */
public class Ridder extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAcc) {
        /* The implementation of the algorithm was inspired by
           Press, Teukolsky, Vetterling, and Flannery,
           "Numerical Recipes in C", 2nd edition,
           Cambridge University Press
        */

        double fxMid, froot, s, xMid, nextRoot;

        // test on Black-Scholes implied volatility show that
        // Ridder solver algorithm actually provides an
        // accuracy 100 times below promised
        double xAccuracy = xAcc / 100.0;

        // Any highly unlikely value, to simplify logic below
        root_ = QL_MIN_REAL;

        while (evaluationNumber_ <= maxEvaluations_) {
            xMid = 0.5 * (xMin_ + xMax_);
            // First of two function evaluations per iteraton
            fxMid = f.value(xMid);
            ++evaluationNumber_;
            s = Math.sqrt(fxMid * fxMid - fxMin_ * fxMax_);
            if (close(s, 0.0)) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }
            // Updating formula
            nextRoot = xMid + (xMid - xMin_) *
                    ((fxMin_ >= fxMax_ ? 1.0 : -1.0) * fxMid / s);
            if (Math.abs(nextRoot - root_) <= xAccuracy) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }

            root_ = nextRoot;
            // Second of two function evaluations per iteration
            froot = f.value(root_);
            ++evaluationNumber_;
            if (close(froot, 0.0))
                return root_;

            // Bookkeeping to keep the root bracketed on next iteration
            if (sign(fxMid, froot) != fxMid) {
                xMin_ = xMid;
                fxMin_ = fxMid;
                xMax_ = root_;
                fxMax_ = froot;
            } else if (sign(fxMin_, froot) != fxMin_) {
                xMax_ = root_;
                fxMax_ = froot;
            } else if (sign(fxMax_, froot) != fxMax_) {
                xMin_ = root_;
                fxMin_ = froot;
            } else {
                QL_FAIL("never get here.");
            }

            if (Math.abs(xMax_ - xMin_) <= xAccuracy) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }
        }

        QL_FAIL("maximum number of function evaluations ("
                + maxEvaluations_ + ") exceeded");
        return 0;
    }

    private double sign(double a, double b) {
        return b >= 0.0 ? Math.abs(a) : (double) (-Math.abs(a));
    }
}
