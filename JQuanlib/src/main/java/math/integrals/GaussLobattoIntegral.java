package math.integrals;

import math.Function;
import math.MathUtils;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.NULL_REAL;
import static math.MathUtils.QL_EPSILON;

public class GaussLobattoIntegral extends Integrator {
    protected double relAccuracy_;
    protected boolean useConvergenceEstimate_;
    protected final static double alpha_ = Math.sqrt(2.0 / 3.0);
    protected final static double beta_ = 1.0 / Math.sqrt(5.0);
    protected final static double x1_ = 0.94288241569547971906;
    protected final static double x2_ = 0.64185334234578130578;
    protected final static double x3_ = 0.23638319966214988028;

    public GaussLobattoIntegral(int maxIterations,
                                double absAccuracy) {
        super(absAccuracy, maxIterations);
        relAccuracy_ = MathUtils.NULL_REAL;
        useConvergenceEstimate_ = true;
    }

    public GaussLobattoIntegral(int maxIterations,
                                double absAccuracy,
                                double relAccuracy,
                                boolean useConvergenceEstimate) {
        super(absAccuracy, maxIterations);
        relAccuracy_ = relAccuracy;
        useConvergenceEstimate_ = useConvergenceEstimate;
    }

    protected double calculateAbsTolerance(
            Function f,
            double a, double b) {
        double relTol = Math.max(relAccuracy_, QL_EPSILON);

        final double m = (a + b) / 2;
        final double h = (b - a) / 2;
        final double y1 = f.value(a);
        final double y3 = f.value(m - alpha_ * h);
        final double y5 = f.value(m - beta_ * h);
        final double y7 = f.value(m);
        final double y9 = f.value(m + beta_ * h);
        final double y11 = f.value(m + alpha_ * h);
        final double y13 = f.value(b);

        final double f1 = f.value(m - x1_ * h);
        final double f2 = f.value(m + x1_ * h);
        final double f3 = f.value(m - x2_ * h);
        final double f4 = f.value(m + x2_ * h);
        final double f5 = f.value(m - x3_ * h);
        final double f6 = f.value(m + x3_ * h);

        double acc = h * (0.0158271919734801831 * (y1 + y13)
                + 0.0942738402188500455 * (f1 + f2)
                + 0.1550719873365853963 * (y3 + y11)
                + 0.1888215739601824544 * (f3 + f4)
                + 0.1997734052268585268 * (y5 + y9)
                + 0.2249264653333395270 * (f5 + f6)
                + 0.2426110719014077338 * y7);

        increaseNumberOfEvaluations(13);
        if (acc == 0.0 && (f1 != 0.0 || f2 != 0.0 || f3 != 0.0
                || f4 != 0.0 || f5 != 0.0 || f6 != 0.0)) {
            QL_FAIL("can not calculate absolute accuracy " +
                    "from relative accuracy");
        }

        double r = 1.0;
        if (useConvergenceEstimate_) {
            final double integral2 = (h / 6) * (y1 + y13 + 5 * (y5 + y9));
            final double integral1 = (h / 1470) * (77 * (y1 + y13) + 432 * (y3 + y11) +
                    625 * (y5 + y9) + 672 * y7);

            if (Math.abs(integral2 - acc) != 0.0)
                r = Math.abs(integral1 - acc) / Math.abs(integral2 - acc);
            if (r == 0.0 || r > 1.0)
                r = 1.0;
        }

        if (relAccuracy_ != NULL_REAL)
            return Math.min(absoluteAccuracy(), acc * relTol) / (r * QL_EPSILON);
        else {
            return absoluteAccuracy() / (r * QL_EPSILON);
        }
    }

    protected double adaptivGaussLobattoStep(Function f,
                                             double a, double b, double fa, double fb,
                                             double acc) {
        QL_REQUIRE(numberOfEvaluations() < maxEvaluations(),
                "max number of iterations reached");

        final double h = (b - a) / 2;
        final double m = (a + b) / 2;

        final double mll = m - alpha_ * h;
        final double ml = m - beta_ * h;
        final double mr = m + beta_ * h;
        final double mrr = m + alpha_ * h;

        final double fmll = f.value(mll);
        final double fml = f.value(ml);
        final double fm = f.value(m);
        final double fmr = f.value(mr);
        final double fmrr = f.value(mrr);
        increaseNumberOfEvaluations(5);

        final double integral2 = (h / 6) * (fa + fb + 5 * (fml + fmr));
        final double integral1 = (h / 1470) * (77 * (fa + fb)
                + 432 * (fmll + fmrr) + 625 * (fml + fmr) + 672 * fm);

        // avoid 80 bit logic on x86 cpu
        final double dist = acc + (integral1 - integral2);
        if (dist == acc || mll <= a || b <= mrr) {
            QL_REQUIRE(m > a && b > m, "Interval contains no more machine number");
            return integral1;
        } else {
            return adaptivGaussLobattoStep(f, a, mll, fa, fmll, acc)
                    + adaptivGaussLobattoStep(f, mll, ml, fmll, fml, acc)
                    + adaptivGaussLobattoStep(f, ml, m, fml, fm, acc)
                    + adaptivGaussLobattoStep(f, m, mr, fm, fmr, acc)
                    + adaptivGaussLobattoStep(f, mr, mrr, fmr, fmrr, acc)
                    + adaptivGaussLobattoStep(f, mrr, b, fmrr, fb, acc);
        }
    }

    @Override
    protected double integrate(Function f, double a, double b) {
        setNumberOfEvaluations(0);
        final double calcAbsTolerance = calculateAbsTolerance(f, a, b);

        increaseNumberOfEvaluations(2);
        return adaptivGaussLobattoStep(f, a, b, f.value(a), f.value(b), calcAbsTolerance);
    }
}
