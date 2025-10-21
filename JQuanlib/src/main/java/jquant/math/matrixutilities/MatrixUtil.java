package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

public class MatrixUtil {
    public static Matrix CholeskyDecomposition(final Matrix S, boolean flexible) {
        int i, j, size = S.rows();

        QL_REQUIRE(size == S.cols(), "input matrix is not a square matrix");
        check_symmetric(S);
        Matrix result = new Matrix(size, size, 0.0);
        double sum;
        for (i = 0; i < size; i++) {
            for (j = i; j < size; j++) {
                sum = S.get(i, j);
                for (int k = 0; k <= i - 1; k++) {
                    sum -= result.get(i, k) * result.get(j, k);
                }
                if (i == j) {
                    QL_REQUIRE(flexible || sum > 0.0,
                            "input matrix is not positive definite");
                    // To handle positive semi-definite matrices take the
                    // square root of sum if positive, else zero.
                    result.set(i, i, Math.sqrt(Math.max(sum, 0d)));
                    // result[i][i] = std::sqrt(std::max<Real>(sum, 0.0));
                } else {
                    // With positive semi-definite matrices is possible
                    // to have result[i][i]==0.0
                    // In this case sum happens to be zero as well
                    result.set(j, i, close_enough(result.get(i, i), 0d) ? 0d : sum / result.get(i, i));
                    // result[j][i] = close_enough(result[i][i], 0.0) ? 0.0 : Real(sum / result[i][i]);
                }
            }
        }
        return result;
    }

    public static Array CholeskySolveFor(final Matrix L, final Array b) {
        final int n = b.size();

        QL_REQUIRE(L.cols() == n && L.rows() == n,
                "Size of input matrix and vector does not match.");

        Array x = new Array(n);
        for (int i = 0; i < n; ++i) {
            double tmp = -b.get(i);
            for (int j = 0; j < i; j++) {
                tmp += L.get(i, j) * x.get(j);
            }
            x.set(i, -tmp);
            // x[i] = -std::inner_product(L.row_begin(i), L.row_begin(i)+i, x.begin(), Real(-b[i]));
            x.set(i, x.get(i) / L.get(i, i));
            // x[i] /= L[i][i];
        }

        for (int i = n - 1; i >= 0; --i) {
            double tmp = -x.get(i);
            for (int j = i + 1; j < L.rows(); j++) {
                tmp += L.get(j, i) * x.get(j);
            }
            x.set(i, -tmp);
            // x[i] = -std::inner_product (L.column_begin(i) + i + 1, L.column_end(i), x.begin() + i + 1, Real(-x[i]));
            x.set(i, x.get(i) / L.get(i, i));
            // x[i] /= L[i][i];
        }
        return x;
    }

    //! matrix exponential based on the ordinary differential equations method

    /*! References:

        C. Moler; C. Van Loan, 1978,
        Nineteen Dubious Ways to Compute the Exponential of a Matrix
        http://xa.yimg.com/kq/groups/22199541/1399635765/name/moler-nineteen.pdf
    */

    //! returns the matrix exponential exp(t*M)
    // default double t=1.0, double tol=QL_EPSILON
//    public static Matrix Expm(final Matrix M, double t, double tol) {
//        final int n = M.rows();
//        QL_REQUIRE(n == M.cols(), "Expm expects a square matrix");
//
//        AdaptiveRungeKutta<> rk(tol);
//        AdaptiveRungeKutta<>::OdeFct odeFct = MatrixVectorProductFct(M);
//
//        Matrix result(n, n);
//        for (Size i=0; i < n; ++i) {
//            std::vector<Real> x0(n, 0.0);
//            x0[i] = 1.0;
//
//            const std::vector<Real> r = rk(odeFct, x0, 0.0, t);
//            std::copy(r.begin(), r.end(), result.column_begin(i));
//        }
//        return result;
//    }

    public static void check_symmetric(Matrix S) {
        for (int i = 0; i < S.rows(); i++)
            for (int j = 0; j < i; j++)
                QL_REQUIRE(S.get(i, j) == S.get(j, i), "input matrix is not symmetric");
    }
}
