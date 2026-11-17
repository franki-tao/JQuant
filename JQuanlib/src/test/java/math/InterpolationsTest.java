package math;

import jquant.math.Array;
import jquant.math.BSpline;
import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.math.GaussianKernel;
import jquant.math.Interpolation;
import jquant.math.LaplaceInterpolation;
import jquant.math.Matrix;
import jquant.math.RichardsonExtrapolation;
import jquant.math.integrals.SimpsonIntegral;
import jquant.math.interpolations.*;
import jquant.experimental.volatility.NoArbSabrInterpolation;
import jquant.experimental.volatility.NoArbSabrModel;
import jquant.experimental.volatility.NoArbSabrSmileSection;
import jquant.experimental.volatility.NoArbSabrSpecs;
import jquant.math.interpolations.impl.MixedInterpolationImpl;
import jquant.math.interpolations.impl.SABRSpecs;
import jquant.math.randomnumbers.HaltonRsg;
import jquant.math.randomnumbers.SobolRsg;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Simplex;
import jquant.termstructures.volatility.Sarb;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;
import static jquant.math.MathUtils.close_enough;
import static jquant.math.MathUtils.squared;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.*;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class InterpolationsTest {
    // Official testInterpolateWithoutUpdate is intentionally not ported.
    // It exercises the C++ factory `interpolateWithoutUpdate` and the old
    // versus new `interpolate(..., update)` API of the C++ Interpolation
    // framework; JQuant constructors do not expose such an update argument.
    // No fake equivalent is provided.

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

        int[] dim = {6, 5, 5, 6, 4};
        double[] offsets = {1.005, 14.0, 33.005, 35.025, 19.025};

        int i, j, k, l, m;

        List<List<Double>> grid = new ArrayList<>();
        for (i = 0; i < 5; ++i) {
            grid.add(new ArrayList<>());
        }

        double r = 0.15;

        for (i = 0; i < 5; ++i) {
            double temp = offsets[i];
            for (j = 0; j < dim[i]; temp += r, ++j)
                grid.get(i).add(temp);
        }

        // row-major layout, the last dimension varies fastest
        int[] spacing = new int[5];
        spacing[4] = 1;
        for (i = 3; i >= 0; --i)
            spacing[i] = spacing[i + 1] * dim[i + 1];

        double[] y5 = new double[dim[0] * dim[1] * dim[2] * dim[3] * dim[4]];
        for (i = 0; i < dim[0]; ++i)
            for (j = 0; j < dim[1]; ++j)
                for (k = 0; k < dim[2]; ++k)
                    for (l = 0; l < dim[3]; ++l)
                        for (m = 0; m < dim[4]; ++m)
                            y5[i * spacing[0] + j * spacing[1] + k * spacing[2]
                                    + l * spacing[3] + m] =
                                    multif(grid.get(0).get(i), grid.get(1).get(j),
                                            grid.get(2).get(k), grid.get(3).get(l),
                                            grid.get(4).get(m));

        MultiCubicSpline cs = new MultiCubicSpline(grid, y5);

        double[] args = new double[5];

        for (i = 1; i < dim[0] - 1; ++i)
            for (j = 1; j < dim[1] - 1; ++j)
                for (k = 1; k < dim[2] - 1; ++k)
                    for (l = 1; l < dim[3] - 1; ++l)
                        for (m = 1; m < dim[4] - 1; ++m) {
                            args[0] = grid.get(0).get(i);
                            args[1] = grid.get(1).get(j);
                            args[2] = grid.get(2).get(k);
                            args[3] = grid.get(3).get(l);
                            args[4] = grid.get(4).get(m);
                            double interpolated = cs.value(args);
                            double expected = y5[i * spacing[0] + j * spacing[1]
                                    + k * spacing[2] + l * spacing[3] + m];
                            double error = Math.abs(interpolated - expected);
                            double tolerance = 1e-16;
                            assertFalse(error > tolerance,
                                    "failed to reproduce the original values at ("
                                            + args[0] + "," + args[1] + "," + args[2]
                                            + "," + args[3] + "," + args[4] + ")"
                                            + "\n    interpolated: " + interpolated
                                            + "\n    actual value: " + expected
                                            + "\n       error: " + error
                                            + "\n    tolerance: " + tolerance);
                        }

        long seed = 42;
        SobolRsg rsg = new SobolRsg(5, seed,
                SobolRsg.DirectionIntegers.Jaeckel, true);

        double tolerance = 1.7e-4;
        // actually tested up to 2^21-1=2097151 Sobol draws
        for (i = 0; i < 1023; ++i) {
            List<Double> next = rsg.nextSequence().value;
            for (j = 0; j < 5; ++j) {
                args[j] = grid.get(j).get(0)
                        + next.get(j) * (grid.get(j).get(dim[j] - 1) - grid.get(j).get(0));
            }
            double interpolated = cs.value(args);
            double expected = multif(args[0], args[1], args[2], args[3], args[4]);
            double error = Math.abs(interpolated - expected);
            assertFalse(error > tolerance,
                    "failed to reproduce the original values at ("
                            + args[0] + "," + args[1] + "," + args[2]
                            + "," + args[3] + "," + args[4] + ")"
                            + "\n    interpolated: " + interpolated
                            + "\n    actual value: " + expected
                            + "\n       error: " + error
                            + "\n    tolerance: " + tolerance);
        }
    }

    @Test
    public void testLaplaceInterpolation() {
        System.out.println("Testing Laplace interpolation...");

        double tol = 1E-12;
        double na = NULL_REAL;

        // full matrix

        Matrix m1 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m1);

        checkClose(m1.get(0, 0), 1.0, tol, "m1(0,0)");
        checkClose(m1.get(0, 2), 4.0, tol, "m1(0,2)");
        checkClose(m1.get(2, 0), 5.0, tol, "m1(2,0)");
        checkClose(m1.get(2, 1), 3.0, tol, "m1(2,1)");

        // inner point

        Matrix m2 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, na, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m2);

        checkClose(m2.get(1, 1), 4.5, tol, "m2(1,1)");
        checkClose(m2.get(2, 1), 3.0, tol, "m2(2,1)");

        // boundaries

        Matrix m3 = new Matrix(new double[][]{
                {1.0, na, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m3);

        checkClose(m3.get(0, 1), 2.5, tol, "m3(0,1)");

        Matrix m4 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {na, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m4);

        checkClose(m4.get(1, 0), 3.0, tol, "m4(1,0)");

        Matrix m5 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, na, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m5);

        checkClose(m5.get(2, 1), 3.5, tol, "m5(2,1)");

        Matrix m6 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, na},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m6);

        checkClose(m6.get(1, 2), 3.0, tol, "m6(1,2)");

        // corners

        Matrix m7 = new Matrix(new double[][]{
                {na, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m7);

        checkClose(m7.get(0, 0), 4.0, tol, "m7(0,0)");

        Matrix m8 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {na, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m8);

        checkClose(m8.get(2, 0), 4.5, tol, "m8(2,0)");

        Matrix m9 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, na},
        });

        LaplaceInterpolation.laplaceInterpolation(m9);

        checkClose(m9.get(2, 2), 5.0, tol, "m9(2,2)");

        Matrix m10 = new Matrix(new double[][]{
                {1.0, 2.0, na},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m10);

        checkClose(m10.get(0, 2), 4.5, tol, "m10(0,2)");

        // one dim (col vector)

        Matrix m20 = new Matrix(new double[][]{
                {na}, {na}, {3.0}, {5.0}, {7.0}, {na},
        });

        LaplaceInterpolation.laplaceInterpolation(m20);

        checkClose(m20.get(0, 0), 3.0, tol, "m20(0,0)");
        checkClose(m20.get(1, 0), 3.0, tol, "m20(1,0)");
        checkClose(m20.get(5, 0), 7.0, tol, "m20(5,0)");

        // one dim (row vector)

        Matrix m21 = new Matrix(new double[][]{
                {na, na, 3.0, 5.0, 7.0, na},
        });

        LaplaceInterpolation.laplaceInterpolation(m21);

        checkClose(m21.get(0, 0), 3.0, tol, "m21(0,0)");
        checkClose(m21.get(0, 1), 3.0, tol, "m21(0,1)");
        checkClose(m21.get(0, 5), 7.0, tol, "m21(0,5)");

        // non equidistant grid, inner point

        Matrix m30 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, na, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m30,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});

        checkClose(m30.get(1, 1), 26.0 / 6.0, tol, "m30(1,1)");

        // non equidistant grid, boundaries

        Matrix m31 = new Matrix(new double[][]{
                {1.0, na, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m31,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});

        checkClose(m31.get(0, 1), 6.0 / 3.0, tol, "m31(0,1)");

        Matrix m32 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {na, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m32,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});

        checkClose(m32.get(1, 0), 7.0 / 3.0, tol, "m32(1,0)");

        Matrix m33 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, na, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m33,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});

        checkClose(m33.get(2, 1), 12.0 / 3.0, tol, "m33(2,1)");

        Matrix m34 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, na},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m34,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});

        checkClose(m34.get(1, 2), 10.0 / 3.0, tol, "m34(1,2)");

        // non equidistant grid, corners

        Matrix m35 = new Matrix(new double[][]{
                {na, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m35,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});

        checkClose(m35.get(0, 0), 10.0 / 3.0, tol, "m35(0,0)");

        Matrix m36 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {na, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m36,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});

        checkClose(m36.get(2, 0), 18.0 / 5.0, tol, "m36(2,0)");

        Matrix m37 = new Matrix(new double[][]{
                {1.0, 2.0, 4.0},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, na},
        });

        LaplaceInterpolation.laplaceInterpolation(m37,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});

        checkClose(m37.get(2, 2), 13.0 / 3.0, tol, "m37(2,2)");

        Matrix m38 = new Matrix(new double[][]{
                {1.0, 2.0, na},
                {6.0, 6.5, 7.0},
                {5.0, 3.0, 2.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m38,
                new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 3.0});

        checkClose(m38.get(0, 2), 16.0 / 3.0, tol, "m38(0,2)");

        // single point with given value

        Matrix m50 = new Matrix(new double[][]{
                {1.0},
        });

        LaplaceInterpolation.laplaceInterpolation(m50);

        checkClose(m50.get(0, 0), 1.0, tol, "m50(0,0)");

        // single point with missing value

        Matrix m51 = new Matrix(new double[][]{
                {na},
        });

        LaplaceInterpolation.laplaceInterpolation(m51);

        checkClose(m51.get(0, 0), 0.0, tol, "m51(0,0)");

        // no point

        LaplaceInterpolation l0 = new LaplaceInterpolation(
                coordinates -> NULL_REAL, new ArrayList<>());
        checkClose(l0.value(new int[0]), 0.0, tol, "l0");

        // single test cases from actual issues observed in the field

        double[] tx = {0.0849315, 0.257534, 0.509589, 1.00548, 2.00274, 3.00274, 4.00274,
                5.00548, 7.00822, 10.0082, 15.011, 20.0137, 30.0219, 70.0493};
        double[] ty = {0.25, 1, 2, 3, 4, 5, 7, 10, 15, 20, 30, 100};
        Matrix m52 = new Matrix(new double[][]{
                {na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na},
                {na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na, na},
                {na, na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na, na},
                {na, na, na, na, na, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, na, na},
                {na, na, na, na, na, na, na, na, na, na, na, na, na, na},
        });

        // we need to allow for more iterations to achieve the desired accuracy
        LaplaceInterpolation.laplaceInterpolation(m52, tx, ty, 1E-6, 100);

        for (int i = 0; i < m52.rows(); ++i) {
            for (int j = 0; j < m52.cols(); ++j) {
                checkClose(m52.get(i, j), 1.0, 0.1,
                        "m52(" + i + "," + j + ")");
            }
        }
    }

    @Test
    public void testTransformations() {
        System.out.println("Testing Sabr and no-arbitrage Sabr transformation functions...");

        // Both official halves of testTransformations are now present: the
        // SABR transformations and the no-arbitrage SABR transformations.
        double size = 25.0; // test inputs from [-size,size]^4

        int N = 100000;

        List<Boolean> fixed = new ArrayList<>();
        List<Double> params = new ArrayList<>();
        for (int j = 0; j < 4; ++j) {
            fixed.add(false);
            params.add(0.0);
        }
        double forward = 0.03;

        HaltonRsg h = new HaltonRsg(4, 42, false, false);
        SABRSpecs sabrSpecs = new SABRSpecs();
        NoArbSabrSpecs noArbSabrSpecs = new NoArbSabrSpecs();

        for (int iter = 0; iter < 1_000_000; ++iter) {
            List<Double> s = h.nextSequence().value;
            Array x = new Array(4);
            for (int j = 0; j < 4; ++j)
                x.set(j, 2.0 * size * s.get(j) - size);

            // sabr
            Array y = sabrSpecs.direct(x, fixed, params, forward);
            Sarb.validateSabrParameters(y.get(0), y.get(1), y.get(2), y.get(3));
            Array z = sabrSpecs.inverse(y, fixed, params, forward);
            z = sabrSpecs.direct(z, fixed, params, forward);
            assertTrue(close(z.get(0), y.get(0), N)
                            && close(z.get(1), y.get(1), N)
                            && close(z.get(2), y.get(2), N)
                            && close(z.get(3), y.get(3), N),
                    "SabrInterpolation: direct(inverse("
                            + y.get(0) + "," + y.get(1) + "," + y.get(2) + ","
                            + y.get(3) + ")) = (" + z.get(0) + "," + z.get(1) + ","
                            + z.get(2) + "," + z.get(3) + "), difference is ("
                            + (z.get(0) - y.get(0)) + "," + (z.get(1) - y.get(1))
                            + "," + (z.get(2) - y.get(2)) + ","
                            + (z.get(3) - y.get(3)) + ")");

            // noarb sabr
            y = noArbSabrSpecs.direct(x, fixed, params, forward);

            // we can not invoke the constructor, this would be too slow, so
            // we copy the parameter check here ...
            double alpha = y.get(0);
            double beta = y.get(1);
            double nu = y.get(2);
            double rho = y.get(3);
            CommonUtil.QL_REQUIRE(beta >= NoArbSabrModel.beta_min &&
                            beta <= NoArbSabrModel.beta_max,
                    "beta (" + beta + ") out of bounds");
            double sigmaI = alpha * Math.pow(forward, beta - 1.0);
            CommonUtil.QL_REQUIRE(sigmaI >= NoArbSabrModel.sigmaI_min &&
                            sigmaI <= NoArbSabrModel.sigmaI_max,
                    "sigmaI = alpha*forward^(beta-1.0) (" + sigmaI +
                            ") out of bounds, alpha=" + alpha +
                            " beta=" + beta + " forward=" + forward);
            CommonUtil.QL_REQUIRE(nu >= NoArbSabrModel.nu_min &&
                            nu <= NoArbSabrModel.nu_max,
                    "nu (" + nu + ") out of bounds");
            CommonUtil.QL_REQUIRE(rho >= NoArbSabrModel.rho_min &&
                            rho <= NoArbSabrModel.rho_max,
                    "rho (" + rho + ") out of bounds");

            z = noArbSabrSpecs.inverse(y, fixed, params, forward);
            z = noArbSabrSpecs.direct(z, fixed, params, forward);
            assertTrue(close(z.get(0), y.get(0), N)
                            && close(z.get(1), y.get(1), N)
                            && close(z.get(2), y.get(2), N)
                            && close(z.get(3), y.get(3), N),
                    "NoArbSabrInterpolation: direct(inverse("
                            + y.get(0) + "," + y.get(1) + "," + y.get(2) + ","
                            + y.get(3) + ")) = (" + z.get(0) + "," + z.get(1) + ","
                            + z.get(2) + "," + z.get(3) + "), difference is ("
                            + (z.get(0) - y.get(0)) + "," + (z.get(1) - y.get(1))
                            + "," + (z.get(2) - y.get(2)) + ","
                            + (z.get(3) - y.get(3)) + ")");
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
            org.junit.jupiter.api.Assertions.fail(
                    "failed to throw exception when trying to extrapolate");
        } catch (IllegalArgumentException e) {
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
    public void testForwardFlat() {
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

    // BOOST_CHECK_CLOSE / QL_CHECK_CLOSE: the tolerance is a percentage.
    private void checkClose(double calculated, double expected, double percentTolerance,
                            String message) {
        double relativeError = Math.abs(calculated - expected) / Math.abs(expected);
        assertFalse(relativeError > percentTolerance / 100.0, message);
    }

    @Test
    public void testKrugerZeroSlope() {
        System.out.println("Testing Kruger interpolation next to zero slopes...");

        // a flat segment, and a -0 slope next to a +0 one
        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[][] y = {{ 1.0, 1.0, 1.0, 2.0, 3.0 },
                        { 0.0, -0.0, 0.0, 1.0, 2.0 }};

        for (double[] yi : y) {
            Interpolation f = new KrugerCubic(x, yi);
            for (int k = 0; k <= 40; ++k) {
                double xk = k * 0.1;
                assertFalse(Double.isNaN(f.value(xk, false)),
                        "NaN detected at x = " + xk);
                assertFalse(Double.isNaN(f.derivative(xk, false)),
                        "NaN detected at x = " + xk);
            }
            assertFalse(f.derivative(1.0, false) != 0.0,
                    "nonzero derivative " + f.derivative(1.0, false)
                            + " at a zero slope");
        }
    }

    @Test
    public void testAkimaWithFewPoints() {
        System.out.println("Testing Akima interpolation with few points...");

        // The Akima scheme reads S_[2] and S_[n-4] while computing the
        // first-derivative estimates; with three points S_ has size two and
        // both indices are out of bounds. Construction must fail cleanly
        // rather than read past the end of the vector.
        double[] x3 = { 0.0, 1.0, 2.0 };
        double[] y3 = { 1.0, 2.0, 0.5 };
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AkimaCubicInterpolation(x3, y3));
        assertTrue(ex.getMessage().contains(
                        "Akima approximation requires at least 4 points"),
                "unexpected error message: " + ex.getMessage());

        // Four points are enough; the interpolation must reproduce the knots.
        double[] x4 = { 0.0, 1.0, 2.0, 3.0 };
        double[] y4 = { 1.0, 2.0, 0.5, 1.5 };
        AkimaCubicInterpolation f = new AkimaCubicInterpolation(x4, y4);
        for (int i = 0; i < 4; ++i)
            checkClose(f.value(x4[i], false), y4[i], 1.0e-12,
                    "failed to reproduce Akima knot " + i);
    }

    @Test
    public void testNotAKnotWithFewPoints() {
        System.out.println("Testing not-a-knot spline with few points...");

        // The not-a-knot conditions read dx_[1], S_[1] and dx_[n-3]; with two
        // points dx_ and S_ have size one and these indices are out of bounds.
        // Construction must fail cleanly rather than read past the end.
        double[] x2 = { 1.0, 2.0 };
        double[] y2 = { 1.0, 3.0 };
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CubicInterpolation(x2, y2, Spline, false,
                        SecondDerivative, 0.0, NotAKnot, NULL_REAL));
        assertTrue(ex.getMessage().contains(
                        "not-a-knot boundary condition requires at least 3 points"),
                "unexpected error message: " + ex.getMessage());

        // With three points, not-a-knot at both ends gives a singular system.
        double[] x3 = { 0.0, 1.0, 2.0 };
        double[] y3 = { 1.0, 2.0, 0.5 };
        ex = assertThrows(IllegalArgumentException.class,
                () -> new CubicInterpolation(x3, y3, Spline, false,
                        NotAKnot, NULL_REAL, NotAKnot, NULL_REAL));
        assertTrue(ex.getMessage().contains(
                        "not-a-knot boundary condition requires at least 4 points"),
                "unexpected error message: " + ex.getMessage());

        // Three points are enough for not-a-knot at one end only...
        CubicInterpolation f = new CubicInterpolation(x3, y3, Spline, false,
                NotAKnot, NULL_REAL, SecondDerivative, 0.0);
        for (int i = 0; i < 3; ++i)
            checkClose(f.value(x3[i], false), y3[i], 1.0e-12,
                    "failed to reproduce not-a-knot knot " + i);

        // ...and four for not-a-knot at both ends.
        double[] x4 = { 0.0, 1.0, 2.0, 3.0 };
        double[] y4 = { 1.0, 2.0, 0.5, 1.5 };
        CubicInterpolation g = new CubicInterpolation(x4, y4, Spline, false,
                NotAKnot, NULL_REAL, NotAKnot, NULL_REAL);
        for (int i = 0; i < 4; ++i)
            checkClose(g.value(x4[i], false), y4[i], 1.0e-12,
                    "failed to reproduce not-a-knot knot " + i);
    }

    @Test
    public void testMixedLinearCubicMatchDerivatives() {
        System.out.println("Testing derivative matching of mixed linear-cubic interpolation...");
        int n = 6;
        int k = 2;

        List<Double> x = xRange(-2.0, 2.0, n);
        List<Double> y = parabolic(x);

        MixedLinearCubicInterpolation f = new MixedLinearCubicInterpolation(
                CommonUtil.toArray(x), CommonUtil.toArray(y),
                k, MixedInterpolationImpl.Behavior.SplitRanges,
                Spline, false,
                FirstDerivative, NULL_REAL,
                SecondDerivative, 0.0);

        double tolerance = 1.0e-12;
        double eps = tolerance / 10;

        double leftSide = f.derivative(x.get(k) - eps, false);
        double rightSide = f.derivative(x.get(k) + eps, false);
        assertFalse(Math.abs(leftSide - rightSide) > tolerance,
                "derivatives at the switch point do not match"
                        + "\n    left side:  " + leftSide
                        + "\n    right side: " + rightSide
                        + "\n    error:      " + Math.abs(leftSide - rightSide));
    }

    @Test
    public void testMixedLinearCubicSwitchPoint() {
        System.out.println("Testing switch point bounds of mixed linear/cubic interpolation...");

        int n = 5;
        List<Double> x = xRange(-2.0, 2.0, n);
        List<Double> y = parabolic(x);

        // a switch index equal to the number of points puts the switch
        // iterator one past the end of the x range, which used to be
        // dereferenced; it must be rejected instead
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new MixedLinearCubicNaturalSpline(
                        CommonUtil.toArray(x), CommonUtil.toArray(y), n));
        assertTrue(ex.getMessage().contains("n is too large"),
                "unexpected error message: " + ex.getMessage());

        // the largest valid switch index still reproduces the knots
        MixedLinearCubicNaturalSpline f = new MixedLinearCubicNaturalSpline(
                CommonUtil.toArray(x), CommonUtil.toArray(y), n - 1);
        for (int i = 0; i < n; ++i) {
            double interpolated = f.value(x.get(i), false);
            assertFalse(Math.abs(interpolated - y.get(i)) > 1e-12,
                    "failed to reproduce knot " + i
                            + "\n    expected:     " + y.get(i)
                            + "\n    interpolated: " + interpolated);
        }
    }

    @Test
    public void testKernelInterpolation() {
        System.out.println("Testing kernel 1D interpolation...");

        double[] deltaGrid = {0.10, 0.25, 0.50, 0.75, 0.90};

        double[][] yd = {
                {11.275, 11.125, 11.250, 11.825, 12.625},
                {16.025, 13.450, 11.350, 10.150, 10.075},
                {10.300, 9.6375, 9.2000, 9.1125, 9.4000}
        };
        double[] lambdaVec = {0.05, 0.50, 0.75, 1.65, 2.55};

        double tolerance = 2.0e-5;

        // Check that y-values at knots are exactly the feeded y-values,
        // irrespective of kernel parameters
        for (double lambda : lambdaVec) {
            GaussianKernel myKernel = new GaussianKernel(0, lambda);

            for (double[] currY : yd) {
                KernelInterpolation f = new KernelInterpolation(
                        deltaGrid, currY, myKernel);

                for (int dIt = 0; dIt < deltaGrid.length; ++dIt) {
                    double expectedVal = currY[dIt];
                    double calcVal = f.value(deltaGrid[dIt], false);
                    assertFalse(Math.abs(expectedVal - calcVal) > tolerance,
                            "Kernel interpolation failed at x = " + deltaGrid[dIt]
                                    + "\n    interpolated value: " + calcVal
                                    + "\n    expected value:     " + expectedVal
                                    + "\n    error:              "
                                    + Math.abs(expectedVal - calcVal));
                }
            }
        }

        double[] testDeltaGrid = {0.121, 0.279, 0.678, 0.790, 0.980};

        // Gaussian Kernel values for testDeltaGrid with a standard
        // deviation of 2.05 (the value is arbitrary.)
        double[][] ytd = {
                {11.23847, 11.12003, 11.58932, 11.99168, 13.29650},
                {15.55922, 13.11088, 10.41615, 10.05153, 10.50741},
                {10.17473, 9.557842, 9.09339, 9.149687, 9.779971}
        };

        GaussianKernel myKernel = new GaussianKernel(0, 2.05);

        for (int j = 0; j < ytd.length; ++j) {
            double[] currY = yd[j];
            double[] currTY = ytd[j];

            // Build interpolation according to original grid + y-values
            KernelInterpolation f = new KernelInterpolation(
                    deltaGrid, currY, myKernel);

            // test values at test Grid
            for (int dIt = 0; dIt < testDeltaGrid.length; ++dIt) {
                double expectedVal = currTY[dIt];
                f.enableExtrapolation(); // allow extrapolation

                double calcVal = f.value(testDeltaGrid[dIt], false);
                assertFalse(Math.abs(expectedVal - calcVal) > tolerance,
                        "Kernel interpolation failed at x = " + testDeltaGrid[dIt]
                                + "\n    interpolated value: " + calcVal
                                + "\n    expected value:     " + expectedVal
                                + "\n    error:              "
                                + Math.abs(expectedVal - calcVal));
            }
        }
    }

    @Test
    public void testKernelInterpolation2D() {
        System.out.println("Testing kernel 2D interpolation...");

        double mean = 0.0, var = 0.18;
        GaussianKernel myKernel = new GaussianKernel(mean, var);

        double[] xVec = new double[10];
        xVec[0] = 0.10; xVec[1] = 0.20; xVec[2] = 0.30; xVec[3] = 0.40;
        xVec[4] = 0.50; xVec[5] = 0.60; xVec[6] = 0.70; xVec[7] = 0.80;
        xVec[8] = 0.90; xVec[9] = 1.00;

        double[] yVec = new double[3];
        yVec[0] = 1.0; yVec[1] = 2.0; yVec[2] = 3.5;

        double[][] M = new double[xVec.length][yVec.length];

        M[0][0]=0.25; M[1][0]=0.24; M[2][0]=0.23; M[3][0]=0.20; M[4][0]=0.19;
        M[5][0]=0.20; M[6][0]=0.21; M[7][0]=0.22; M[8][0]=0.26; M[9][0]=0.29;

        M[0][1]=0.27; M[1][1]=0.26; M[2][1]=0.25; M[3][1]=0.22; M[4][1]=0.21;
        M[5][1]=0.22; M[6][1]=0.23; M[7][1]=0.24; M[8][1]=0.28; M[9][1]=0.31;

        M[0][2]=0.21; M[1][2]=0.22; M[2][2]=0.27; M[3][2]=0.29; M[4][2]=0.24;
        M[5][2]=0.28; M[6][2]=0.25; M[7][2]=0.22; M[8][2]=0.29; M[9][2]=0.30;

        KernelInterpolation2D kernel2D = new KernelInterpolation2D(
                xVec, yVec, M, myKernel);

        double tolerance = 1.0e-10;

        for (int i = 0; i < xVec.length; ++i) {
            for (int j = 0; j < yVec.length; ++j) {
                double calcVal = kernel2D.value(xVec[i], yVec[j], false);
                double expectedVal = M[i][j];

                assertFalse(Math.abs(expectedVal - calcVal) > tolerance,
                        "2D Kernel interpolation failed at x = " + xVec[i]
                                + ", y = " + yVec[j]
                                + "\n    interpolated value: " + calcVal
                                + "\n    expected value:     " + expectedVal
                                + "\n    error:              "
                                + Math.abs(expectedVal - calcVal));
            }
        }

        // alternative data set
        double[] xVec1 = new double[4];
        xVec1[0] = 80.0; xVec1[1] = 90.0; xVec1[2] = 100.0; xVec1[3] = 110.0;

        double[] yVec1 = new double[8];
        yVec1[0] = 0.5; yVec1[1] = 0.7; yVec1[2] = 1.0; yVec1[3] = 2.0;
        yVec1[4] = 3.5; yVec1[5] = 4.5; yVec1[6] = 5.5; yVec1[7] = 6.5;

        double[][] M1 = new double[xVec1.length][yVec1.length];
        M1[0][0]=10.25; M1[1][0]=12.24;M1[2][0]=14.23;M1[3][0]=17.20;
        M1[0][1]=12.25; M1[1][1]=15.24;M1[2][1]=16.23;M1[3][1]=16.20;
        M1[0][2]=12.25; M1[1][2]=13.24;M1[2][2]=13.23;M1[3][2]=17.20;
        M1[0][3]=13.25; M1[1][3]=15.24;M1[2][3]=12.23;M1[3][3]=19.20;
        M1[0][4]=14.25; M1[1][4]=16.24;M1[2][4]=13.23;M1[3][4]=12.20;
        M1[0][5]=15.25; M1[1][5]=17.24;M1[2][5]=14.23;M1[3][5]=12.20;
        M1[0][6]=16.25; M1[1][6]=13.24;M1[2][6]=15.23;M1[3][6]=10.20;
        M1[0][7]=14.25; M1[1][7]=14.24;M1[2][7]=16.23;M1[3][7]=19.20;

        // test with function pointer
        KernelInterpolation2D kernel2DEp = new KernelInterpolation2D(
                xVec1, yVec1, M1, this::epanechnikovKernel);

        for (int i = 0; i < xVec1.length; ++i) {
            for (int j = 0; j < yVec1.length; ++j) {
                double calcVal = kernel2DEp.value(xVec1[i], yVec1[j], false);
                double expectedVal = M1[i][j];

                assertFalse(Math.abs(expectedVal - calcVal) > tolerance,
                        "2D Epanechnkikov Kernel interpolation failed at x = "
                                + xVec1[i] + ", y = " + yVec1[j]
                                + "\n    interpolated value: " + calcVal
                                + "\n    expected value:     " + expectedVal
                                + "\n    error:              "
                                + Math.abs(expectedVal - calcVal));
            }
        }

        // test updating mechanism by changing initial variables
        xVec1[0] = 60.0; xVec1[1] = 95.0; xVec1[2] = 105.0; xVec1[3] = 135.0;

        yVec1[0] = 12.5; yVec1[1] = 13.7; yVec1[2] = 15.0; yVec1[3] = 19.0;
        yVec1[4] = 26.5; yVec1[5] = 27.5; yVec1[6] = 29.2; yVec1[7] = 36.5;

        kernel2DEp.update();

        for (int i = 0; i < xVec1.length; ++i) {
            for (int j = 0; j < yVec1.length; ++j) {
                double calcVal = kernel2DEp.value(xVec1[i], yVec1[j], false);
                double expectedVal = M1[i][j];

                assertFalse(Math.abs(expectedVal - calcVal) > tolerance,
                        "2D Epanechnkikov Kernel updated interpolation failed at x = "
                                + xVec1[i] + ", y = " + yVec1[j]
                                + "\n    interpolated value: " + calcVal
                                + "\n    expected value:     " + expectedVal
                                + "\n    error:              "
                                + Math.abs(expectedVal - calcVal));
            }
        }
    }

    @Test
    public void testBicubicDerivatives() {
        System.out.println("Testing bicubic spline derivatives...");

        int n = 100;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; ++i) {
            x[i] = y[i] = i / 20.0;
        }

        double[][] f = new double[n][n];
        for (int i = 0; i < n; ++i)
            for (int j = 0; j < n; ++j)
                f[i][j] = y[i] / 10 * Math.sin(x[j]) + Math.cos(y[i]);

        double tol = 0.005;
        BicubicSpline spline = new BicubicSpline(x, y, f);

        for (int i = 5; i < 95; i += 10) {
            for (int j = 5; j < 95; j += 10) {
                double f_x  = spline.derivativeX(x[j], y[i]);
                double f_xx = spline.secondDerivativeX(x[j], y[i]);
                double f_y  = spline.derivativeY(x[j], y[i]);
                double f_yy = spline.secondDerivativeY(x[j], y[i]);
                double f_xy = spline.derivativeXY(x[j], y[i]);

                assertFalse(Math.abs(f_x - y[i] / 10 * Math.cos(x[j])) > tol,
                        "Failed to reproduce f_x");
                assertFalse(Math.abs(f_xx + y[i] / 10 * Math.sin(x[j])) > tol,
                        "Failed to reproduce f_xx");
                assertFalse(Math.abs(f_y - (Math.sin(x[j]) / 10 - Math.sin(y[i]))) > tol,
                        "Failed to reproduce f_y");
                assertFalse(Math.abs(f_yy + Math.cos(y[i])) > tol,
                        "Failed to reproduce f_yy");
                assertFalse(Math.abs(f_xy - Math.cos(x[j]) / 10) > tol,
                        "Failed to reproduce f_xy");
            }
        }
    }

    @Test
    public void testBicubicUpdate() {
        System.out.println("Testing that bicubic splines actually update...");

        int N = 6;
        double[] x = new double[N], y = new double[N];
        for (int i = 0; i < N; ++i) {
            x[i] = y[i] = i * 0.2;
        }

        double[][] f = new double[N][N];
        for (int i = 0; i < N; ++i)
            for (int j = 0; j < N; ++j)
                f[i][j] = x[j] * (x[j] + y[i]);

        BicubicSpline spline = new BicubicSpline(x, y, f);

        double old_result = spline.value(x[2] + 0.1, y[4], false);

        // modify input matrix and update.
        f[4][3] += 1.0;
        spline.update();

        double new_result = spline.value(x[2] + 0.1, y[4], false);
        assertFalse(Math.abs(old_result - new_result) < 0.5,
                "Failed to update bicubic spline");
    }

    @Test
    public void testLagrangeInterpolation() {
        System.out.println("Testing Lagrange interpolation...");

        double[] x = {-1.0, -0.5, -0.25, 0.1, 0.4, 0.75, 0.96};
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; ++i)
            y[i] = lagrangeTestFct(x[i]);

        LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        // reference results are taken from R package pracma
        double[] references = {
                -0.5000000000000000,-0.5392414024347419,-0.5591485962711904,
                -0.5629199661387594,-0.5534414777017116,-0.5333043347921566,
                -0.5048221831582063,-0.4700478608272949,-0.4307896950846587,
                -0.3886273460669714,-0.3449271969711449,-0.3008572908782903,
                -0.2574018141928359,-0.2153751266968088,-0.1754353382192734,
                -0.1380974319209344,-0.1037459341938971,-0.0726471311765894,
                -0.0449608318838433,-0.0207516779521373,0.0000000000000000,
                0.0173877793964286,0.0315691961126723,0.0427562482700356,
                0.0512063534145595,0.0572137590808174,0.0611014067405497,
                0.0632132491361394,0.0639070209989264,0.0635474631523613,
                0.0625000000000000,0.0611248703983366,0.0597717119144768,
                0.0587745984686508,0.0584475313615655,0.0590803836865967,
                0.0609352981268212,0.0642435381368876,0.0692027925097279,
                0.0759749333281079,0.0846842273010179,0.0954160004849021,
                0.1082157563897290,0.1230887474699003,0.1400000000000001,
                0.1588747923353829,0.1795995865576031,0.2020234135046815,
                0.2259597111862140,0.2511886165833182,0.2774597108334206,
                0.3044952177998833,0.3319936560264689,0.3596339440766487,
                0.3870799592577457,0.4139855497299214,0.4400000000000001,
                0.4647739498001331,0.4879657663513030,0.5092483700116673,
                0.5283165133097421,0.5448945133624253,0.5587444376778583,
                0.5696747433431296,0.5775493695968156,0.5822972837863635,
                0.5839224807103117,0.5825144353453510,0.5782590089582251,
                0.5714498086024714,0.5625000000000000,0.5519545738075141,
                0.5405030652677689,0.5289927272456703,0.5184421566492137,
                0.5100553742352614,0.5052363578001620,0.5056040287552059,
                0.5130076920869246
        };

        double tol = 50 * QL_EPSILON;
        for (int i = 0; i < 79; ++i) {
            double xx = -1.0 + i * 0.025;
            double calculated = interpl.value(xx, false);
            if (Double.isNaN(calculated)
                    || Math.abs(references[i] - calculated) > tol) {
                fail("failed to reproduce the Lagrange interpolation"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + references[i]);
            }
        }
    }

    @Test
    public void testLagrangeInterpolationAtSupportPoint() {
        System.out.println(
                "Testing Lagrange interpolation at supporting points...");

        int n = 5;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; ++i) {
            x[i] = i / (double) n;
            y[i] = 1.0 / (1.0 - x[i]);
        }
        LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        double relTol = 5e-12;

        checkClose(interpl.value(x[0], false), y[0], relTol,
                "failed to reproduce the Lagrange interpolation at support point");

        Array updatedY = new Array(y);
        updatedY.set(0, 2.0);
        checkClose(interpl.value(updatedY, x[0]), updatedY.get(0), relTol,
                "failed to reproduce updated Lagrange interpolation at support point");

        for (int i = 1; i < n - 1; ++i) {
            for (double z = x[i] - 100 * QL_EPSILON;
                 z < x[i] + 100 * QL_EPSILON; z += 2 * QL_EPSILON) {
                double expected = 1.0 / (1.0 - x[i]);
                double calculated = interpl.value(z, false);

                if (Double.isNaN(calculated)
                        || Math.abs(expected - calculated) > relTol) {
                    fail("failed to reproduce the Lagrange interplation"
                            + "\n    x         : " + z
                            + "\n    calculated: " + calculated
                            + "\n    expected  : " + expected);
                }
            }
        }
    }

    @Test
    public void testLagrangeInterpolationDerivative() {
        System.out.println(
                "Testing Lagrange interpolation derivatives...");

        double[] x = new double[5], y = new double[5];
        x[0] = -1.0; y[0] = 2.0;
        x[1] = -0.3; y[1] = 3.0;
        x[2] =  0.1; y[2] = 6.0;
        x[3] =  0.3; y[3] = 3.0;
        x[4] =  0.9; y[4] = -1.0;

        LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        double eps = Math.sqrt(QL_EPSILON);
        for (double xx = -1.0; xx <= 0.9; xx += 0.01) {
            double calculated = interpl.derivative(xx, true);
            double expected = (interpl.value(xx + eps, true)
                    - interpl.value(xx - eps, true)) / (2 * eps);

            if (Double.isNaN(calculated)
                    || Math.abs(expected - calculated) > 25 * eps) {
                fail("failed to reproduce the Lagrange"
                        + " interplation derivative"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected);
            }
        }
    }

    @Test
    public void testLagrangeInterpolationOnChebyshevPoints() {
        System.out.println(
                "Testing Lagrange interpolation on Chebyshev nodes...");

        // Test example taken from
        // J.P. Berrut, L.N. Trefethen, Barycentric Lagrange Interpolation
        // https://people.maths.ox.ac.uk/trefethen/barycentric.pdf

        int n = 50;
        double[] x = new double[n + 1], y = new double[n + 1];
        for (int i = 0; i <= n; ++i) {
            // Chebyshev nodes
            x[i] = Math.cos((2 * i + 1) * M_PI / (2 * n + 2));
            y[i] = Math.exp(x[i]) / Math.cos(x[i]);
        }

        LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        double tol = 1e-13;
        double tolDeriv = 1e-11;

        for (double xx = -1.0; xx <= 1.0; xx += 0.03) {
            double calculated = interpl.value(xx, true);
            double expected = Math.exp(xx) / Math.cos(xx);

            double diff = Math.abs(expected - calculated);
            if (Double.isNaN(calculated) || diff > tol) {
                fail("failed to reproduce the Lagrange"
                        + " interpolation on Chebyshev nodes"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected
                        + "\n    difference: " + diff);
            }

            double calculatedDeriv = interpl.derivative(xx, true);
            double expectedDeriv = Math.exp(xx) * (Math.cos(xx) + Math.sin(xx))
                    / squared(Math.cos(xx));

            double diffDeriv = Math.abs(expectedDeriv - calculatedDeriv);
            if (Double.isNaN(calculatedDeriv) || diffDeriv > tolDeriv) {
                fail("failed to reproduce the Lagrange"
                        + " interpolation derivative on Chebyshev nodes"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculatedDeriv
                        + "\n    expected  : " + expectedDeriv
                        + "\n    difference: " + diffDeriv);
            }
        }
    }

    @Test
    public void testBSplines() {
        System.out.println("Testing B-Splines...");

        // reference values have been generate with the R package splines2
        // https://cran.r-project.org/web/packages/splines2/splines2.pdf

        List<Double> knots = List.of(-1.0, 0.5, 0.75, 1.2, 3.0, 4.0, 5.0);

        int p = 2;
        BSpline bspline = new BSpline(p, knots.size() - p - 2, knots);

        Object[][] referenceValues = {
                {0, -0.95, 9.5238095238e-04},
                {0, -0.01, 0.37337142857},
                {0, 0.49, 0.84575238095},
                {0, 1.21, 0.0},
                {1, 1.49, 0.562987654321},
                {1, 1.59, 0.490888888889},
                {2, 1.99, 0.62429409171},
                {3, 1.19, 0.0},
                {3, 1.99, 0.12382936508},
                {3, 3.59, 0.765914285714}
        };

        double tol = 1e-10;
        for (Object[] ref : referenceValues) {
            int idx = (Integer) ref[0];
            double xx = (Double) ref[1];
            double expected = (Double) ref[2];

            double calculated = bspline.value(idx, xx);

            if (Double.isNaN(calculated)
                    || Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce the B-Spline value"
                        + "\n    i         : " + idx
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected
                        + "\n    difference: " + Math.abs(calculated - expected)
                        + "\n    tolerance : " + tol);
            }
        }
    }

    @Test
    public void testBackwardFlatOnSinglePoint() {
        System.out.println("Testing piecewise constant interpolation on a "
                + "single point...");
        double[] knots = {1.0};
        double[] values = {2.5};

        Interpolation impl = new BackwardFlatInterpolation(knots, values);

        double[] x = { -1.0, 1.0, 2.0, 3.0 };

        for (double i : x) {
            double calculated = impl.value(i, true);
            double expected = values[0];

            assertTrue(close_enough(calculated, expected),
                    "failed to reproduce a piecewise constant "
                            + "interpolation on a single point "
                            + "\n   x         : " + i
                            + "\n   expected  : " + expected
                            + "\n   calculated: " + calculated);

            double expectedPrimitive = values[0] * (i - knots[0]);
            double calculatedPrimitive = impl.primitive(i, true);

            assertTrue(close_enough(calculatedPrimitive, expectedPrimitive),
                    "failed to reproduce primitive on a piecewise constant "
                            + "interpolation for a single point "
                            + "\n   x         : " + i
                            + "\n   expected  : " + expectedPrimitive
                            + "\n   calculated: " + calculatedPrimitive);
        }
    }

    @Test
    public void testChebyshevInterpolation() {
        System.out.println("Testing Chebyshev interpolation...");

        Function[] fcts = {
                x -> Math.sin(x),
                x -> Math.cos(x),
                x -> Math.exp(-x * x)
        };
        String[] fctNames = {"sin", "cos", "e^(-x*x)"};

        int[] nodes = {11, 20};
        double[] tols = {1e-5, 1e-11};

        for (int t = 0; t < nodes.length; ++t) {
            for (int f = 0; f < fcts.length; ++f) {
                ChebyshevInterpolation interp = new ChebyshevInterpolation(
                        nodes[t], fcts[f]);

                for (double xx = -0.99; xx < 1.0; xx += 0.01) {
                    double expected = fcts[f].value(xx);
                    double calculated = interp.value(xx, false);
                    double diff = Math.abs(expected - calculated);
                    double tol = tols[t];

                    if (Double.isNaN(calculated)
                            || Math.abs(calculated - expected) > tol) {
                        fail("failed to reproduce the Chebyshev interpolation values"
                                + "\n    x         : " + xx
                                + "\n    fct       : " + fctNames[f]
                                + "\n    calculated: " + calculated
                                + "\n    expected  : " + expected
                                + "\n    difference: " + diff
                                + "\n    tolerance : " + tol);
                    }
                }
            }
        }
    }

    @Test
    public void testChebyshevInterpolationOnNodes() {
        System.out.println("Testing Chebyshev interpolation on and around nodes...");

        double tol = 10 * QL_EPSILON;
        Function testFct = x -> Math.sin(x);

        int nrNodes = 7;

        for (ChebyshevInterpolation.PointsType pointType :
                new ChebyshevInterpolation.PointsType[]{
                        ChebyshevInterpolation.PointsType.FirstKind,
                        ChebyshevInterpolation.PointsType.SecondKind}) {

            Array nodes = ChebyshevInterpolation.nodes(nrNodes, pointType);
            Array y = nodes.transform(testFct);

            ChebyshevInterpolation interp = new ChebyshevInterpolation(y, pointType);
            for (int i = 0; i < nodes.size(); ++i) {
                double node = nodes.get(i);
                // test on Chebyshev node
                double expected = testFct.value(node);
                double calculated = interp.value(node, false);
                double diff = Math.abs(expected - calculated);

                if (diff > tol) {
                    fail("failed to reproduce the node values"
                            + "\n    node      : " + node
                            + "\n    calculated: " + calculated
                            + "\n    expected  : " + expected
                            + "\n    difference: " + diff
                            + "\n    tolerance : " + tol);
                }

                // check around Chebyshev node
                for (int k = -50; k < 50; ++k) {
                    double xx = node + k * QL_EPSILON;
                    double expectedAround = testFct.value(xx);
                    double calculatedAround = interp.value(xx, true);
                    double diffAround = Math.abs(expectedAround - calculatedAround);

                    if (diffAround > tol) {
                        fail("failed to reproduce values around nodes"
                                + "\n    node      : " + node
                                + "\n    epsilon   : " + (xx - node)
                                + "\n    calculated: " + calculatedAround
                                + "\n    expected  : " + expectedAround
                                + "\n    difference: " + diffAround
                                + "\n    tolerance : " + tol);
                    }
                }
            }
        }
    }

    @Test
    public void testChebyshevInterpolationUpdateY() {
        System.out.println("Testing Y update for Chebyshev interpolation...");

        Array y = new Array(new double[]{1, 4, 7, 4});
        ChebyshevInterpolation interp = new ChebyshevInterpolation(y);

        Array yd = new Array(new double[]{6, 4, 5, 6});
        interp.updateY(yd);

        double tol = 10 * QL_EPSILON;

        for (int i = 0; i < y.size(); ++i) {
            double expected = yd.get(i);
            double calculated = interp.value(interp.nodes().get(i), true);
            double diff = Math.abs(calculated - expected);

            if (diff > tol) {
                fail("failed to reproduce updated node values"
                        + "\n    node      : " + i
                        + "\n    expected  : " + expected
                        + "\n    calculated: " + calculated
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }
        }
    }

    @Test
    public void testUnknownRichardsonExtrapolation() {
        System.out.println("Testing Richardson extrapolation with "
                + "unknown order of convergence...");

        double stepSize = 0.01;

        double[][] testCases = {
                {1.0, 1.0}, {1.0, -1.0},
                {2.0, 0.25}, {2.0, -1.0},
                {3.0, 2.0}, {3.0, -0.5},
                {4.0, 1.0}, {4.0, 0.5}
        };

        for (double[] testCase : testCases) {
            RichardsonExtrapolation extrap = new RichardsonExtrapolation(
                    new GF(testCase[0], testCase[1]), stepSize);

            double calculated = extrap.value(4.0, 2.0);
            double diff = Math.abs(M_PI - calculated);

            double tol = Math.pow(stepSize, testCase[0] + 1);

            assertFalse(diff > tol,
                    "failed to reproduce Richardson extrapolation "
                            + " with unknown order of convergence");
        }

        double highOrder = new RichardsonExtrapolation(
                new GF(14.0, 1.0), 0.5).value(4.0, 2.0);
        assertFalse(Math.abs(highOrder - M_PI) > 1e-12,
                "failed to reproduce Richardson extrapolation "
                        + " with unknown order of convergence");

        assertThrows(Exception.class,
                () -> new RichardsonExtrapolation(new GF(16.0, 1.0), 0.5)
                        .value(4.0, 2.0),
                "Richardson extrapolation with order of"
                        + " convergence above 15 should throw exception");

        double limCosValue = new RichardsonExtrapolation(
                this::limCos, 0.01).value(4.0, 2.0);
        assertFalse(Math.abs(limCosValue + 1.0) > 1e-6,
                "failed to reproduce Richardson extrapolation "
                        + " with unknown order of convergence");
    }

    @Test
    public void testRichardsonExtrapolation() {
        System.out.println("Testing Richardson extrapolation...");

        /* example taken from
         * http://www.ipvs.uni-stuttgart.de/abteilungen/bv/lehre/
         *      lehrveranstaltungen/vorlesungen/WS0910/
         *      NSG_termine/dateien/Richardson.pdf
         */

        double stepSize = 0.1;
        double orderOfConvergence = 1.0;
        RichardsonExtrapolation extrap = new RichardsonExtrapolation(
                this::f, stepSize, orderOfConvergence);

        double tol = 0.00002;
        double expected = 2.71285;

        double scalingFactor = 2.0;
        double calculated = extrap.value(scalingFactor);

        assertFalse(Math.abs(expected - calculated) > tol,
                "failed to reproduce Richardson extrapolation");

        calculated = extrap.value();
        assertFalse(Math.abs(expected - calculated) > tol,
                "failed to reproduce Richardson extrapolation");

        expected = 2.721376;
        double scalingFactor2 = 4.0;
        calculated = extrap.value(scalingFactor2, scalingFactor);

        assertFalse(Math.abs(expected - calculated) > tol,
                "failed to reproduce Richardson extrapolation");
    }

    @Test
    public void testFlatExtrapolation() {
        System.out.println("Testing flat extrapolation of 1-D interpolation...");

        double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        double[] y = { 5.0, 3.0, 4.0, 2.0, 1.0 };
        int N = x.length;

        CubicInterpolation cubic = new CubicInterpolation(
                x, y,
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
        cubic.enableExtrapolation();

        FlatExtrapolator f = new FlatExtrapolator(cubic);

        double tolerance = 1.0e-12;

        // extrapolation not allowed by default
        assertThrows(IllegalArgumentException.class,
                () -> f.value(-1.0, false),
                "failed to throw when extrapolating without permission");

        f.enableExtrapolation();

        // in-range: must match underlying cubic at knots
        for (int i = 0; i < N; ++i) {
            double calculated = f.value(x[i], false);
            double expected = y[i];
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "in-range mismatch at x=" + x[i]
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // in-range at midpoints: must match underlying cubic
        for (int i = 0; i < N - 1; ++i) {
            double mid = (x[i] + x[i + 1]) / 2.0;
            double calculated = f.value(mid, false);
            double expected = cubic.value(mid, false);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "in-range mismatch at midpoint x=" + mid
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // below range: flat at y[0]
        for (double p : new double[]{ -2.0, -1.0, -0.01 }) {
            double calculated = f.value(p, false);
            double expected = y[0];
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "flat extrapolation below range failed at " + p
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // above range: flat at y[N-1]
        for (double p : new double[]{ 4.01, 5.0, 10.0 }) {
            double calculated = f.value(p, false);
            double expected = y[N - 1];
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "flat extrapolation above range failed at " + p
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // derivative outside range is zero
        double calc = f.derivative(-1.0, false);
        assertFalse(Math.abs(calc) > tolerance,
                "derivative should be zero below range"
                        + "\n    calculated: " + calc);
        calc = f.derivative(5.0, false);
        assertFalse(Math.abs(calc) > tolerance,
                "derivative should be zero above range"
                        + "\n    calculated: " + calc);

        // second derivative outside range is zero
        calc = f.secondDerivative(-1.0, false);
        assertFalse(Math.abs(calc) > tolerance,
                "second derivative should be zero below range"
                        + "\n    calculated: " + calc);
        calc = f.secondDerivative(5.0, false);
        assertFalse(Math.abs(calc) > tolerance,
                "second derivative should be zero above range"
                        + "\n    calculated: " + calc);

        // derivative inside range matches underlying
        for (double p : new double[]{ 0.5, 1.5, 2.5, 3.5 }) {
            double calculated = f.derivative(p, false);
            double expected = cubic.derivative(p, false);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "in-range derivative mismatch at x=" + p
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // second derivative inside range matches underlying
        for (double p : new double[]{ 0.5, 1.5, 2.5, 3.5 }) {
            double calculated = f.secondDerivative(p, false);
            double expected = cubic.secondDerivative(p, false);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "in-range second derivative mismatch at x=" + p
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // primitive below range
        {
            double calculated = f.primitive(-1.0, false);
            double expected = cubic.primitive(x[0], true) + y[0] * (-1.0 - x[0]);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "primitive below range mismatch at x=-1.0"
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // primitive above range
        {
            double calculated = f.primitive(5.0, false);
            double expected = cubic.primitive(x[N - 1], true)
                    + y[N - 1] * (5.0 - x[N - 1]);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "primitive above range mismatch at x=5.0"
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // primitive inside range matches underlying
        for (double p : new double[]{ 0.5, 1.5, 2.5, 3.5 }) {
            double calculated = f.primitive(p, false);
            double expected = cubic.primitive(p, false);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "in-range primitive mismatch at x=" + p
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }

        // xValues and yValues accessors
        List<Double> xs = f.xValues();
        List<Double> ys = f.yValues();
        assertFalse(xs.size() != N,
                "xValues size mismatch"
                        + "\n    expected: " + N
                        + "\n    got:      " + xs.size());
        assertFalse(ys.size() != N,
                "yValues size mismatch"
                        + "\n    expected: " + N
                        + "\n    got:      " + ys.size());
        for (int i = 0; i < N; ++i) {
            assertFalse(Math.abs(xs.get(i) - x[i]) > tolerance,
                    "xValues mismatch at index " + i
                            + "\n    expected: " + x[i]
                            + "\n    got:      " + xs.get(i));
            assertFalse(Math.abs(ys.get(i) - y[i]) > tolerance,
                    "yValues mismatch at index " + i
                            + "\n    expected: " + y[i]
                            + "\n    got:      " + ys.get(i));
        }

        // isInRange
        assertTrue(f.isInRange(2.0), "isInRange should return true for x=2.0");
        assertFalse(f.isInRange(-1.0), "isInRange should return false for x=-1.0");

        // update smoke test
        f.update();
        {
            double calculated = f.value(2.0, false);
            double expected = cubic.value(2.0, false);
            assertFalse(Math.abs(calculated - expected) > tolerance,
                    "mismatch after update at x=2.0"
                            + "\n    expected:   " + expected
                            + "\n    calculated: " + calculated
                            + "\n    error:      " + Math.abs(calculated - expected));
        }
    }

    @Test
    public void testSabrInterpolation() {
        System.out.println("Testing Sabr interpolation...");

        // Test SABR function against input volatilities
        double tolerance = 1.0e-12;
        double[] strikes = {
                0.03, 0.032, 0.034, 0.036, 0.038, 0.04, 0.042, 0.044,
                0.046, 0.048, 0.05, 0.052, 0.054, 0.056, 0.058, 0.06,
                0.062, 0.064, 0.066, 0.068, 0.07, 0.072, 0.074, 0.076,
                0.078, 0.08, 0.082, 0.084, 0.086, 0.088, 0.09};
        double[] volatilities = {
                1.16725837321531, 1.15226075991385, 1.13829711098834,
                1.12524190877505, 1.11299079244474, 1.10145609357162,
                1.09056348513411, 1.08024942745106, 1.07045919457758,
                1.06114533019077, 1.05226642581503, 1.04378614411707,
                1.03567243073732, 1.0278968727451, 1.02043417226345,
                1.01326171139321, 1.00635919013311, 0.999708323124949,
                0.993292584155381, 0.987096989695393, 0.98110791455717,
                0.975312934134512, 0.969700688771689, 0.964260766651027,
                0.958983602256592, 0.953860388001395, 0.948882997029509,
                0.944043915545469, 0.939336183299237, 0.934753341079515,
                0.930289384251337};

        double expiry = 1.0;
        double forward = 0.039;
        // input SABR coefficients (corresponding to the vols above)
        double initialAlpha = 0.3;
        double initialBeta = 0.6;
        double initialNu = 0.02;
        double initialRho = 0.01;
        // calculate SABR vols and compare with input vols
        for (int i = 0; i < strikes.length; i++) {
            double calculatedVol = Sarb.sabrVolatility(strikes[i], forward, expiry,
                    initialAlpha, initialBeta, initialNu, initialRho);
            assertFalse(Math.abs(volatilities[i] - calculatedVol) > tolerance,
                    "failed to calculate Sabr function at strike " + strikes[i]
                            + "\n    expected:   " + volatilities[i]
                            + "\n    calculated: " + calculatedVol
                            + "\n    error:      " + Math.abs(calculatedVol - volatilities[i]));
        }

        // Test SABR calibration against input parameters
        double alphaGuess = Math.sqrt(0.2);
        double betaGuess = 0.5;
        double nuGuess = Math.sqrt(0.4);
        double rhoGuess = 0.0;

        boolean[] vegaWeighted = {true, false};
        boolean[] isAlphaFixed = {true, false};
        boolean[] isBetaFixed = {true, false};
        boolean[] isNuFixed = {true, false};
        boolean[] isRhoFixed = {true, false};

        double calibrationTolerance = 5.0e-8;
        // initialize optimization methods
        OptimizationMethod[] methods = {
                new Simplex(0.01),
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8)
        };
        // Initialize end criteria
        EndCriteria endCriteria = new EndCriteria(100000, 100, 1e-8, 1e-8, 1e-8);
        // Test looping over all possibilities
        for (OptimizationMethod method : methods) {
            for (boolean i : vegaWeighted) {
                for (boolean k_a : isAlphaFixed) {
                    for (boolean k_b : isBetaFixed) {
                        for (boolean k_n : isNuFixed) {
                            for (boolean k_r : isRhoFixed) {
                                // to meet the tough calibration tolerance we need to lower the default
                                // error threshold for accepting a calibration (to be more specific,
                                // some of the new test cases arising from fixing a subset of the
                                // model's parameters do not calibrate with the desired error using the
                                // initial guess (i.e. optimization runs into a local minimum) - then a
                                // series of random start values for optimization is chosen until our
                                // tight custom error threshold is satisfied.
                                SABRInterpolation sabrInterpolation = new SABRInterpolation(
                                        strikes, volatilities, expiry,
                                        forward, k_a ? initialAlpha : alphaGuess,
                                        k_b ? initialBeta : betaGuess,
                                        k_n ? initialNu : nuGuess,
                                        k_r ? initialRho : rhoGuess,
                                        k_a, k_b, k_n, k_r, i, endCriteria,
                                        method, 1E-10);
                                sabrInterpolation.update();

                                // Recover SABR calibration parameters
                                boolean failed = false;
                                double calibratedAlpha = sabrInterpolation.alpha();
                                double calibratedBeta = sabrInterpolation.beta();
                                double calibratedNu = sabrInterpolation.nu();
                                double calibratedRho = sabrInterpolation.rho();
                                double error;

                                // compare results: alpha
                                error = Math.abs(initialAlpha - calibratedAlpha);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate alpha Sabr parameter:"
                                            + "\n    expected:        " + initialAlpha
                                            + "\n    calibrated:      " + calibratedAlpha
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Beta
                                error = Math.abs(initialBeta - calibratedBeta);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate beta Sabr parameter:"
                                            + "\n    expected:        " + initialBeta
                                            + "\n    calibrated:      " + calibratedBeta
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Nu
                                error = Math.abs(initialNu - calibratedNu);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate nu Sabr parameter:"
                                            + "\n    expected:        " + initialNu
                                            + "\n    calibrated:      " + calibratedNu
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Rho
                                error = Math.abs(initialRho - calibratedRho);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate rho Sabr parameter:"
                                            + "\n    expected:        " + initialRho
                                            + "\n    calibrated:      " + calibratedRho
                                            + "\n    error:           " + error);
                                    failed = true;
                                }

                                assertFalse(failed, "\nSabr calibration failure:"
                                        + "\n    isAlphaFixed:    " + k_a
                                        + "\n    isBetaFixed:     " + k_b
                                        + "\n    isNuFixed:       " + k_n
                                        + "\n    isRhoFixed:      " + k_r
                                        + "\n    vegaWeighted[i]: " + i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testNoArbSabrInterpolation() {
        System.out.println("Testing no-arbitrage Sabr interpolation...");

        // Test SABR function against input volatilities
        double tolerance = 1.0e-12;
        double[] strikes = {
                0.03, 0.032, 0.034, 0.036, 0.038, 0.04, 0.042, 0.044,
                0.046, 0.048, 0.05, 0.052, 0.054, 0.056, 0.058, 0.06,
                0.062, 0.064, 0.066, 0.068, 0.07, 0.072, 0.074, 0.076,
                0.078, 0.08, 0.082, 0.084, 0.086, 0.088, 0.09};
        // input volatilities for noarb sabr (other than above
        // alpha is 0.2 here due to the restriction sigmaI <= 1.0 !)
        double[] volatilities = {
                0.773729077752926, 0.763916242454194, 0.754773878663612,
                0.746222305031368, 0.738193023523582, 0.730629785825930,
                0.723484825471685, 0.716716812668892, 0.710290301049393,
                0.704174528906769, 0.698342635400901, 0.692771033345972,
                0.687438902593476, 0.682327777297265, 0.677421206991904,
                0.672704476238547, 0.668164371832768, 0.663788984329375,
                0.659567547226380, 0.655490294349232, 0.651548341349061,
                0.647733583657137, 0.644038608699086, 0.640456620061898,
                0.636981371712714, 0.633607110719560, 0.630328527192861,
                0.627140710386248, 0.624039110072250, 0.621019502453590,
                0.618077959983455};

        double expiry = 1.0;
        double forward = 0.039;
        // input SABR coefficients (corresponding to the vols above)
        double initialAlpha = 0.2;
        double initialBeta = 0.6;
        double initialNu = 0.02;
        double initialRho = 0.01;
        // calculate SABR vols and compare with input vols
        NoArbSabrSmileSection noarbSabr = new NoArbSabrSmileSection(expiry,
                forward,
                Arrays.asList(initialAlpha, initialBeta, initialNu, initialRho));
        for (int i = 0; i < strikes.length; i++) {
            double calculatedVol = noarbSabr.volatility(strikes[i]);
            assertFalse(Math.abs(volatilities[i] - calculatedVol) > tolerance,
                    "failed to calculate noarb-Sabr function at strike " +
                            strikes[i]
                            + "\n    expected:   " + volatilities[i]
                            + "\n    calculated: " + calculatedVol
                            + "\n    error:      " +
                            Math.abs(calculatedVol - volatilities[i]));
        }

        // Test SABR calibration against input parameters
        double betaGuess = 0.5;
        double alphaGuess = 0.2 / Math.pow(forward, betaGuess - 1.0);
        double nuGuess = Math.sqrt(0.4);
        double rhoGuess = 0.0;

        boolean[] vegaWeighted = {true, false};
        boolean[] isAlphaFixed = {true, false};
        boolean[] isBetaFixed = {true, false};
        boolean[] isNuFixed = {true, false};
        boolean[] isRhoFixed = {true, false};

        double calibrationTolerance = 5.0e-6;
        // initialize optimization methods
        OptimizationMethod[] methods = {
                new Simplex(0.01),
                new LevenbergMarquardt(1e-8, 1e-8, 1e-8)
        };
        // Initialize end criteria
        EndCriteria endCriteria = new EndCriteria(100000, 100, 1e-8, 1e-8, 1e-8);
        // Test looping over all possibilities
        for (int j = 1; j < methods.length; ++j) { // skip simplex (gets caught in some cases)
            for (boolean i : vegaWeighted) {
                for (boolean k_a : isAlphaFixed) {
                    for (int k_b = 0; k_b < 1; ++k_b) { // keep beta fixed (all 4 params free is a problem for this kind of test)
                        for (boolean k_n : isNuFixed) {
                            for (boolean k_r : isRhoFixed) {
                                NoArbSabrInterpolation noarbSabrInterpolation =
                                        new NoArbSabrInterpolation(
                                                strikes, volatilities, expiry,
                                                forward,
                                                k_a ? initialAlpha : alphaGuess,
                                                isBetaFixed[k_b] ? initialBeta : betaGuess,
                                                k_n ? initialNu : nuGuess,
                                                k_r ? initialRho : rhoGuess,
                                                k_a, isBetaFixed[k_b], k_n, k_r, i,
                                                endCriteria, methods[j], 1E-10);
                                noarbSabrInterpolation.update();

                                // Recover SABR calibration parameters
                                boolean failed = false;
                                double calibratedAlpha = noarbSabrInterpolation.alpha();
                                double calibratedBeta = noarbSabrInterpolation.beta();
                                double calibratedNu = noarbSabrInterpolation.nu();
                                double calibratedRho = noarbSabrInterpolation.rho();
                                double error;

                                // compare results: alpha
                                error = Math.abs(initialAlpha - calibratedAlpha);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate alpha Sabr parameter:"
                                            + "\n    expected:        " + initialAlpha
                                            + "\n    calibrated:      " + calibratedAlpha
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Beta
                                error = Math.abs(initialBeta - calibratedBeta);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate beta Sabr parameter:"
                                            + "\n    expected:        " + initialBeta
                                            + "\n    calibrated:      " + calibratedBeta
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Nu
                                error = Math.abs(initialNu - calibratedNu);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate nu Sabr parameter:"
                                            + "\n    expected:        " + initialNu
                                            + "\n    calibrated:      " + calibratedNu
                                            + "\n    error:           " + error);
                                    failed = true;
                                }
                                // Rho
                                error = Math.abs(initialRho - calibratedRho);
                                if (error > calibrationTolerance) {
                                    System.out.println("\nfailed to calibrate rho Sabr parameter:"
                                            + "\n    expected:        " + initialRho
                                            + "\n    calibrated:      " + calibratedRho
                                            + "\n    error:           " + error);
                                    failed = true;
                                }

                                assertFalse(failed, "\nnoarb-Sabr calibration failure:"
                                        + "\n    isAlphaFixed:    " + k_a
                                        + "\n    isBetaFixed:     " + isBetaFixed[k_b]
                                        + "\n    isNuFixed:       " + k_n
                                        + "\n    isRhoFixed:      " + k_r
                                        + "\n    vegaWeighted[i]: " + i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSabrNegativeVolatilities() {
        System.out.println("Testing that Sabr interpolation rejects negative volatilities...");

        double[] strikes = {0.03, 0.04, 0.05, 0.06};
        double[] vols = {0.20, 0.18, 0.19, 0.185};
        double forward = 0.04;
        SABRInterpolation sabr = new SABRInterpolation(strikes, vols,
                1.0, forward, 0.2, 1.0, 0.1, 0.0,
                false, false, false, false, true);
        assertDoesNotThrow(() -> sabr.update());

        // the interpolation refers to the data, so changes are seen at recalibration
        vols[0] = 0.0;
        assertDoesNotThrow(() -> sabr.update());

        vols[0] = -0.20;
        assertThrows(IllegalArgumentException.class, sabr::update);
    }

    @Test
    public void testSabrGuess() {
        System.out.println("Testing Sabr guess...");

        // table 3 in Le Floc'h and Kennedy.
        // They only seems to use it with lognormal volatility
        // and no shift; we extend the test here.
        double[][] cases = {
                {0.058, 2016, 0.271, 1.0, 1.010, -0.345},
                {0.153, 2016, 0.256, 1.0, 0.933, -0.321},
                {0.230, 2016, 0.256, 1.0, 0.820, -0.346},
                {0.479, 2016, 0.255, 1.0, 0.629, -0.370},
                {0.729, 2016, 0.257, 1.0, 0.528, -0.403},
                {1.227, 2016, 0.260, 1.0, 0.448, -0.429},
                {1.726, 2016, 0.261, 1.0, 0.392, -0.440},
                {2.244, 2016, 0.262, 1.0, 0.355, -0.445},
                {2.742, 2016, 0.262, 1.0, 0.329, -0.445},
                {3.241, 2016, 0.262, 1.0, 0.310, -0.447},
                {4.239, 2016, 0.263, 1.0, 0.284, -0.452}
        };

        for (double[] c : cases) {
            double expiry = c[0];
            double forward = c[1];
            double alpha = c[2];
            double beta = c[3];
            double nu = c[4];
            double rho = c[5];
            for (Sarb.VolatilityType type : new Sarb.VolatilityType[]{
                    Sarb.VolatilityType.ShiftedLognormal, Sarb.VolatilityType.Normal}) {
                for (double shift : new double[]{0.0, 0.01, 0.1}) {
                    // strikes at forward and plus or minus 5%
                    double k_m = forward * 0.95, k_0 = forward, k_p = forward * 1.05;
                    double vol_m = Sarb.shiftedSabrVolatility(k_m, forward, expiry,
                            alpha, beta, nu, rho, shift, type);
                    double vol_0 = Sarb.shiftedSabrVolatility(k_0, forward, expiry,
                            alpha, beta, nu, rho, shift, type);
                    double vol_p = Sarb.shiftedSabrVolatility(k_p, forward, expiry,
                            alpha, beta, nu, rho, shift, type);

                    // try to invert smile and retrieve parameters
                    double[] guess = Sarb.sabrGuess(k_m, vol_m, k_0, vol_0, k_p, vol_p,
                            forward, expiry, beta, shift, type);

                    assertFalse(Math.abs(guess[0] - alpha) > 0.0001,
                            "alpha = " + guess[0] + ", expected = " + alpha
                                    + ", error = " + (guess[0] - alpha));
                    assertTrue(guess[1] == beta,
                            "beta = " + guess[1] + ", expected = " + beta);
                    assertFalse(Math.abs(guess[2] - nu) > 0.01,
                            "nu = " + guess[2] + ", expected = " + nu
                                    + ", error = " + (guess[2] - nu));
                    assertFalse(Math.abs(guess[3] - rho) > 0.005,
                            "rho = " + guess[3] + ", expected = " + rho
                                    + ", error = " + (guess[3] - rho));
                }
            }
        }
    }

    @Test
    public void testSabrSingleCases() {
        System.out.println("Testing Sabr calibration single cases...");

        // case #1
        // this fails with an exception thrown in 1.4, fixed in 1.5

        double[] strikes = {0.01, 0.01125, 0.0125, 0.01375, 0.0150};
        double[] vols = {0.1667, 0.2020, 0.2785, 0.3279, 0.3727};

        double tte = 0.3833;
        double forward = 0.011025;

        SABRInterpolation s0 = new SABRInterpolation(strikes, vols, tte, forward,
                NULL_REAL, 0.25, NULL_REAL, NULL_REAL,
                false, true, false, false);
        s0.update();

        assertFalse(s0.maxError() > 0.01 || s0.rmsError() > 0.01,
                "Sabr case #1 failed with max error (" + s0.maxError()
                        + ") and rms error (" + s0.rmsError()
                        + "), both should be < 0.01");
    }

    @Test
    public void testFlochKennedySabrIsSmoothAroundATM() {
        System.out.println("Testing that Andersen SABR formula is smooth "
                + "close to the ATM level...");

        double f0 = 1.1;
        double alpha = 0.35;
        double nu = 1.1;
        double rho = 0.25;
        double beta = 0.3;
        double strike = f0;
        double t = 2.1;

        double vol = Sarb.sabrFlochKennedyVolatility(strike, f0, t, alpha, beta, nu, rho);

        double expected = 0.3963883944;
        double tol = 1e-8;
        double diff = Math.abs(expected - vol);
        assertFalse(diff > tol,
                "\nfailed to get ATM value :"
                        + "\n    expected:   " + expected
                        + "\n    calculated: " + vol
                        + "\n    diff:      " + diff);

        double k = 0.996 * strike;
        double v = Sarb.sabrFlochKennedyVolatility(k, f0, t, alpha, beta, nu, rho);

        for (; k < 1.004 * strike; k += 0.0001 * strike) {
            double vt = Sarb.sabrFlochKennedyVolatility(k, f0, t, alpha, beta, nu, rho);

            diff = Math.abs(v - vt);

            assertFalse(diff > 1e-5,
                    "\nSabr vol spike around ATM :"
                            + "\n    volatility at " + (k - 0.0001 * strike)
                            + " is " + v
                            + "\n    volatility at " + k + " is " + vt
                            + "\n    difference: " + diff
                            + "\n    tolerance : " + 1e-5);
            v = vt;
        }
    }

    @Test
    public void testLeFlochKennedySabrExample() {
        System.out.println("Testing Le Floc'h Kennedy SABR Example...");

        /*
        Example is taken from F. Le Floc'h, G. Kennedy:
        Explicit SABR Calibration through Simple Expansions.
        https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2467231
        */

        double f0 = 1.0;
        double alpha = 0.35;
        double nu = 1.0;
        double rho = 0.25;
        double beta = 0.25;
        double[] strikes = {1.0, 1.5, 0.5};
        double t = 2.0;

        double[] expected = {0.408702473958, 0.428489933046, 0.585701651161};

        for (int i = 0; i < strikes.length; i++) {
            double strike = strikes[i];
            double vol =
                    Sarb.sabrFlochKennedyVolatility(strike, f0, t, alpha, beta, nu, rho);

            double tol = 1e-8;
            double diff = Math.abs(expected[i] - vol);
            assertFalse(diff > tol,
                    "\nfailed to reproduce reference examples :"
                            + "\n    expected:   " + expected[i]
                            + "\n    calculated: " + vol
                            + "\n    diff:       " + diff);
        }
    }

}
