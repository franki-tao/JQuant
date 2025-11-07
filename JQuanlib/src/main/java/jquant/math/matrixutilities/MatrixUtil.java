package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.matrixutilities.impl.HypersphereCostFunction;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct;
import jquant.math.optimization.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.transpose;
import static jquant.math.MathUtils.close;
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

    public static void checkSymmetry(Matrix matrix) {
        int size = matrix.rows();
        QL_REQUIRE(size == matrix.cols(),
                "non square matrix: " + size + " rows, " +
                        matrix.cols() + " columns");
        for (int i = 0; i < size; ++i)
            for (int j = 0; j < i; ++j)
                QL_REQUIRE(close(matrix.get(i, j), matrix.get(j, i)),
                        "non symmetric matrix: " +
                                "[" + i + "][" + j + "]=" + matrix.get(i, j) +
                                ", [" + j + "][" + i + "]=" + matrix.get(j, i));
    }

    public static void normalizePseudoRoot(Matrix matrix, Matrix pseudo) {
        int size = matrix.rows();
        QL_REQUIRE(size == pseudo.rows(),
                "matrix/pseudo mismatch: matrix rows are " + size +
                        " while pseudo rows are " + pseudo.cols());
        int pseudoCols = pseudo.cols();

        // row normalization
        for (int i = 0; i < size; ++i) {
            double norm = 0.0;
            for (int j = 0; j < pseudoCols; ++j)
                norm += pseudo.get(i, j) * pseudo.get(i, j);
            if (norm > 0.0) {
                double normAdj = Math.sqrt(matrix.get(i, i) / norm);
                for (int j = 0; j < pseudoCols; ++j)
                    pseudo.set(i, j, pseudo.get(i, j) * normAdj);
            }
        }
    }

    // Optimization function for hypersphere and lower-diagonal algorithm
    public static Matrix hypersphereOptimize(Matrix targetMatrix, Matrix currentRoot,
                                             boolean lowerDiagonal) {
        int i, j, k, size = targetMatrix.rows();
        Matrix result = new Matrix(currentRoot.matrix);
        Array variance = new Array(size, 0);
        for (i = 0; i < size; i++) {
            variance.set(i, Math.sqrt(targetMatrix.get(i, i)));
            // variance[i]=std::sqrt(targetMatrix[i][i]);
        }
        if (lowerDiagonal) {
            Matrix approxMatrix = new Matrix(result.multipy(transpose(result)).matrix);
            result = CholeskyDecomposition(approxMatrix, true);
            for (i = 0; i < size; i++) {
                for (j = 0; j < size; j++) {
                    result.multipyEq(i, j, 1d / Math.sqrt(approxMatrix.get(i, i)));
                    // result[i][j]/=std::sqrt(approxMatrix[i][i]);
                }
            }
        } else {
            for (i = 0; i < size; i++) {
                for (j = 0; j < size; j++) {
                    result.multipyEq(i, j, 1d / Math.sqrt(variance.get(i)));
                    // result[i][j]/=variance[i];
                }
            }
        }

        ConjugateGradient optimize = new ConjugateGradient(new ArmijoLineSearch(1e-8, 0.05, 0.65));
        EndCriteria endCriteria = new EndCriteria(100, 10, 1e-8, 1e-8, 1e-8);
        HypersphereCostFunction costFunction = new HypersphereCostFunction(targetMatrix, variance, lowerDiagonal);
        NoConstraint constraint = new NoConstraint();

        // hypersphere vector optimization

        if (lowerDiagonal) {
            Array theta = new Array(size * (size - 1) / 2);
            double eps = 1e-16;
            for (i = 1; i < size; i++) {
                for (j = 0; j < i; j++) {
                    theta.set(i * (i - 1) / 2 + j, result.get(i, j));
                    // theta[i*(i-1)/2+j]=result[i][j];
                    if (theta.get(i * (i - 1) / 2 + j) > 1 - eps)
                        theta.set(i * (i - 1) / 2 + j, 1 - eps);
                    // theta[i*(i-1)/2+j]=1-eps;
                    if (theta.get(i * (i - 1) / 2 + j) < -1 + eps)
                        theta.set(i * (i - 1) / 2 + j, -1 + eps);
                    // theta[i*(i-1)/2+j]=-1+eps;
                    for (k = 0; k < j; k++) {
                        theta.multiplyEq(i * (i - 1) / 2 + j, 1d / (Math.sin(theta.get(i * (i - 1) / 2 + k))));
                        // theta[i*(i-1)/2+j] /= std::sin(theta[i*(i-1)/2+k]);
                        if (theta.get(i * (i - 1) / 2 + j) > 1 - eps)
                            theta.set(i * (i - 1) / 2 + j, 1 - eps);
                        // theta[i*(i-1)/2+j]=1-eps;
                        if (theta.get(i * (i - 1) / 2 + j) < -1 + eps)
                            theta.set(i * (i - 1) / 2 + j, -1 + eps);
                        // theta[i*(i-1)/2+j]=-1+eps;
                    }
                    theta.set(i * (i - 1) / 2 + j, Math.acos(theta.get(i * (i - 1) / 2 + j)));
                    // theta[i*(i-1)/2+j] = std::acos(theta[i*(i-1)/2+j]);
                    if (j == i - 1) {
                        if (result.get(i, i) < 0)
                            theta.set(i * (i - 1) / 2 + j, -theta.get(i * (i - 1) / 2 + j));
                        // theta[i*(i-1)/2+j]=-theta[i*(i-1)/2+j];
                    }
                }
            }
            Problem p = new Problem(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            theta = p.currentValue();
            result.fill(1.0);
            // std::fill(result.begin(),result.end(),1.0);
            for (i = 0; i < size; i++) {
                for (k = 0; k < size; k++) {
                    if (k > i) {
                        result.set(i, k, 0);
                        // result[i][k]=0;
                    } else {
                        for (j = 0; j <= k; j++) {
                            if (j == k && k != i)
                                result.multipyEq(i, k, Math.cos(theta.get(i * (i - 1) / 2 + j)));
                                // result[i][k] *= std::cos(theta[i*(i-1)/2+j]);
                            else if (j != i)
                                result.multipyEq(i, k, Math.sin(theta.get(i * (i - 1) / 2 + j)));
                            // result[i][k] *= std::sin(theta[i*(i-1)/2+j]);
                        }
                    }
                }
            }
        } else {
            Array theta = new Array(size * (size - 1));
            double eps = 1e-16;
            for (i = 0; i < size; i++) {
                for (j = 0; j < size - 1; j++) {
                    theta.set(j * size + i, result.get(i, j));
                    // theta[j * size + i] = result[i][j];
                    if (theta.get(j * size + i) > 1 - eps)
                        theta.set(j * size + i, 1 - eps);
                    // theta[j * size + i] = 1 - eps;
                    if (theta.get(j * size + i) < -1 + eps)
                        theta.set(j * size + i, -1 + eps);
                    // theta[j * size + i] = -1 + eps;
                    for (k = 0; k < j; k++) {
                        theta.multiplyEq(j * size + i, 1d / (Math.sin(theta.get(k * size + i))));
                        // theta[j * size + i] /= std::sin (theta[k * size + i]);
                        if (theta.get(j * size + i) > 1 - eps)
                            theta.set(j * size + i, 1 - eps);
                        // theta[j * size + i] = 1 - eps;
                        if (theta.get(j * size + i) < -1 + eps)
                            theta.set(j * size + i, -1 + eps);
                        // theta[j * size + i] = -1 + eps;
                    }
                    theta.set(j * size + i, Math.acos(theta.get(j * size + i)));
                    // theta[j * size + i] = std::acos (theta[j * size + i]);
                    if (j == size - 2) {
                        if (result.get(i, j + 1) < 0)
                            theta.set(j * size + i, -theta.get(j * size + i));
                        // theta[j * size + i] = -theta[j * size + i];
                    }
                }
            }
            Problem p = new Problem(costFunction, constraint, theta);
            optimize.minimize(p, endCriteria);
            theta = p.currentValue();
            result.fill(1.0);
            // std::fill (result.begin(), result.end(), 1.0);
            for (i = 0; i < size; i++) {
                for (k = 0; k < size; k++) {
                    for (j = 0; j <= k; j++) {
                        if (j == k && k != size - 1)
                            result.multipyEq(i, k, Math.cos(theta.get(j * size + i)));
                            // result[i][k] *= std::cos (theta[j * size + i]);
                        else if (j != size - 1)
                            result.multipyEq(i, k, Math.sin(theta.get(j * size + i)));
                        // result[i][k] *= std::sin (theta[j * size + i]);
                    }
                }
            }
        }

        for (i = 0; i < size; i++) {
            for (j = 0; j < size; j++) {
                result.multipyEq(i, j, variance.get(i));
                // result[i][j] *= variance[i];
            }
        }
        return result;
    }

    // Matrix infinity norm. See Golub and van Loan (2.3.10) or
    // <http://en.wikipedia.org/wiki/Matrix_norm>
    public static double normInf(Matrix M) {
        int rows = M.rows();
        int cols = M.cols();
        double norm = 0.0;
        for (int i = 0; i < rows; ++i) {
            double colSum = 0.0;
            for (int j = 0; j < cols; ++j)
                colSum += Math.abs(M.get(i, j));
            norm = Math.max(norm, colSum);
        }
        return norm;
    }

    // Take a matrix and make all the diagonal entries 1.
    public static Matrix projectToUnitDiagonalMatrix(Matrix M) {
        int size = M.rows();
        QL_REQUIRE(size == M.cols(),
                "matrix not square");

        Matrix result = new Matrix(M.matrix);
        for (int i = 0; i < size; ++i)
            result.set(i, i, 1);
        // result[i][i] = 1.0;
        return result;
    }

    public static Matrix projectToPositiveSemidefiniteMatrix(Matrix M) {
        int size = M.rows();
        QL_REQUIRE(size == M.cols(),
                "matrix not square");

        Matrix diagonal = new Matrix(size, size, 0.0);
        SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(M);
        Array eigenvalues = jd.eigenvalues();
        for (int i = 0; i < size; ++i)
            diagonal.set(i, i, Math.max(eigenvalues.get(i), 0));
        // diagonal[i][i] = std::max<Real>(jd.eigenvalues()[i], 0.0);

        // jd.eigenvectors()*diagonal*transpose(jd.eigenvectors());
        return jd.eigenvectors().multipy(diagonal).multipy(transpose(jd.eigenvectors()));
    }

    // implementation of the Higham algorithm to find the nearest
    // correlation matrix.
    public static Matrix highamImplementation(final Matrix A, final int maxIterations, final double tolerance) {

        int size = A.rows();
        Matrix R;
        Matrix Y = new Matrix(A.matrix);
        Matrix X = new Matrix(A.matrix);
        Matrix deltaS = new Matrix(size, size, 0.0);

        Matrix lastX = new Matrix(X.matrix);
        Matrix lastY = new Matrix(Y.matrix);

        for (int i = 0; i < maxIterations; ++i) {
            R = Y.subtract(deltaS);
            X = projectToPositiveSemidefiniteMatrix(R);
            deltaS = X.subtract(R);
            Y = projectToUnitDiagonalMatrix(X);

            // convergence test
            if (CommonUtil.maxVal(Arrays.asList(normInf(X.subtract(lastX)) / normInf(X),
                    normInf(Y.subtract(lastY)) / normInf(Y),
                    normInf(Y.subtract(X)) / normInf(Y))) <= tolerance) {
                break;
            }
            lastX = X;
            lastY = Y;
        }

        // ensure we return a symmetric matrix
        for (int i = 0; i < size; ++i)
            for (int j = 0; j < i; ++j)
                Y.set(i, j, Y.get(j, i));
        // Y[i][j] = Y[j][i];

        return Y;
    }
}
