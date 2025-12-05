package jquant.math;

import jquant.termstructures.volatility.AbcdFunction;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class MathUtils {
    public static final double NULL_REAL = 3.40282e+38;

    public static final int NULL_SIZE = 2147483647;

    public static final double QL_MIN_POSITIVE_REAL = 2.22507e-308;

    public static final double M_EULER_MASCHERONI = 0.5772156649015328606065121;

    public static final double M_PI_2   =   1.57079632679489661923;
    public static final double M_SQRT_2 = 0.7071067811865475244008443621048490392848359376887;
    public static final double M_SQRT2 = 1.41421356237309504880;
    public static final double QL_MAX_REAL = 1.79769e+308;

    public static final double QL_MIN_REAL = -1.79769e+308;
    public static final double M_1_SQRTPI = 0.564189583547756286948;

    public static final double QL_EPSILON = 2.22045e-16;

    public static final double DBL_MIN = 2.2250738585072014e-308;

    public static final double M_PI = 3.141592653589793238462643383280;

    public static final int MAX_FUNCTION_EVALUATIONS = 100;

    public static final double M_TWOPI = M_PI * 2;

    public static final int PPMT_MAX_DIM = 21200;

    public static final double M_LN2 = 0.693147180559945309417;  // ln(2)

    public static final double squared(double x) {
        return x * x;
    }

    public static boolean close_enough(double x, double y) {
        // see close() for a note on duplication

        // Deals with +infinity and -infinity representations etc.
        if (x == y)
            return true;

        double diff = Math.abs(x - y);
        double tolerance = 42 * QL_EPSILON;

        if (x == 0.0 || y == 0.0) // x or y = 0.0
            return diff < (tolerance * tolerance);

        return diff <= tolerance * Math.abs(x) ||
                diff <= tolerance * Math.abs(y);
    }

    public static boolean close(double x, double y) {
        // we're duplicating the code here instead of calling close(x,y,42)
        // for optimization; this allows us to make tolerance constexpr
        // and shave a few more cycles.

        // Deals with +infinity and -infinity representations etc.
        if (x == y)
            return true;

        double diff = Math.abs(x - y);
        double tolerance = 42 * QL_EPSILON;

        if (x == 0.0 || y == 0.0)
            return diff < (tolerance * tolerance);

        return diff <= tolerance * Math.abs(x) &&
                diff <= tolerance * Math.abs(y);
    }

    public static double binomialCoefficientLn(int n, int k) {
        if (n < k) {
            throw new IllegalArgumentException("n < k not allowed.");
        }
        return Factorial.ln(n) - Factorial.ln(k) - Factorial.ln(n - k);
    }

    public static double binomialCoefficient(int n, int k) {
        return Math.floor(0.5 + Math.exp(binomialCoefficientLn(n, k)));
    }

    public static double PeizerPrattMethod2Inversion(double z, int n) {
        if (n % 2 != 1) {
            throw new IllegalArgumentException("n must be odd.");
        }
        double result = (z / (n + 1.0 / 3.0 + 0.1 / (n + 1.0)));
        result *= result;
        result = Math.exp(-result * (n + 1.0 / 6.0));
        result = 0.5 + (z > 0 ? 1 : -1) * Math.sqrt((0.25 * (1.0 - result)));
        return result;
    }

    public static double betaContinuedFraction(double a, double b, double x,
                                               double accuracy, int maxIteration) {

        double aa, del;
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double c = 1.0;
        double d = 1.0 - qab * x / qap;
        if (Math.abs(d) < QL_EPSILON) {
            d = QL_EPSILON;
        }
        d = 1.0 / d;
        double result = d;

        int m, m2;
        for (m = 1; m <= maxIteration; m++) {
            m2 = 2 * m;
            aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < QL_EPSILON) {
                d = QL_EPSILON;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < QL_EPSILON) {
                c = QL_EPSILON;
            }
            d = 1.0 / d;
            result *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < QL_EPSILON) {
                d = QL_EPSILON;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < QL_EPSILON) {
                c = QL_EPSILON;
            }
            d = 1.0 / d;
            del = d * c;
            result *= del;
            if (Math.abs(del - 1.0) < accuracy) {
                return result;
            }
        }
        throw new IllegalArgumentException("a or b too big, or maxIteration too small in betacf");
    }

    public static double incompleteBetaFunction(double a, double b,
                                                double x) {
        return incompleteBetaFunction(a, b, x, 1e-16, 100);
    }

    public static double incompleteBetaFunction(double a, double b,
                                                double x, double accuracy,
                                                int maxIteration) {
        if (a <= 0) {
            throw new IllegalArgumentException("a must be greater than zero");
        }
        if (b <= 0) {
            throw new IllegalArgumentException("b must be greater than zero");
        }

        if (x == 0.0)
            return 0.0;
        else if (x == 1.0)
            return 1.0;
        else {
            if (x < 0 || x > 1) {
                throw new IllegalArgumentException("x must be in [0,1]");
            }
        }
        GammaFunction gammaFunction = new GammaFunction();
        double result = Math.exp(gammaFunction.logValue(a + b) -
                gammaFunction.logValue(a) - gammaFunction.logValue(b) +
                a * Math.log(x) + b * Math.log(1.0 - x));

        if (x < (a + 1.0) / (a + b + 2.0))
            return result *
                    betaContinuedFraction(a, b, x, accuracy, maxIteration) / a;
        else
            return 1.0 - result *
                    betaContinuedFraction(b, a, 1.0 - x, accuracy, maxIteration) / b;
    }

    public static double incompleteGammaFunction(double a, double x) {
        return incompleteGammaFunction(a, x, 1.0e-13, 100);
    }

    public static double incompleteGammaFunction(double a, double x, double accuracy,
                                                 Integer maxIteration) {

        QL_REQUIRE(a > 0.0, "non-positive a is not allowed");

        QL_REQUIRE(x >= 0.0, "negative x non allowed");

        if (x < (a + 1.0)) {
            // Use the series representation
            return incompleteGammaFunctionSeriesRepr(a, x,
                    accuracy, maxIteration);
        } else {
            // Use the continued fraction representation
            return 1.0 - incompleteGammaFunctionContinuedFractionRepr(a, x,
                    accuracy, maxIteration);
        }

    }

    public static double incompleteGammaFunctionSeriesRepr(double a, double x, double accuracy,
                                                           Integer maxIteration) {

        if (x == 0.0) return 0.0;

        double gln = new GammaFunction().logValue(a);
        double ap = a;
        double del = 1.0 / a;
        double sum = del;
        for (int n = 1; n <= maxIteration; n++) {
            ++ap;
            del *= x / ap;
            sum += del;
            if (Math.abs(del) < Math.abs(sum) * accuracy) {
                return sum * Math.exp(-x + a * Math.log(x) - gln);
            }
        }
        QL_FAIL("accuracy not reached");
        return 0;
    }

    public static double incompleteGammaFunctionContinuedFractionRepr(double a, double x,
                                                                      double accuracy,
                                                                      Integer maxIteration) {

        int i;
        double an, b, c, d, del, h;
        double gln = new GammaFunction().logValue(a);
        b = x + 1.0 - a;
        c = 1.0 / QL_EPSILON;
        d = 1.0 / b;
        h = d;
        for (i = 1; i <= maxIteration; i++) {
            an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < QL_EPSILON) d = QL_EPSILON;
            c = b + an / c;
            if (Math.abs(c) < QL_EPSILON) c = QL_EPSILON;
            d = 1.0 / d;
            del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < accuracy) {
                return Math.exp(-x + a * Math.log(x) - gln) * h;
            }
        }

        QL_FAIL("accuracy not reached");
        return 0;
    }

    public static double abcdBlackVolatility(double u, double a, double b, double c, double d) {
        AbcdFunction model = new AbcdFunction(a,b,c,d);
        return model.volatility(0.,u,u);
    }

}
