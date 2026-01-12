package math;

import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.integrals.SimpsonIntegral;
import jquant.math.interpolations.*;
import jquant.termstructures.volatility.Sarb;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer.Vanilla.std;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.*;
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
            // System.out.println("Interpolated value at " + x[i] + " is " + interpolated + " and should be " + y[i]);
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
    /* See J. M. Hyman, "Accurate monotonicity preserving cubic interpolation"
       SIAM J. of Scientific and Statistical Computing, v. 4, 1983, pp. 645-654.
       http://math.lanl.gov/~mac/papers/numerics/H83.pdf
    */
    @Test
    public void testSplineOnGaussianValues() {
        System.out.println("Testing spline interpolation on a Gaussian data set...");
        double interpolated, interpolated2;
        int n = 5;
        List<Double> x = CommonUtil.ArrayInit(n);
        List<Double> y = CommonUtil.ArrayInit(n);
        double x1_bad=-1.7, x2_bad=1.7;
        for (double start = -1.9, j=0; j<2; start+=0.2, j++) {
            x = xRange(start, start+3.6, n);
            y = gaussian(x);

            // Not-a-knot spline
            CubicInterpolation f = new CubicInterpolation(CommonUtil.toArray(x), CommonUtil.toArray(y),
                    Spline, false,
                    NotAKnot, NULL_REAL,
                    NotAKnot, NULL_REAL);
            f.update();
            checkValues("Not-a-knot spline", f,
                    CommonUtil.toArray(x), CommonUtil.toArray(y));
            checkNotAKnotCondition("Not-a-knot spline", f);
            // bad performance
            interpolated = f.value(x1_bad, false);
            interpolated2= f.value(x2_bad, false);
            assertFalse(interpolated>0.0 && interpolated2>0.0, "Not-a-knot spline interpolation ");
            // MC not-a-knot spline
            f = new CubicInterpolation(CommonUtil.toArray(x), CommonUtil.toArray(y),
                    Spline, true,
                    NotAKnot, NULL_REAL,
                    NotAKnot, NULL_REAL);
            f.update();
            checkValues("MC not-a-knot spline", f,
                    CommonUtil.toArray(x), CommonUtil.toArray(y));
            // good performance
            interpolated = f.value(x1_bad, false);
            assertFalse(interpolated<0.0, "MC not-a-knot spline interpolation ");
            interpolated = f.value(x2_bad, false);
            assertFalse(interpolated<0.0, "MC not-a-knot spline interpolation ");
        }
    }
    /* See J. M. Hyman, "Accurate monotonicity preserving cubic interpolation"
       SIAM J. of Scientific and Statistical Computing, v. 4, 1983, pp. 645-654.
       http://math.lanl.gov/~mac/papers/numerics/H83.pdf
    */
    @Test
    public void testSplineOnRPN15AValues() {
        System.out.println("Testing spline interpolation on RPN15A data set...");
        double[] RPN15A_x = {
                7.99,       8.09,       8.19,      8.7,
                9.2,     10.0,     12.0,     15.0,     20.0
        };
        double[] RPN15A_y = {
                0.0, 2.76429e-5, 4.37498e-5, 0.169183,
                0.469428, 0.943740, 0.998636, 0.999919, 0.999994
        };
        double interpolated;
        // Natural spline
        CubicInterpolation f = new CubicInterpolation(
                RPN15A_x,
                RPN15A_y,
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
        f.update();
        checkValues("Natural spline", f,
                RPN15A_x,
                RPN15A_y);
        check2ndDerivativeValue("Natural spline", f,
                RPN15A_x[0], 0.0);
        check2ndDerivativeValue("Natural spline", f,
                20.0, 0.0);
        // poor performance
        double x_bad = 11.0;
        interpolated = f.value(x_bad,false);
        assertFalse(interpolated<1.0, "Natural spline interpolation ");

        // Clamped spline
        f = new CubicInterpolation(RPN15A_x,
                RPN15A_y,
                Spline, false,
                FirstDerivative, 0.0,
                FirstDerivative, 0.0);
        f.update();
        checkValues("Clamped spline", f,
                RPN15A_x,
                RPN15A_y);
        check1stDerivativeValue("Clamped spline", f,
                RPN15A_x[0], 0.0);
        check1stDerivativeValue("Clamped spline", f,
                20.0, 0.0);
        // poor performance
        interpolated = f.value(x_bad,false);
        assertFalse(interpolated<1.0, "Clamped spline interpolation ");

        // Not-a-knot spline
        f = new CubicInterpolation(RPN15A_x,
                RPN15A_y,
                Spline, false,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("Not-a-knot spline", f,
                RPN15A_x,
                RPN15A_y);
        checkNotAKnotCondition("Not-a-knot spline", f);
        // poor performance
        interpolated = f.value(x_bad,false);
        assertFalse(interpolated<1.0, "Not-a-knot spline interpolation ");

        // MC natural spline values
        f = new CubicInterpolation(RPN15A_x,
                RPN15A_y,
                Spline, true,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
        f.update();
        checkValues("MC natural spline", f,
                RPN15A_x,
                RPN15A_y);
        // good performance
        interpolated = f.value(x_bad,false);
        assertFalse(interpolated>1.0, "MC natural spline interpolation ");
        // MC clamped spline values
        f = new CubicInterpolation(RPN15A_x,
                RPN15A_y,
                Spline, true,
                FirstDerivative, 0.0,
                FirstDerivative, 0.0);
        f.update();
        checkValues("MC clamped spline", f,
                RPN15A_x,
                RPN15A_y);
        check1stDerivativeValue("MC clamped spline", f,
                RPN15A_x[0], 0.0);
        check1stDerivativeValue("MC clamped spline", f,
                20.0, 0.0);
        // good performance
        interpolated = f.value(x_bad, false);
        assertFalse(interpolated>1.0, "MC clamped spline interpolation ");
        // MC not-a-knot spline values
        f = new CubicInterpolation(RPN15A_x,
                RPN15A_y,
                Spline, true,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("MC not-a-knot spline", f,
                RPN15A_x,
                RPN15A_y);
        // good performance
        interpolated = f.value(x_bad, false);
        assertFalse(interpolated>1.0, "MC clamped spline interpolation");
    }
    /* Blossey, Frigyik, Farnum "A Note On CubicSpline Splines"
       Applied Linear Algebra and Numerical Analysis AMATH 352 Lecture Notes
       http://www.amath.washington.edu/courses/352-winter-2002/spline_note.pdf
    */
    @Test
    public void testSplineOnGenericValues() {
        System.out.println("Testing spline interpolation on generic values...");
        double[] generic_x = { 0.0, 1.0, 3.0, 4.0 };
        double[] generic_y = { 0.0, 0.0, 2.0, 2.0 };
        double[] generic_natural_y2 = { 0.0, 1.5, -1.5, 0.0 };

        double interpolated, error;
        int i, n = 4;
        List<Double> x35 = CommonUtil.ArrayInit(3);

        // Natural spline
        CubicInterpolation f = new CubicInterpolation(generic_x, generic_y,
                Spline, false,
                SecondDerivative,
                generic_natural_y2[0],
                SecondDerivative,
                generic_natural_y2[n-1]);
        f.update();
        checkValues("Natural spline", f, generic_x, generic_y);
        // cached second derivative
        for (i=0; i<n; i++) {
            interpolated = f.secondDerivative(generic_x[i], false);
            error = interpolated - generic_natural_y2[i];
            assertFalse(Math.abs(error)>3e-16, "Natural spline interpolation ");
        }
        x35.set(1, f.value(3.5,false));


        // Clamped spline
        double y1a = 0.0, y1b = 0.0;
        f = new CubicInterpolation(generic_x, generic_y,
                Spline, false,
                FirstDerivative, y1a,
                FirstDerivative, y1b);
        f.update();
        checkValues("Clamped spline", f,
                generic_x, generic_y);
        check1stDerivativeValue("Clamped spline", f,
                0, 0.0);
        check1stDerivativeValue("Clamped spline", f,
                4, 0.0);
        x35.set(0, f.value(3.5, false));


        // Not-a-knot spline
        f = new CubicInterpolation(generic_x, generic_y,
                Spline, false,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("Not-a-knot spline", f,
                generic_x, generic_y);
        checkNotAKnotCondition("Not-a-knot spline", f);

        x35.set(2, f.value(3.5, false));
        assertFalse(x35.get(0)>x35.get(1) || x35.get(1)>x35.get(2), "Spline interpolation failure");
    }
    @Test
    public void testSimmetricEndConditions() {
        System.out.println("Testing symmetry of spline interpolation end-conditions...");
        int n = 9;

        List<Double> x, y;
        x = xRange(-1.8, 1.8, n);
        y = gaussian(x);

        // Not-a-knot spline
        CubicInterpolation f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, false,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("Not-a-knot spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        checkNotAKnotCondition("Not-a-knot spline", f);
        checkSymmetry("Not-a-knot spline", f, x.get(0));


        // MC not-a-knot spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("MC not-a-knot spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        checkSymmetry("MC not-a-knot spline", f, x.get(0));
    }
    @Test
    public void testDerivativeEndConditions() {
        System.out.println("Testing derivative end-conditions for spline interpolation...");
        int n = 4;

        List<Double> x, y;
        x = xRange(-2.0, 2.0, n);
        y = parabolic(x);

        // Not-a-knot spline
        CubicInterpolation f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, false,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("Not-a-knot spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("Not-a-knot spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("Not-a-knot spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("Not-a-knot spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("Not-a-knot spline", f,
                x.get(n-1), -2.0);


        // Clamped spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, false,
                FirstDerivative,  4.0,
                FirstDerivative, -4.0);
        f.update();
        checkValues("Clamped spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("Clamped spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("Clamped spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("Clamped spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("Clamped spline", f,
                x.get(n-1), -2.0);


        // SecondDerivative spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, false,
                SecondDerivative, -2.0,
                SecondDerivative, -2.0);
        f.update();
        checkValues("SecondDerivative spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("SecondDerivative spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("SecondDerivative spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("SecondDerivative spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("SecondDerivative spline", f,
                x.get(n-1), -2.0);

        // MC Not-a-knot spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        checkValues("MC Not-a-knot spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("MC Not-a-knot spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("MC Not-a-knot spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("MC Not-a-knot spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("MC Not-a-knot spline", f,
                x.get(n-1), -2.0);


        // MC Clamped spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                FirstDerivative,  4.0,
                FirstDerivative, -4.0);
        f.update();
        checkValues("MC Clamped spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("MC Clamped spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("MC Clamped spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("MC Clamped spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("MC Clamped spline", f,
                x.get(n-1), -2.0);


        // MC SecondDerivative spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                SecondDerivative, -2.0,
                SecondDerivative, -2.0);
        f.update();
        checkValues("MC SecondDerivative spline", f,
                CommonUtil.toArray(x),CommonUtil.toArray(y));
        check1stDerivativeValue("MC SecondDerivative spline", f,
                x.get(0), 4.0);
        check1stDerivativeValue("MC SecondDerivative spline", f,
                x.get(n-1), -4.0);
        check2ndDerivativeValue("SecondDerivative spline", f,
                x.get(0), -2.0);
        check2ndDerivativeValue("MC SecondDerivative spline", f,
                x.get(n-1), -2.0);
    }

    /* See R. L. Dougherty, A. Edelman, J. M. Hyman,
       "Nonnegativity-, Monotonicity-, or Convexity-Preserving CubicSpline and Quintic
       Hermite Interpolation"
       Mathematics Of Computation, v. 52, n. 186, April 1989, pp. 471-494.
    */
    @Test
    public void testNonRestrictiveHymanFilter() {
        System.out.println("Testing non-restrictive Hyman filter...");
        int n = 4;

        List<Double> x, y;
        x = xRange(-2.0, 2.0, n);
        y = parabolic(x);
        double zero=0.0, interpolated, expected=0.0;

        // MC Not-a-knot spline
        CubicInterpolation f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                NotAKnot, NULL_REAL,
                NotAKnot, NULL_REAL);
        f.update();
        interpolated = f.value(zero, false);
        assertFalse(Math.abs(interpolated-expected)>1e-15);

        // MC Clamped spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                FirstDerivative,  4.0,
                FirstDerivative, -4.0);
        f.update();
        interpolated = f.value(zero,false);
        assertFalse(Math.abs(interpolated-expected)>1e-15);

        // MC SecondDerivative spline
        f = new CubicInterpolation(CommonUtil.toArray(x),CommonUtil.toArray(y),
                Spline, true,
                SecondDerivative, -2.0,
                SecondDerivative, -2.0);
        f.update();
        interpolated = f.value(zero,false);
        assertFalse(Math.abs(interpolated-expected)>1e-15);
    }
    @Test
    public void testMultiSpline() {
        System.out.println("Testing N-dimensional cubic spline...");
        List<Integer> dim = CommonUtil.ArrayInit(5);
        dim.set(0, 6);
        dim.set(1, 5);
        dim.set(2, 5);
        dim.set(3, 6);
        dim.set(4, 4);

        List<Double> args = CommonUtil.ArrayInit(5);
        List<Double> offsets = CommonUtil.ArrayInit(5);
        offsets.set(0, 1.005); offsets.set(1, 14.0); offsets.set(2, 33.005);
        offsets.set(3, 35.025); offsets.set(4, 19.025);

        double s = offsets.get(0);
        args.set(0, s);
        double t = offsets.get(1);
        args.set(1, t);
        double u = offsets.get(2);
        args.set(2, u);
        double v = offsets.get(3);
        args.set(3, v);
        double w = offsets.get(4);
        args.set(4, w);

        int i, j, k, l, m;

        List<List<Double>> grid = new ArrayList<>();
        for (i = 0; i < 5; ++i) {
            grid.add(new ArrayList<>());
        }

        double r = 0.15;

        for (i = 0; i < 5; ++i) {
            double temp = offsets.get(i);
            for (j = 0; j < dim.get(i); temp += r, ++j)
                grid.get(i).add(temp);
        }
    }

    @Test
    public void testAsFunctor() {
        System.out.println("Testing use of interpolations as functors...");
        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[] y = { 5.0, 4.0, 3.0, 2.0, 1.0 };

        Interpolation f = new LinearInterpolation(x,y);
        f.update();

        double[] x2 = { -2.0, -1.0, 0.0, 1.0, 3.0, 4.0, 5.0, 6.0, 7.0 };
        int N = x2.length;
        List<Double> y2 = CommonUtil.ArrayInit(N);
        double tolerance = 1.0e-12;

        // case 1: extrapolation not allowed
        try {
            for (int i = 0; i < x2.length; i++) {
                y2.set(i, f.value(x2[i], false));
            }
        } catch (Exception e) {
            System.out.println("failed to throw exception when trying to extrapolate");
            // QL_FAIL("failed to throw exception when trying to extrapolate");
            // as expected; do nothing
        }

        // case 2: enable extrapolation
        f.enableExtrapolation();
        y2 = CommonUtil.ArrayInit(N);
        for (int i = 0; i < x2.length; i++) {
            y2.set(i, f.value(x2[i], false));
        }
        for (int i=0; i<N; i++) {
            double expected = 5.0-x2[i];
            assertFalse(Math.abs(y2.get(i)-expected) > tolerance);
        }
    }

    @Test
    public void testFritschButland() {
        System.out.println("Testing Fritsch-Butland interpolation...");
        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[][] y = {{ 1.0, 2.0, 1.0, 1.0, 2.0 },
            { 1.0, 2.0, 1.0, 1.0, 1.0 },
            { 2.0, 1.0, 0.0, 2.0, 3.0 }};

        for (int i=0; i<3; ++i) {

            Interpolation f = new FritschButlandCubic(x, y[i]);
            f.update();

            for (int j=0; j<4; ++j) {
                double left_knot = x[j];
                int expected_sign = sign(y[i][j], y[i][j+1]);
                for (int k=0; k<10; ++k) {
                    double x1 = left_knot + k*0.1, x2 = left_knot + (k+1)*0.1;
                    double y1 = f.value(x1,false), y2 = f.value(x2, false);
                    assertFalse(Double.isNaN(y1));
                    assertFalse(sign(y1, y2) != expected_sign);
                }
            }
        }
    }
    @Test
    public void testBackwardFlat() {
        System.out.println("Testing backward-flat interpolation...");
        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[] y = { 5.0, 4.0, 3.0, 2.0, 1.0 };

        Interpolation f = new BackwardFlatInterpolation(x,y);
        f.update();

        int N = 5;
        int i;
        double tolerance = 1.0e-12;

        // at original points
        for (i=0; i<N; i++) {
            double p = x[i];
            double calculated = f.value(p, false);
            double expected = y[i];
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // at middle points
        for (i=0; i<N-1; i++) {
            double p = (x[i]+x[i+1])/2;
            double calculated = f.value(p, false);
            double expected = y[i+1];
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // outside the original range
        f.enableExtrapolation();

        double p = x[0] - 0.5;
        double calculated = f.value(p, false);
        double expected = y[0];
        assertFalse(Math.abs(expected-calculated) > tolerance);

        p = x[N-1] + 0.5;
        calculated = f.value(p,false);
        expected = y[N-1];
        assertFalse(Math.abs(expected-calculated) > tolerance);

        // primitive at original points
        calculated = f.primitive(x[0], false);
        expected = 0.0;
        assertFalse(Math.abs(expected-calculated) > tolerance);

        double sum = 0.0;
        for (i=1; i<N; i++) {
            sum += (x[i]-x[i-1])*y[i];
            calculated = f.primitive(x[i], false);
            expected = sum;
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // primitive at middle points
        sum = 0.0;
        for (i=0; i<N-1; i++) {
            p = (x[i]+x[i+1])/2;
            sum += (x[i+1]-x[i])*y[i+1]/2;
            calculated = f.primitive(p, false);
            expected = sum;
            sum += (x[i+1]-x[i])*y[i+1]/2;
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }
    }

    @Test
    public void estForwardFlat() {
        System.out.println("Testing forward-flat interpolation...");
        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[] y = { 5.0, 4.0, 3.0, 2.0, 1.0 };

        Interpolation f = new ForwardFlatInterpolation(x,y);
        f.update();

        int N = 5;
        int i;
        double tolerance = 1.0e-12;

        // at original points
        for (i=0; i<N; i++) {
            double p = x[i];
            double calculated = f.value(p, false);
            double expected = y[i];
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // at middle points
        for (i=0; i<N-1; i++) {
            double p = (x[i]+x[i+1])/2;
            double calculated = f.value(p, false);
            double expected = y[i];
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // outside the original range
        f.enableExtrapolation();

        double p = x[0] - 0.5;
        double calculated = f.value(p, false);
        double expected = y[0];
        assertFalse(Math.abs(expected-calculated) > tolerance);

        p = x[N-1] + 0.5;
        calculated = f.value(p, false);
        expected = y[N-1];
        assertFalse(Math.abs(expected-calculated) > tolerance);

        // primitive at original points
        calculated = f.primitive(x[0], false);
        expected = 0.0;
        assertFalse(Math.abs(expected-calculated) > tolerance);

        double sum = 0.0;
        for (i=1; i<N; i++) {
            sum += (x[i]-x[i-1])*y[i-1];
            calculated = f.primitive(x[i], false);
            expected = sum;
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }

        // primitive at middle points
        sum = 0.0;
        for (i=0; i<N-1; i++) {
            p = (x[i]+x[i+1])/2;
            sum += (x[i+1]-x[i])*y[i]/2;
            calculated = f.primitive(p, false);
            expected = sum;
            sum += (x[i+1]-x[i])*y[i]/2;
            assertFalse(Math.abs(expected-calculated) > tolerance);
        }
    }

//    @Test
//    public void testSabrInterpolation() {
//        System.out.println("Testing Sabr interpolation...");
//        double tolerance = 1.0e-12;
//        double[] strikes = new double[31];
//        double[] volatilities = new double[31];
//        // input strikes
//        strikes[0] = 0.03 ; strikes[1] = 0.032 ; strikes[2] = 0.034 ;
//        strikes[3] = 0.036 ; strikes[4] = 0.038 ; strikes[5] = 0.04 ;
//        strikes[6] = 0.042 ; strikes[7] = 0.044 ; strikes[8] = 0.046 ;
//        strikes[9] = 0.048 ; strikes[10] = 0.05 ; strikes[11] = 0.052 ;
//        strikes[12] = 0.054 ; strikes[13] = 0.056 ; strikes[14] = 0.058 ;
//        strikes[15] = 0.06 ; strikes[16] = 0.062 ; strikes[17] = 0.064 ;
//        strikes[18] = 0.066 ; strikes[19] = 0.068 ; strikes[20] = 0.07 ;
//        strikes[21] = 0.072 ; strikes[22] = 0.074 ; strikes[23] = 0.076 ;
//        strikes[24] = 0.078 ; strikes[25] = 0.08 ; strikes[26] = 0.082 ;
//        strikes[27] = 0.084 ; strikes[28] = 0.086 ; strikes[29] = 0.088;
//        strikes[30] = 0.09;
//        // input volatilities
//        volatilities[0] = 1.16725837321531 ; volatilities[1] = 1.15226075991385 ; volatilities[2] = 1.13829711098834 ;
//        volatilities[3] = 1.12524190877505 ; volatilities[4] = 1.11299079244474 ; volatilities[5] = 1.10145609357162 ;
//        volatilities[6] = 1.09056348513411 ; volatilities[7] = 1.08024942745106 ; volatilities[8] = 1.07045919457758 ;
//        volatilities[9] = 1.06114533019077 ; volatilities[10] = 1.05226642581503 ; volatilities[11] = 1.04378614411707 ;
//        volatilities[12] = 1.03567243073732 ; volatilities[13] = 1.0278968727451 ; volatilities[14] = 1.02043417226345 ;
//        volatilities[15] = 1.01326171139321 ; volatilities[16] = 1.00635919013311 ; volatilities[17] = 0.999708323124949 ;
//        volatilities[18] = 0.993292584155381 ; volatilities[19] = 0.987096989695393 ; volatilities[20] = 0.98110791455717 ;
//        volatilities[21] = 0.975312934134512 ; volatilities[22] = 0.969700688771689 ; volatilities[23] = 0.964260766651027;
//        volatilities[24] = 0.958983602256592 ; volatilities[25] = 0.953860388001395 ; volatilities[26] = 0.948882997029509 ;
//        volatilities[27] = 0.944043915545469 ; volatilities[28] = 0.939336183299237 ; volatilities[29] = 0.934753341079515 ;
//        volatilities[30] = 0.930289384251337;
//
//        double expiry = 1.0;
//        double forward = 0.039;
//        // input SABR coefficients (corresponding to the vols above)
//        double initialAlpha = 0.3;
//        double initialBeta = 0.6;
//        double initialNu = 0.02;
//        double initialRho = 0.01;
//        // calculate SABR vols and compare with input vols
//        for(int i=0; i< strikes.length; i++){
//            Real calculatedVol = Sarb.sabrVolatility(strikes[i], forward, expiry,
//                    initialAlpha, initialBeta,
//                    initialNu, initialRho);
//            if (std::fabs(volatilities[i]-calculatedVol) > tolerance)
//            BOOST_ERROR(
//                    "failed to calculate Sabr function at strike " << strikes[i]
//                            << "\n    expected:   " << volatilities[i]
//                            << "\n    calculated: " << calculatedVol
//                            << "\n    error:      " << std::fabs(calculatedVol-volatilities[i]));
//        }
//
//        // Test SABR calibration against input parameters
//        Real alphaGuess = std::sqrt(0.2);
//        Real betaGuess = 0.5;
//        Real nuGuess = std::sqrt(0.4);
//        Real rhoGuess = 0.0;
//
//    const bool vegaWeighted[]= {true, false};
//    const bool isAlphaFixed[]= {true, false};
//    const bool isBetaFixed[]= {true, false};
//    const bool isNuFixed[]= {true, false};
//    const bool isRhoFixed[]= {true, false};
//
//        Real calibrationTolerance = 5.0e-8;
//        // initialize optimization methods
//        std::vector<ext::shared_ptr<OptimizationMethod>> methods_ = {
//                ext::shared_ptr<OptimizationMethod>(new Simplex(0.01)),
//                ext::shared_ptr<OptimizationMethod>(new LevenbergMarquardt(1e-8, 1e-8, 1e-8))
//        };
//        // Initialize end criteria
//        ext::shared_ptr<EndCriteria> endCriteria(new
//                EndCriteria(100000, 100, 1e-8, 1e-8, 1e-8));
//        // Test looping over all possibilities
//        for (auto& method : methods_) {
//            for (bool i : vegaWeighted) {
//                for (bool k_a : isAlphaFixed) {
//                    for (bool k_b : isBetaFixed) {
//                        for (bool k_n : isNuFixed) {
//                            for (bool k_r : isRhoFixed) {
//                                // to meet the tough calibration tolerance we need to lower the default
//                                // error threshold for accepting a calibration (to be more specific,
//                                // some of the new test cases arising from fixing a subset of the
//                                // model's parameters do not calibrate with the desired error using the
//                                // initial guess (i.e. optimization runs into a local minimum) - then a
//                                // series of random start values for optimization is chosen until our
//                                // tight custom error threshold is satisfied.
//                                SABRInterpolation sabrInterpolation(
//                                        strikes.begin(), strikes.end(), volatilities.begin(), expiry,
//                                        forward, k_a ? initialAlpha : alphaGuess,
//                                        k_b ? initialBeta : betaGuess, k_n ? initialNu : nuGuess,
//                                        k_r ? initialRho : rhoGuess, k_a, k_b, k_n, k_r, i, endCriteria,
//                                        method, 1E-10);
//                                sabrInterpolation.update();
//
//                                // Recover SABR calibration parameters
//                                bool failed = false;
//                                Real calibratedAlpha = sabrInterpolation.alpha();
//                                Real calibratedBeta = sabrInterpolation.beta();
//                                Real calibratedNu = sabrInterpolation.nu();
//                                Real calibratedRho = sabrInterpolation.rho();
//                                Real error;
//
//                                // compare results: alpha
//                                error = std::fabs(initialAlpha - calibratedAlpha);
//                                if (error > calibrationTolerance) {
//                                    BOOST_ERROR("\nfailed to calibrate alpha Sabr parameter:"
//                                            << "\n    expected:        " << initialAlpha
//                                            << "\n    calibrated:      " << calibratedAlpha
//                                            << "\n    error:           " << error);
//                                    failed = true;
//                                }
//                                // Beta
//                                error = std::fabs(initialBeta - calibratedBeta);
//                                if (error > calibrationTolerance) {
//                                    BOOST_ERROR("\nfailed to calibrate beta Sabr parameter:"
//                                            << "\n    expected:        " << initialBeta
//                                            << "\n    calibrated:      " << calibratedBeta
//                                            << "\n    error:           " << error);
//                                    failed = true;
//                                }
//                                // Nu
//                                error = std::fabs(initialNu - calibratedNu);
//                                if (error > calibrationTolerance) {
//                                    BOOST_ERROR("\nfailed to calibrate nu Sabr parameter:"
//                                            << "\n    expected:        " << initialNu
//                                            << "\n    calibrated:      " << calibratedNu
//                                            << "\n    error:           " << error);
//                                    failed = true;
//                                }
//                                // Rho
//                                error = std::fabs(initialRho - calibratedRho);
//                                if (error > calibrationTolerance) {
//                                    BOOST_ERROR("\nfailed to calibrate rho Sabr parameter:"
//                                            << "\n    expected:        " << initialRho
//                                            << "\n    calibrated:      " << calibratedRho
//                                            << "\n    error:           " << error);
//                                    failed = true;
//                                }
//
//                                if (failed)
//                                    BOOST_FAIL("\nSabr calibration failure:"
//                                            << "\n    isAlphaFixed:    " << k_a
//                                            << "\n    isBetaFixed:     " << k_b
//                                            << "\n    isNuFixed:       " << k_n
//                                            << "\n    isRhoFixed:      " << k_r
//                                            << "\n    vegaWeighted[i]: " << i);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
}
