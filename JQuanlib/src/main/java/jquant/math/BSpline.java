package jquant.math;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! B-spline basis functions
/*! Follows treatment and notation from:

    Weisstein, Eric W. "B-Spline." From MathWorld--A Wolfram Web
    Resource.  <http://mathworld.wolfram.com/B-Spline.html>

    \ingroup math
*/
public class BSpline {
    // e.g. p_=2 is a quadratic B-spline, p_=3 is a cubic B-Spline, etc.
    private final int p_;
    // n_ + 1 = "control points" = max number of basis functions
    private final int n_;
    private final double[] knots_;

    public BSpline(int p, int n, List<Double> knots) {
        p_ = p;
        n_ = n;
        knots_ = CommonUtil.toArray(knots);

        QL_REQUIRE(p >= 1, "lowest degree B-spline has p = 1");
        QL_REQUIRE(n >= 1, "number of control points n+1 >= 2");
        QL_REQUIRE(p <= n, "must have p <= n");

        QL_REQUIRE(knots_.length == p + n + 2,
                "number of knots must equal p+n+2");

        for (int i = 0; i < knots_.length - 1; ++i) {
            QL_REQUIRE(knots_[i] <= knots_[i + 1],
                    "knots points must be nondecreasing");
        }
    }

    public double value(int i, double x) {
        QL_REQUIRE(i <= n_, "i must not be greater than n");
        return N(i, p_, x);
    }

    // recursive definition of N, the B-spline basis function
    private double N(int i, int p, double x) {
        if (p == 0) {
            return (knots_[i] <= x && x < knots_[i + 1]) ? 1.0 : 0.0;
        } else {
            return ((x - knots_[i]) / (knots_[i + p] - knots_[i])) * N(i, p - 1, x) +
                    ((knots_[i + p + 1] - x) / (knots_[i + p + 1] - knots_[i + 1])) * N(i + 1, p - 1, x);
        }
    }
}
