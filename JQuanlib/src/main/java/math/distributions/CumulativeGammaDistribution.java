package math.distributions;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.QL_EPSILON;
import static math.MathUtils.QL_MAX_REAL;

public class CumulativeGammaDistribution {
    private double a_;

    public CumulativeGammaDistribution(double a) {
        QL_REQUIRE(a > 0.0, "invalid parameter for gamma distribution");
        this.a_ = a;
    }

    public double value(double x) {
        if (x <= 0.0) return 0.0;

        double gln = new GammaFunction().logValue(a_);

        if (x < (a_ + 1.0)) {
            double ap = a_;
            double del = 1.0 / a_;
            double sum = del;
            for (int n = 1; n <= 100; n++) {
                ap += 1.0;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 3.0e-7)
                    return sum * Math.exp(-x + a_ * Math.log(x) - gln);
            }
        } else {
            double b = x + 1.0 - a_;
            double c = QL_MAX_REAL;
            double d = 1.0 / b;
            double h = d;
            for (int n = 1; n <= 100; n++) {
                double an = -1.0 * n * (n - a_);
                b += 2.0;
                d = an * d + b;
                if (Math.abs(d) < QL_EPSILON) d = QL_EPSILON;
                c = b + an / c;
                if (Math.abs(c) < QL_EPSILON) c = QL_EPSILON;
                d = 1.0 / d;
                double del = d * c;
                h *= del;
                if (Math.abs(del - 1.0) < QL_EPSILON)
                    return 1.0 - h * Math.exp(-x + a_ * Math.log(x) - gln);
            }
        }
        QL_FAIL("too few iterations");
        return 0;
    }
}
