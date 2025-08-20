package math.solvers1d;

import math.Function;
import math.Solver1D;

import static math.CommonUtil.QL_FAIL;
import static math.MathUtils.QL_EPSILON;
import static math.MathUtils.close;

public class Brent extends Solver1D {
    @Override
    public double solveImpl(Function f, double xAccuracy) {
        /* The implementation of the algorithm was inspired by
               Press, Teukolsky, Vetterling, and Flannery,
               "Numerical Recipes in C", 2nd edition, Cambridge
               University Press
            */

        double min1, min2;
        double froot, p, q, r, s, xAcc1, xMid;

        // we want to start with root_ (which equals the guess) on
        // one side of the bracket and both xMin_ and xMax_ on the
        // other.
        froot = f.value(root_);
        ++evaluationNumber_;
        if (froot * fxMin_ < 0) {
            xMax_ = xMin_;
            fxMax_ = fxMin_;
        } else {
            xMin_ = xMax_;
            fxMin_ = fxMax_;
        }
        double d = root_ - xMax_;
        double e = d;

        while (evaluationNumber_ <= maxEvaluations_) {
            if ((froot > 0.0 && fxMax_ > 0.0) ||
                    (froot < 0.0 && fxMax_ < 0.0)) {

                // Rename xMin_, root_, xMax_ and adjust bounds
                xMax_ = xMin_;
                fxMax_ = fxMin_;
                e = d = root_ - xMin_;
            }
            if (Math.abs(fxMax_) < Math.abs(froot)) {
                xMin_ = root_;
                root_ = xMax_;
                xMax_ = xMin_;
                fxMin_ = froot;
                froot = fxMax_;
                fxMax_ = fxMin_;
            }
            // Convergence check
            xAcc1 = 2.0 * QL_EPSILON * Math.abs(root_) + 0.5 * xAccuracy;
            xMid = (xMax_ - root_) / 2.0;
            if (Math.abs(xMid) <= xAcc1 || (close(froot, 0.0))) {
                f.value(root_);
                ++evaluationNumber_;
                return root_;
            }
            if (Math.abs(e) >= xAcc1 &&
                    Math.abs(fxMin_) > Math.abs(froot)) {

                // Attempt inverse quadratic interpolation
                s = froot / fxMin_;
                if (close(xMin_, xMax_)) {
                    p = 2.0 * xMid * s;
                    q = 1.0 - s;
                } else {
                    q = fxMin_ / fxMax_;
                    r = froot / fxMax_;
                    p = s * (2.0 * xMid * q * (q - r) - (root_ - xMin_) * (r - 1.0));
                    q = (q - 1.0) * (r - 1.0) * (s - 1.0);
                }
                if (p > 0.0) q = -q;  // Check whether in bounds
                p = Math.abs(p);
                min1 = 3.0 * xMid * q - Math.abs(xAcc1 * q);
                min2 = Math.abs(e * q);
                if (2.0 * p < (Math.min(min1, min2))) {
                    e = d;                // Accept interpolation
                    d = p / q;
                } else {
                    d = xMid;  // Interpolation failed, use bisection
                    e = d;
                }
            } else {
                // Bounds decreasing too slowly, use bisection
                d = xMid;
                e = d;
            }
            xMin_ = root_;
            fxMin_ = froot;
            if (Math.abs(d) > xAcc1)
                root_ += d;
            else
                root_ += sign(xAcc1, xMid);
            froot = f.value(root_);
            ++evaluationNumber_;
        }
        QL_FAIL("maximum number of function evaluations exceeded");
        return 0;
    }

    private double sign(double a, double b) {
        return b >= 0.0 ? Math.abs(a) : -Math.abs(a);
    }
}
