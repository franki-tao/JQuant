package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.math.matrixutilities.impl.HypersphereCostFunction;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.math.matrixutilities.impl.SparseMatrix;
import jquant.math.ode.AdaptiveRungeKutta;
import jquant.math.ode.OdeFct;
import jquant.math.optimization.*;
import jquant.math.optimization.impl.MinPack;
import jquant.math.optimization.impl.QrFacParams;
import jquant.math.optimization.impl.QrsolvParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.*;
import static jquant.math.MathUtils.*;
import static jquant.math.matrixutilities.impl.SalvagingAlgorithm.Type.Higham;

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

    //! Returns the pseudo square root of a real symmetric matrix
    /*! Given a matrix \f$ M \f$, the result \f$ S \f$ is defined
        as the matrix such that \f$ S S^T = M. \f$
        If the matrix is not positive semi definite, it can
        return an approximation of the pseudo square root
        using a (user selected) salvaging algorithm.

        For more information see: R. Rebonato and P. Jäckel, The most
        general methodology to create a valid correlation matrix for
        risk management and option pricing purposes, The Journal of
        Risk, 2(2), Winter 1999/2000.
        http://www.rebonato.com/correlationmatrix.pdf

        Revised and extended in "Monte Carlo Methods in Finance",
        by Peter Jäckel, Chapter 6.

        \pre the given matrix must be symmetric.

        \relates Matrix

        \warning Higham algorithm only works for correlation matrices.

        \test
        - the correctness of the results is tested by reproducing
          known good data.
        - the correctness of the results is tested by checking
          returned values against numerical calculations.
    */
    public static Matrix pseudoSqrt(final Matrix matrix,
                                    SalvagingAlgorithm.Type sa) {
        if (sa == null) {
            sa = SalvagingAlgorithm.Type.None;
        }
        int size = matrix.rows();
        checkSymmetry(matrix);
        QL_REQUIRE(size == matrix.cols(),
                "non square matrix: " + size + " rows, " +
                        matrix.cols() + " columns");

        // spectral (a.k.a Principal Component) analysis
        SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(matrix);
        Matrix diagonal = new Matrix(size, size, 0.0);

        // salvaging algorithm
        Matrix result = new Matrix(size, size, Double.NaN);
        boolean negative;
        switch (sa) {
            case None:
                // eigenvalues are sorted in decreasing order
                QL_REQUIRE(jd.eigenvalues().get(size - 1) >= -1e-16,
                        "negative eigenvalue(s) ("
                                + jd.eigenvalues().get(size - 1)
                                + ")");
                result = CholeskyDecomposition(matrix, true);
                break;
            case Spectral:
                // negative eigenvalues set to zero
                for (int i = 0; i < size; i++)
                    diagonal.set(i, i, Math.sqrt(Math.max(jd.eigenvalues().get(i), 0.0)));
                // diagonal[i][i] = std::sqrt (std::max < Real > (jd.eigenvalues()[i], 0.0));

                result = jd.eigenvectors().multipy(diagonal);
                normalizePseudoRoot(matrix, result);
                break;
            case Hypersphere:
                // negative eigenvalues set to zero
                negative = false;
                for (int i = 0; i < size; ++i) {
                    diagonal.set(i, i, Math.sqrt(Math.max(jd.eigenvalues().get(i), 0.0)));
                    // diagonal[i][i] = std::sqrt (std::max < Real > (jd.eigenvalues()[i], 0.0));
                    if (jd.eigenvalues().get(i) < 0.0) negative = true;
                }
                result = jd.eigenvectors().multipy(diagonal);
                normalizePseudoRoot(matrix, result);

                if (negative)
                    result = hypersphereOptimize(matrix, result, false);
                break;
            case LowerDiagonal:
                // negative eigenvalues set to zero
                negative = false;
                for (int i = 0; i < size; ++i) {
                    diagonal.set(i, i, Math.sqrt(Math.max(jd.eigenvalues().get(i), 0.0)));
                    if (jd.eigenvalues().get(i) < 0.0) negative = true;
                }
                result = jd.eigenvectors().multipy(diagonal);

                normalizePseudoRoot(matrix, result);

                if (negative)
                    result = hypersphereOptimize(matrix, result, true);
                break;
            case Higham: {
                int maxIterations = 40;
                double tol = 1e-6;
                result = highamImplementation(matrix, maxIterations, tol);
                result = CholeskyDecomposition(result, true);
            }
            break;
            case Principal: {
                QL_REQUIRE(jd.eigenvalues().back() >= -10 * QL_EPSILON,
                        "negative eigenvalue(s) ("
                                + jd.eigenvalues().back()
                                + ")");

                Array sqrtEigenvalues = new Array(size);
                for (int i = 0; i < jd.eigenvalues().size(); i++) {
                    sqrtEigenvalues.set(i, Math.sqrt(Math.max(jd.eigenvalues().get(i), 0d)));
                }
                for (int i = 0; i < size; ++i)
                    for (int j = 0; j < sqrtEigenvalues.size(); j++) {
                        diagonal.set(j, i, sqrtEigenvalues.get(j) * jd.eigenvectors().get(i, j));
                    }

                result = jd.eigenvectors().multipy(diagonal);
                result = result.add(transpose(result)).multiply(0.5);
                // result = 0.5 * (result + transpose(result));
            }
            break;
            default:
                QL_FAIL("unknown salvaging algorithm");
        }
        return result;
    }

    //! Returns the rank-reduced pseudo square root of a real symmetric matrix
    /*! The result matrix has rank<=maxRank. If maxRank>=size, then the
        specified percentage of eigenvalues out of the eigenvalues' sum is
        retained.

        If the input matrix is not positive semi definite, it can return an
        approximation of the pseudo square root using a (user selected)
        salvaging algorithm.

        \pre the given matrix must be symmetric.

        \relates Matrix
    */
    public static Matrix rankReducedSqrt(final Matrix matrix,
                                         int maxRank,
                                         double componentRetainedPercentage,
                                         SalvagingAlgorithm.Type sa) {
        int size = matrix.rows();

        checkSymmetry(matrix);
        QL_REQUIRE(size == matrix.cols(),
                "non square matrix: " + size + " rows, " +
                        matrix.cols() + " columns");

        QL_REQUIRE(componentRetainedPercentage > 0.0,
                "no eigenvalues retained");

        QL_REQUIRE(componentRetainedPercentage <= 1.0,
                "percentage to be retained > 100%");

        QL_REQUIRE(maxRank >= 1,
                "max rank required < 1");

        // spectral (a.k.a Principal Component) analysis
        SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(matrix);
        Array eigenValues = jd.eigenvalues();

        // salvaging algorithm
        switch (sa) {
            case None:
                // eigenvalues are sorted in decreasing order
                QL_REQUIRE(eigenValues.get(size - 1) >= -1e-16,
                        "negative eigenvalue(s) ("
                                + eigenValues.get(size - 1)
                                + ")");
                break;
            case Spectral:
                // negative eigenvalues set to zero
                for (int i = 0; i < size; ++i)
                    eigenValues.set(i, Math.max(eigenValues.get(i), 0d));
                // eigenValues[i] = std::max<Real>(eigenValues[i], 0.0);
                break;
            case Higham: {
                int maxIterations = 40;
                double tolerance = 1e-6;
                Matrix adjustedMatrix = highamImplementation(matrix, maxIterations, tolerance);
                jd = new SymmetricSchurDecomposition(adjustedMatrix);
                eigenValues = jd.eigenvalues();
            }
            break;
            default:
                QL_FAIL("unknown or invalid salvaging algorithm");
        }

        // factor reduction
        double enough = componentRetainedPercentage * CommonUtil.accumulate(eigenValues, 0, eigenValues.size(), 0d);
        // std::accumulate(eigenValues.begin(), eigenValues.end(), Real(0.0));
        if (componentRetainedPercentage == 1.0) {
            // numerical glitches might cause some factors to be discarded
            enough *= 1.1;
        }
        // retain at least one factor
        double components = eigenValues.get(0);
        int retainedFactors = 1;
        for (int i = 1; components < enough && i < size; ++i) {
            components += eigenValues.get(i);
            retainedFactors++;
        }
        // output is granted to have a rank<=maxRank
        retainedFactors = Math.min(retainedFactors, maxRank);

        Matrix diagonal = new Matrix(size, retainedFactors, 0.0);
        for (int i = 0; i < retainedFactors; ++i)
            diagonal.set(i, i, Math.sqrt(eigenValues.get(i)));
        // diagonal[i][i] = std::sqrt(eigenValues[i]);
        Matrix result = jd.eigenvectors().multipy(diagonal);

        normalizePseudoRoot(matrix, result);
        return result;
    }

    //! QR decompoisition
    /*! This implementation is based on MINPACK
        (<http://www.netlib.org/minpack>,
        <http://www.netlib.org/cephes/linalg.tgz>)

        This subroutine uses householder transformations with column
        pivoting (optional) to compute a qr factorization of the
        m by n matrix A. That is, qrfac determines an orthogonal
        matrix q, a permutation matrix p, and an upper trapezoidal
        matrix r with diagonal elements of nonincreasing magnitude,
        such that A*p = q*r.

        Return value ipvt is an integer array of length n, which
        defines the permutation matrix p such that A*p = q*r.
        Column j of p is column ipvt(j) of the identity matrix.

        See lmdiff.cpp for further details.
    */
    // default pivot = true
    public static List<Integer> qrDecomposition(final Matrix M,
                                                Matrix q,
                                                Matrix r,
                                                boolean pivot) {
        Matrix mT = transpose(M);
        final int m = M.rows();
        final int n = M.cols();

        int[] lipvt = new int[n];
        double[] rdiag = new double[n];
        double[] wa = new double[n];
        double[] aa = mT.toArray();
        QrFacParams params = new QrFacParams(m, n, mT.toArray(), 0, (pivot) ? 1 : 0, lipvt, n, rdiag, rdiag, wa);
        MinPack.qrfac(params);
        // MinPack.qrfac(m, n, mT.begin(), 0, (pivot)?1:0, lipvt.get(), n, rdiag.get(), rdiag.get(), wa.get());
        //出箱
        mT.ArraytoMatrix(params.a);
        lipvt = params.ipvt;
        rdiag = params.rdiag;
        wa = params.wa;
        if (r.cols() != n || r.rows() != n)
            r = new Matrix(n, n, Double.NaN);

        for (int i = 0; i < n; ++i) {
            r.row_fill(i, 0, i, 0);
            // std::fill(r.row_begin(i), r.row_begin(i)+i, 0.0);
            r.set(i, i, rdiag[i]);
            // r[i][i] = rdiag[i];
            if (i < m) {
                for (int j = i + 1; j < mT.cols(); j++) {
                    r.set(i, j, mT.get(j, i));
                }
                // std::copy(mT.column_begin(i)+i+1, mT.column_end(i), r.row_begin(i)+i+1);
            } else {
                r.row_fill(i, i + 1, r.cols(), 0d);
                // std::fill(r.row_begin(i)+i+1, r.row_end(i), 0.0);
            }
        }

        if (q.rows() != m || q.cols() != n)
            q = new Matrix(m, n, Double.NaN);

        if (m > n) {
            q.fill(0);
            // std::fill(q.begin(), q.end(), 0.0);

            int u = Math.min(n, m);
            for (int i = 0; i < u; ++i)
                q.set(i, i, 1d);

            Array v = new Array(m);
            for (int i = u - 1; i >= 0; --i) {
                if (Math.abs(mT.get(i, i)) > QL_EPSILON) {
                    final double tau = 1.0 / mT.get(i, i);
                    v.fill(0, i, 0d);
                    // std::fill(v.begin(), v.begin()+i, 0.0);
                    for (int j = i; j < mT.cols(); ++j) {
                        v.set(j, mT.get(i, j));
                    }
                    // std::copy(mT.row_begin(i)+i, mT.row_end(i), v.begin()+i);

                    Array w = new Array(n, 0.0);
                    for (int l = 0; l < n; ++l) {
                        double temp = 0d;
                        for (int k = i; k < v.size(); ++k) {
                            temp += v.get(k) * q.get(k, l);
                        }
                        w.addEq(l, temp);
                    }
                    // w[l] += std::inner_product(v.begin()+i, v.end(), q.column_begin(l)+i, Real(0.0));

                    for (int k = i; k < m; ++k) {
                        final double a = tau * v.get(k);
                        for (int l = 0; l < n; ++l)
                            q.set(k, l, q.get(k, l) - a * w.get(l));
                        // q[k][l] -= a*w[l];
                    }
                }
            }
        } else {
            Array w = new Array(m);
            for (int k = 0; k < m; ++k) {
                w.fill(0, w.size(), 0d);
                // std::fill(w.begin(), w.end(), 0.0);
                w.set(k, 1d);
                // w[k] = 1.0;

                for (int j = 0; j < Math.min(n, m); ++j) {
                    final double t3 = mT.get(j, j);
                    if (t3 != 0.0) {
                        double temp = 0d;
                        for (int i = j; i < mT.cols(); i++) {
                            temp += mT.get(j, i) * w.get(i);
                        }
                        final double t = temp / t3;
                        // final double t = std::inner_product (mT.row_begin(j) + j, mT.row_end(j), w.begin() + j, Real(0.0))/t3;
                        for (int i = j; i < m; ++i) {
                            w.subtractEq(i, mT.get(j, i) * t);
                            // w[i] -= mT[j][i] * t;
                        }
                    }
                    q.set(k, j, w.get(j));
                    // q[k][j] = w[j];
                }
                q.row_fill(k, Math.min(n, m), q.cols(), 0d);
                // std::fill (q.row_begin(k) + std::min (n, m),q.row_end(k), 0.0);
            }
        }
        List<Integer> ipvt = CommonUtil.ArrayInit(n);
        // std::vector < Size > ipvt(n);

        if (pivot) {
            for (int i = 0; i < n; i++) {
                ipvt.set(i, lipvt[i]);
            }
            // std::copy (lipvt.get(), lipvt.get() + n, ipvt.begin());
        } else {
            for (int i = 0; i < n; ++i)
                ipvt.set(i, i);
            // ipvt[i] = i;
        }

        return ipvt;
    }

    //! QR Solve
    /*! This implementation is based on MINPACK
        (<http://www.netlib.org/minpack>,
        <http://www.netlib.org/cephes/linalg.tgz>)

        Given an m by n matrix A, an n by n diagonal matrix d,
        and an m-vector b, the problem is to determine an x which
        solves the system

        A*x = b ,     d*x = 0 ,

        in the least squares sense.

        d is an input array of length n which must contain the
        diagonal elements of the matrix d.

        See lmdiff.cpp for further details.
    */
    // default pivot = true d = Array()
    public static Array qrSolve(final Matrix a,
                                final Array b,
                                boolean pivot,
                                final Array d) {
        final int m = a.rows();
        final int n = a.cols();

        QL_REQUIRE(b.size() == m, "dimensions of A and b don't match");
        QL_REQUIRE(d.size() == n || d.empty(), "dimensions of A and d don't match");

        Matrix q = new Matrix(m, n, Double.NaN);
        Matrix r = new Matrix(n, n,  Double.NaN);

        List<Integer> lipvt = qrDecomposition(a, q, r, pivot);

        int[] ipvt = new int[n];
        for (int i = 0; i < lipvt.size(); i++) {
            ipvt[i] = lipvt.get(i);
        }
        // std::copy(lipvt.begin(), lipvt.end(), ipvt.get());

        Matrix rT = transpose(r);

        double[] sdiag = new double[n];
        double[] wa = new double[n];

        Array ld = new Array(n, 0.0);
        if (!d.empty()) {
            for (int i = 0; i < d.size(); i++) {
                ld.set(i, d.get(i));
            }
            // std::copy(d.begin(), d.end(), ld.begin());
        }

        Array x = new Array(n);
        Array qtb = transpose(q).mutiply(b);
        // 装箱
        QrsolvParams params = new QrsolvParams(n, rT.toArray(), n, ipvt, ld.toArray(), qtb.toArray(), x.toArray(), sdiag, wa);
        MinPack.qrsolv(params);
        // 开箱
        x = new Array(params.x);
        return x;
    }

    public static Array prod(SparseMatrix A, Array x) {
        QL_REQUIRE(x.size() == A.size2(),
                "vectors and sparse matrices with different sizes ("
                        + x.size() + ", " + A.size1() + "x" + A.size2() + ") cannot be multiplied");

        double[][] data = {x.toArray()};
        Matrix m = new Matrix(data).transpose();
        return A.multiply(m).getColArray(0);
    }

    /*  returns hypotenuse of real (non-complex) scalars a and b by
        avoiding underflow/overflow
        using (a * sqrt( 1 + (b/a) * (b/a))), rather than
        sqrt(a*a + b*b).
    */
    public static double hypot(final double a, final double b) {
        if (a == 0) {
            return Math.abs(b);
        } else {
            double c = b/a;
            return Math.abs(a) * Math.sqrt(1 + c*c);
        }
    }

    public static void main(String[] args) {
        SparseMatrix a = new SparseMatrix(3, 3);
        a.set(0, 0, 1);
        Array x = new Array(3,10);
        Array b = prod(a, x);
        System.out.println(b);
    }

}
