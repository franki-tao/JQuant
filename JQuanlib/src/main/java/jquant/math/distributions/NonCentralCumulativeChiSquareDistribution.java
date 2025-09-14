package jquant.math.distributions;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.QL_EPSILON;

public class NonCentralCumulativeChiSquareDistribution {
    private double df_;
    private double ncp_;

    public NonCentralCumulativeChiSquareDistribution(double df, double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    public double value(double x) {
        if (x <= 0.0)
            return 0.0;

        double errmax = 1e-12;
        int itrmax = 10000;
        double lam = 0.5 * ncp_;

        double u = Math.exp(-lam);
        double v = u;
        double x2 = 0.5 * x;
        double f2 = 0.5 * df_;
        double f_x_2n = df_ - x;

        double t = 0.0;
        if (f2 * QL_EPSILON > 0.125 && Math.abs(x2 - f2) < Math.sqrt(QL_EPSILON) * f2) {
            t = Math.exp((1 - t) * (2 - t / (f2 + 1))) / Math.sqrt(2.0 * M_PI * (f2 + 1.0));
        } else {
            t = Math.exp(f2 * Math.log(x2) - x2 - new GammaFunction().logValue(f2 + 1));
        }

        double ans = v * t;

        boolean flag = false;
        int n = 1;
        double f_2n = df_ + 2.0;
        f_x_2n += 2.0;

        double bound;
        for (; ; ) {
            if (f_x_2n > 0) {
                flag = true;
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break;
                }
            }
            for (; ; ) {
                u *= lam / n;
                v += u;
                t *= x / f_2n;
                ans += v * t;
                n++;
                f_2n += 2.0;
                f_x_2n += 2.0;
                if (!flag && n <= itrmax) {
                    break;
                }
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break;
                }

            }
        }
        if (bound > errmax) {
            QL_FAIL("didn't converge");
        }
        return ans;
    }
}
