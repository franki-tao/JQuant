package math;

import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.integrals.SimpsonIntegral;
import jquant.math.interpolations.CubicInterpolation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.NotAKnot;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class InterpolationsTest {
    public List<Double> xRange(double start, double finish, int points) {
        List<Double> x = CommonUtil.ArrayInit(points);
        double dx = (finish - start) / (points - 1);
        for (int i = 0; i < points - 1; i++)
            x.set(i, start + i * dx);
        x.set(points - 1, finish);
        return x;
    }

    public List<Double> gaussian(final List<Double> x) {
        List<Double> y = CommonUtil.ArrayInit(x.size());
        for (int i = 0; i < x.size(); i++)
            y.set(i, Math.exp(-x.get(i) * x.get(i)));
        return y;
    }

    public List<Double> parabolic(final List<Double> x) {
        List<Double> y = CommonUtil.ArrayInit(x.size());
        for (int i = 0; i < x.size(); i++)
            y.set(i, -x.get(i) * x.get(i));
        return y;
    }

    public void checkValues(String type, CubicInterpolation cubic, double[] x, double[] y) {
        double tolerance = 2.0e-15;
        for (int i = 0; i < x.length; i++) {
            double interpolated = cubic.value(x[i], false);
            assertFalse(Math.abs(interpolated - y[i]) > tolerance);
        }
    }

    public void check1stDerivativeValue(String type, CubicInterpolation cubic, double x, double value) {
        double tolerance = 1.0e-14;
        double interpolated = cubic.derivative(x, false);
        double error = Math.abs(interpolated - value);
        assertFalse(error > tolerance, "interpolation first derivative failure");
    }

    public void check2ndDerivativeValue(String type, CubicInterpolation cubic, double x, double value) {
        double tolerance = 1.0e-13;
        double interpolated = cubic.secondDerivative(x, false);
        double error = Math.abs(interpolated - value);
        assertFalse(error > tolerance, "interpolation second derivative failure");
    }

    public void checkNotAKnotCondition(String type, CubicInterpolation cubic) {
        double tolerance = 1.0e-14;
        List<Double> c = cubic.cCoefficients();
        assertFalse(Math.abs(c.get(0) - c.get(1)) > tolerance, "interpolation not-a-knot condition failure");
        int n = c.size();
        assertFalse(Math.abs(c.get(n - 2) - c.get(n - 1)) > tolerance, "interpolation not-a-knot condition failure");
    }

    public void checkSymmetry(String type, CubicInterpolation cubic, double xMin) {
        double tolerance = 1.0e-15;
        for (double x = xMin; x < 0.0; x += 0.1) {
            double y1 = cubic.value(x, false);
            double y2 = cubic.value(-x, false);
            assertFalse(Math.abs(y1 - y2) > tolerance, "interpolation not symmetric");
        }
    }

    public class errorFuncion implements Function{
        private Interpolation f_;

        public errorFuncion(Interpolation f) {
            f_ = f;
        }
        @Override
        public double value(double x) {
            double temp = f_.value(x, false) - Math.exp(-x * x);
            return temp * temp;
        }
    }

    public errorFuncion make_error_function(Interpolation f) {
        return new errorFuncion(f);
    }

    public double multif(double s, double t, double u, double v, double w) {
        return Math.sqrt(s * Math.sinh(Math.log(t)) +
                Math.exp(Math.sin(u) * Math.sin(3 * v)) +
                Math.sinh(Math.log(v * w)));
    }

    public double epanechnikovKernel(double u) {

        if (Math.abs(u) <= 1) {
            return (3.0 / 4.0) * (1 - u * u);
        } else {
            return 0.0;
        }
    }

    public int sign(double y1, double y2) {
        return Double.compare(y2, y1);
    }

    public class GF implements Function{
        private double exponent_, factor_;

        public GF(double exponent, double factor) {
            exponent_ = exponent;
            factor_ = factor;
        }
        @Override
        public double value(double h) {
            return M_PI + factor_ * Math.pow(h, exponent_)
                    + Math.pow(factor_ * h, exponent_ + 1);
        }
    }

    public double limCos(double h) {
        return -Math.cos(h);
    }

    double f(double h) {
        return Math.pow(1.0 + h, 1 / h);
    }

    double lagrangeTestFct(double x) {
        return Math.abs(x) + 0.5 * x - x * x;
    }

    /* See J. M. Hyman, "Accurate monotonicity preserving cubic interpolation"
       SIAM J. of Scientific and Statistical Computing, v. 4, 1983, pp. 645-654.
       http://math.lanl.gov/~mac/papers/numerics/H83.pdf
    */
    @Test
    public void testSplineErrorOnGaussianValues() {
        System.out.println("Testing spline approximation on Gaussian data sets...");
        int[] points = {5, 9, 17, 33};
        // complete spline data from the original 1983 Hyman paper
        double[] tabulatedErrors     = { 3.5e-2, 2.0e-3, 4.0e-5, 1.8e-6 };
        double[] toleranceOnTabErr   = { 0.1e-2, 0.1e-3, 0.1e-5, 0.1e-6 };

        // (complete) MC spline data from the original 1983 Hyman paper
        // NB: with the improved Hyman filter from the Dougherty, Edelman, and
        //     Hyman 1989 paper the n=17 nonmonotonicity is not filtered anymore
        //     so the error agrees with the non MC method.
        double[] tabulatedMCErrors   = { 1.7e-2, 2.0e-3, 4.0e-5, 1.8e-6 };
        double[] toleranceOnTabMCErr = { 0.1e-2, 0.1e-3, 0.1e-5, 0.1e-6 };

        SimpsonIntegral integral = new SimpsonIntegral(1e-12, 10000);
        List<Double> x, y;

        // still unexplained scale factor needed to obtain the numerical
        // results from the paper
        double scaleFactor = 1.9;

        for (int i=0; i<points.length; i++) {
            int n = points[i];
            x = xRange(-1.7, 1.9, n);
            y = gaussian(x);

            // Not-a-knot
            CubicInterpolation f = new CubicInterpolation(CommonUtil.toArray(x), CommonUtil.toArray(y),
                    Spline, false,
                    NotAKnot, NULL_REAL,
                    NotAKnot, NULL_REAL
            );
            f.update();
            double result = Math.sqrt(integral.value(make_error_function(f), -1.7, 1.9));
            result /= scaleFactor;
            assertFalse(Math.abs(result-tabulatedErrors[i]) > toleranceOnTabErr[i],
                    "Not-a-knot spline interpolation ");

            // MC not-a-knot
            f = new CubicInterpolation(CommonUtil.toArray(x), CommonUtil.toArray(y),
                    Spline, true,
                    NotAKnot, NULL_REAL,
                    NotAKnot, NULL_REAL);
            f.update();
            result = Math.sqrt(integral.value(make_error_function(f), -1.7, 1.9));
            result /= scaleFactor;
            assertFalse(Math.abs(result-tabulatedMCErrors[i]) > toleranceOnTabMCErr[i],
                    "MC Not-a-knot spline interpolation ");
        }
    }
}
