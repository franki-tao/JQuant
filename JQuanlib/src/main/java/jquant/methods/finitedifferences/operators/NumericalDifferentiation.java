package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.math.Function;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Numerical Differentiation on arbitrarily spaced grids.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/numericaldifferentiation.{hpp,cpp}}.
 *
 * <p>References:
 * B. Fornberg, 1988. Generation of Finite Difference Formulas
 * on Arbitrarily Spaced Grids,
 * http://amath.colorado.edu/faculty/fornberg/Docs/MathComp_88_FD_formulas.pdf
 */
public class NumericalDifferentiation {

    public enum Scheme { Central, Backward, Forward }

    private final Array offsets_, w_;
    private final Function f_;

    public NumericalDifferentiation(Function f,
                                    int orderOfDerivative,
                                    Array x_offsets) {
        offsets_ = x_offsets;
        w_ = calcWeights(offsets_, orderOfDerivative);
        f_ = f;
    }

    public NumericalDifferentiation(Function f,
                                    int orderOfDerivative,
                                    double stepSize,
                                    int steps,
                                    Scheme scheme) {
        offsets_ = calcOffsets(stepSize, steps, scheme);
        w_ = calcWeights(offsets_, orderOfDerivative);
        f_ = f;
    }

    public double value(double x) {
        double s = 0.0;
        for (int i = 0; i < w_.size(); ++i) {
            if (Math.abs(w_.get(i)) > QL_EPSILON * QL_EPSILON) {
                s += w_.get(i) * f_.value(x + offsets_.get(i));
            }
        }
        return s;
    }

    public Array offsets() {
        return offsets_;
    }

    public Array weights() {
        return w_;
    }

    private static Array calcOffsets(double h, int n, Scheme scheme) {
        QL_REQUIRE(n > 1, "number of steps must be greater than one");

        Array retVal = new Array(n);
        switch (scheme) {
            case Central:
                QL_REQUIRE(n > 2 && (n % 2) == 1,
                        "number of steps must be an odd number greater than two");
                for (int i = 0; i < n; ++i) {
                    retVal.set(i, (i - (n / 2)) * h);
                }
                break;
            case Backward:
                for (int i = 0; i < n; ++i) {
                    retVal.set(i, -(i * h));
                }
                break;
            case Forward:
                for (int i = 0; i < n; ++i) {
                    retVal.set(i, i * h);
                }
                break;
            default:
                QL_FAIL("unknown numerical differentiation scheme");
        }

        return retVal;
    }

    // This is a C++ implementation of the algorithm/pseudo code in
    // B. Fornberg, 1998. Calculation of Weights
    //                    in Finite Difference Formulas
    // https://amath.colorado.edu/faculty/fornberg/Docs/sirev_cl.pdf
    private static Array calcWeights(final Array x, int M) {
        final int N = x.size();
        QL_REQUIRE(N > M, "number of points must be greater "
                + "than the order of the derivative");

        double[][][] d = new double[M + 1][N][N];
        d[0][0][0] = 1.0;
        double c1 = 1.0;

        for (int n = 1; n < N; ++n) {
            double c2 = 1.0;
            for (int nu = 0; nu < n; ++nu) {
                final double c3 = x.get(n) - x.get(nu);
                c2 *= c3;

                for (int m = 0; m <= Math.min(n, M); ++m) {
                    d[m][n][nu] = (x.get(n) * d[m][n - 1][nu]
                            - ((m > 0) ? m * d[m - 1][n - 1][nu] : 0.0)) / c3;
                }
            }

            for (int m = 0; m <= M; ++m) {
                d[m][n][n] = c1 / c2 * (((m > 0) ? m * d[m - 1][n - 1][n - 1] : 0.0)
                        - x.get(n - 1) * d[m][n - 1][n - 1]);
            }
            c1 = c2;
        }

        Array retVal = new Array(N);
        for (int i = 0; i < N; ++i) {
            retVal.set(i, d[M][N - 1][i]);
        }
        return retVal;
    }
}
