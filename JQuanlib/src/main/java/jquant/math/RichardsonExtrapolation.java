package jquant.math;

import jquant.math.solvers1d.Brent;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

//! Richardson Extrapolation
/*! Richardson Extrapolation is a sequence acceleration technique for
    \f[
        f(\Delta h) = f_0 + \alpha\cdot (\Delta h)^n + O((\Delta h)^{n+1})
    \f]

    References: http://en.wikipedia.org/wiki/Richardson_extrapolation
*/
public class RichardsonExtrapolation {
    private final double delta_h_;
    private final double fdelta_h_;
    private final double n_;
    private final Function f_;

    /*! \param f function to be extrapolated to delta_h -> 0
        \param delta_h step size
        \param n if known, n is the order of convergence
    */
    public RichardsonExtrapolation(Function f, double delta_h, double n) {
        delta_h_ = delta_h;
        fdelta_h_ = f.value(delta_h);
        n_ = n;
        f_ = f;
    }

    public RichardsonExtrapolation(Function f, double delta_h) {
        this(f, delta_h, NULL_REAL);
    }

    //! Extrapolation for known order of convergence
    public double value(double t) {
        QL_REQUIRE(t > 1, "scaling factor must be greater than 1");
        QL_REQUIRE(n_ != NULL_REAL, "order of convergence must be known");

        final double tk = Math.pow(t, n_);
        return (tk * f_.value(delta_h_ / t) - fdelta_h_) / (tk - 1.0);
    }

    public double value() {
        return value(2.0);
    }

    //! Extrapolation for unknown order of convergence
    public double value(double t, double s) {
        QL_REQUIRE(t > 1 && s > 1, "scaling factors must be greater than 1");
        QL_REQUIRE(t > s, "t must be greater than s");

        final double ft = f_.value(delta_h_ / t);
        final double fs = f_.value(delta_h_ / s);

        final RichardsonEqn eqn = new RichardsonEqn(fdelta_h_, ft, fs, t, s);

        final double step = 0.1;
        double left = 0.05;
        double fr = eqn.value(left + step), fl = eqn.value(left);
        while (fr * fl > 0.0 && left < 15.1) {
            left += step;
            fl = fr;
            fr = eqn.value(left + step);
        }

        QL_REQUIRE(left < 15.1, "could not estimate the order of convergence");

        final Brent brent = new Brent();
        final double k = brent.solve(eqn, 1e-8, left + 0.5 * step, left, left + step);

        final double ts = Math.pow(s, k);
        return (ts * fs - fdelta_h_) / (ts - 1.0);
    }

    private static class RichardsonEqn implements Function {
        private final double fdelta_h_, ft_, fs_, t_, s_;

        RichardsonEqn(double fh, double ft, double fs, double t, double s) {
            fdelta_h_ = fh;
            ft_ = ft;
            fs_ = fs;
            t_ = t;
            s_ = s;
        }

        @Override
        public double value(double k) {
            return ft_ + (ft_ - fdelta_h_) / (Math.pow(t_, k) - 1.0)
                    - (fs_ + (fs_ - fdelta_h_) / (Math.pow(s_, k) - 1.0));
        }
    }
}
