package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct;

import java.util.ArrayList;
import java.util.List;

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
    public static Matrix Expm(final Matrix M, double t, double tol) {
        final int n = M.rows();
        QL_REQUIRE(n == M.cols(), "Expm expects a square matrix");

        AdaptiveRungeKutta rk = new AdaptiveRungeKutta(tol, 1e-4, 0);
        OdeFct odeFct = new MatrixVectorProductFct(M);

        Matrix result = new Matrix(n, n, Double.NaN);
        for (int i = 0; i < n; ++i) {
            List<Double> x0 = CommonUtil.ArrayInit(n, 0d);
            x0.set(i, 1d);
            final List<Double> r = rk.value(odeFct, x0, 0.0, t);
            for (int j = 0; j < r.size(); j++) {
                result.set(j, i, r.get(j));
            }
        }
        return result;
    }

    public static void check_symmetric(Matrix S) {
        for (int i = 0; i < S.rows(); i++)
            for (int j = 0; j < i; j++)
                QL_REQUIRE(S.get(i, j) == S.get(j, i), "input matrix is not symmetric");
    }

    /*! Iterative procedure to compute a correlation matrix reduction to
        a single factor dependence vector by minimizing the residuals.

        It assumes that such a reduction is possible, notice that if the
        dependence can not be reduced to one factor the correlation
        factors might be above 1.

        The matrix passed is destroyed.

        See for instance: "Modern Factor Analysis", Harry H. Harman,
          University Of Chicago Press, 1976. Chapter 9 is relevant to
          this context.
    */
    // default maxIters = 25
    public static List<Double> factorReduction(Matrix mtrx, int maxIters) {
        double tolerance = 1.e-6;

        QL_REQUIRE(mtrx.rows() == mtrx.cols(), "Input matrix is not square");

        final int n = mtrx.cols();
        // check symmetry
        for (int iRow = 0; iRow < mtrx.rows(); iRow++)
            for (int iCol = 0; iCol < iRow; iCol++)
                QL_REQUIRE(mtrx.get(iRow, iCol) == mtrx.get(iCol, iRow), "input matrix is not symmetric");
        QL_REQUIRE(mtrx.maxEle() <= 1 && mtrx.minEle() >= -1,
                "input matrix data is not correlation data");


        // Initial guess value
        List<Double> previousCorrels = CommonUtil.ArrayInit(n, 0d);
        for (int iCol = 0; iCol < n; iCol++) {
            for (int iRow = 0; iRow < n; iRow++)
                previousCorrels.set(iCol, previousCorrels.get(iCol) + mtrx.get(iRow, iCol) * mtrx.get(iRow, iCol));
            // previousCorrels[iCol] += mtrx[iRow][iCol] * mtrx[iRow][iCol];
            previousCorrels.set(iCol, Math.sqrt((previousCorrels.get(iCol) - 1.) / (n - 1.)));
            // previousCorrels[iCol] = std::sqrt((previousCorrels[iCol]-1.)/(n-1.));
        }

        // iterative solution
        int iteration = 0;
        double distance;
        do {
            // patch Matrix diagonal
            for (int iCol = 0; iCol < n; iCol++)
                mtrx.set(iCol, iCol, previousCorrels.get(iCol));
            // mtrx[iCol][iCol] = previousCorrels[iCol];
            // compute eigenvector decomposition
            SymmetricSchurDecomposition ssDec = new SymmetricSchurDecomposition(mtrx);
            //const Matrix& eigenVect = ssDec.eigenvectors();
            Array eigenVals = ssDec.eigenvalues();
            // We do not need the max value, only the position of the
            //   corresponding eigenvector
            int iMax = eigenVals.maxIndex();
            List<Double> newCorrels = new ArrayList<>();
            List<Double> distances = new ArrayList<>();

            for (int iCol = 0; iCol < n; iCol++) {
                double thisCorrel = mtrx.get(iMax, iCol); //[iMax][iCol];
                newCorrels.add(thisCorrel);
                // strictly is:
                // abs(\sqrt{\rho}- \sqrt{\rho_{old}})/\sqrt{\rho_{old}}
                distances.add(
                        Math.abs(thisCorrel - previousCorrels.get(iCol)) /
                                previousCorrels.get(iCol));
            }
            previousCorrels = newCorrels;
            distance = CommonUtil.maxVal(distances);
        } while (distance > tolerance && ++iteration <= maxIters);

        // test it did not go up to the max iteration and the matrix can
        //   be reduced to one factor.
        QL_REQUIRE(iteration < maxIters, "convergence not reached after " +
                iteration + " iterations");

        QL_REQUIRE(CommonUtil.maxVal(previousCorrels) <= 1 && CommonUtil.minVal(previousCorrels) >= -1,
                "matrix can not be decomposed to a single factor dependence");


        return previousCorrels;
    }

    //! Calculation of covariance from correlation and standard deviations
    /*! Combines the correlation matrix and the vector of standard deviations
        to return the covariance matrix.

        Note that only the symmetric part of the correlation matrix is
        used. Also it is assumed that the diagonal member of the
        correlation matrix equals one.

        \pre The correlation matrix must be symmetric with the diagonal
             members equal to one.

        \test tested on know values and cross checked with
              CovarianceDecomposition
    */
    // default tolerance = 1.0e-12
    Matrix getCovariance(List<Double> stdDev,
                         Matrix corr,
                         double tolerance) {
        int size = stdDev.size();
        QL_REQUIRE(corr.rows() == size,
                "dimension mismatch between volatilities (" + size +
                        ") and correlation rows (" + corr.rows() + ")");
        QL_REQUIRE(corr.cols() == size,
                "correlation matrix is not square: " + size +
                        " rows and " + corr.cols() + " columns");

        Matrix covariance = new Matrix(size, size, Double.NaN);
        int i, j;
        // DataIterator iIt, jIt;
        for (i = 0; i < size; ++i) {
            for (j = 0; j < i; ++j) {
                QL_REQUIRE(Math.abs(corr.get(i, j) - corr.get(j, i)) <= tolerance,
                        "correlation matrix not symmetric:"
                                + "\nc[" + i + "," + j + "] = " + corr.get(i, j)
                                + "\nc[" + j + "," + i + "] = " + corr.get(j, i));
                covariance.set(i, i, stdDev.get(i) * stdDev.get(i));
                covariance.set(i, j, stdDev.get(i) * stdDev.get(j) * 0.5 * (corr.get(i, j) + corr.get(j, i)));
                //covariance[i][j] = (*iIt) * (*jIt) * 0.5 * (corr[i][j] + corr[j][i]);
                covariance.set(j, i, covariance.get(i, j));
                // covariance[j][i] = covariance[i][j];
            }
            QL_REQUIRE(Math.abs(corr.get(i, i) - 1.0) <= tolerance,
                    "invalid correlation matrix, "
                            + "diagonal element of the " +
                            " row is " + corr.get(i, i) + " instead of 1.0");
            covariance.set(i, i, stdDev.get(i) * stdDev.get(i));
            // covariance[i][i] = (*iIt) * (*iIt);
        }
        return covariance;
    }
}
