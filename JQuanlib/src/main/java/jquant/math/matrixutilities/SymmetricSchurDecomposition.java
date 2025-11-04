package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.Pair;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! symmetric threshold Jacobi algorithm.
/*! Given a real symmetric matrix S, the Schur decomposition
    finds the eigenvalues and eigenvectors of S. If D is the
    diagonal matrix formed by the eigenvalues and U the
    unitarian matrix of the eigenvectors we can write the
    Schur decomposition as
    \f[ S = U \cdot D \cdot U^T \, ,\f]
    where \f$ \cdot \f$ is the standard matrix product
    and  \f$ ^T  \f$ is the transpose operator.
    This class implements the Schur decomposition using the
    symmetric threshold Jacobi algorithm. For details on the
    different Jacobi transfomations see "Matrix computation,"
    second edition, by Golub and Van Loan,
    The Johns Hopkins University Press

    \test the correctness of the returned values is tested by
          checking their properties.
*/
public class SymmetricSchurDecomposition {
    private Array diagonal_;
    private Matrix eigenVectors_;

    public SymmetricSchurDecomposition(Matrix s) {
        diagonal_ = new Array(s.rows());
        eigenVectors_ = new Matrix(s.rows(), s.cols(), 0.0);
        QL_REQUIRE(s.rows() > 0 && s.cols() > 0, "null matrix given");
        QL_REQUIRE(s.rows() == s.cols(), "input matrix must be square");
        int size = s.rows();
        for (int q = 0; q < size; q++) {
            diagonal_.set(q, s.get(q, q));
            eigenVectors_.set(q, q, 1.0);
        }
        Matrix ss = new Matrix(s.matrix);

        List<Double> tmpDiag = diagonal_.getList();
        List<Double> tmpAccumulate = CommonUtil.ArrayInit(size, 0.0);
        double threshold, epsPrec = 1e-15;
        boolean keeplooping = true;
        int maxIterations = 100, ite = 1;
        do {
            //main loop
            double sum = 0;
            for (int a = 0; a < size - 1; a++) {
                for (int b = a + 1; b < size; b++) {
                    sum += Math.abs(ss.get(a, b));
                }
            }

            if (sum == 0) {
                keeplooping = false;
            } else {
                /* To speed up computation a threshold is introduced to
                   make sure it is worthy to perform the Jacobi rotation
                */
                if (ite < 5) threshold = 0.2 * sum / (size * size);
                else threshold = 0.0;

                int j, k, l;
                for (j = 0; j < size - 1; j++) {
                    for (k = j + 1; k < size; k++) {
                        double sine, rho, cosin, heig, tang, beta;
                        double smll = Math.abs(ss.get(j, k));
                        if (ite > 5 &&
                                smll < epsPrec * Math.abs(diagonal_.get(j)) &&
                                smll < epsPrec * Math.abs(diagonal_.get(k))) {
                            ss.set(j, k, 0d);
                        } else if (Math.abs(ss.get(j, k)) > threshold) {
                            heig = diagonal_.get(k) - diagonal_.get(j);
                            if (smll < epsPrec * Math.abs(heig)) {
                                tang = ss.get(j, k) / heig;
                            } else {
                                beta = 0.5 * heig / ss.get(j, k);
                                tang = 1.0 / (Math.abs(beta) +
                                        Math.sqrt(1 + beta * beta));
                                if (beta < 0)
                                    tang = -tang;
                            }
                            cosin = 1 / Math.sqrt(1 + tang * tang);
                            sine = tang * cosin;
                            rho = sine / (1 + cosin);
                            heig = tang * ss.get(j, k);
                            tmpAccumulate.set(j, tmpAccumulate.get(j) - heig);
                            tmpAccumulate.set(k, tmpAccumulate.get(k) + heig);
                            diagonal_.subtractEq(j, heig);
                            diagonal_.addEq(k, heig);
                            ss.set(j, k, 0.0);
                            for (l = 0; l + 1 <= j; l++)
                                jacobiRotate_(ss, rho, sine, l, j, l, k);
                            for (l = j + 1; l <= k - 1; l++)
                                jacobiRotate_(ss, rho, sine, j, l, l, k);
                            for (l = k + 1; l < size; l++)
                                jacobiRotate_(ss, rho, sine, j, l, k, l);
                            for (l = 0; l < size; l++)
                                jacobiRotate_(eigenVectors_,
                                        rho, sine, l, j, l, k);
                        }
                    }
                }
                for (k = 0; k < size; k++) {
                    tmpDiag.set(k, tmpDiag.get(k) + tmpAccumulate.get(k));
                    diagonal_.set(k, tmpDiag.get(k));
                    tmpAccumulate.set(k, 0.0);
                }
            }
        } while (++ite <= maxIterations && keeplooping);

        QL_REQUIRE(ite<=maxIterations, "Too many iterations (" + maxIterations + ") reached");


        // sort (eigenvalues, eigenvectors)
        List<Pair> temp = CommonUtil.ArrayInit(size);
        List<Double> eigenVector = CommonUtil.ArrayInit(size);
        int row, col;
        for (col=0; col<size; col++) {
            for (int i = 0; i < eigenVectors_.rows(); i++) {
                eigenVector.set(i, eigenVectors_.get(i, col));
            }
            // std::copy(eigenVectors_.column_begin(col), eigenVectors_.column_end(col), eigenVector.begin());
            temp.set(col, new Pair(diagonal_.get(col), eigenVector));
            // temp[col] = std::make_pair(diagonal_[col], eigenVector);
        }
        Collections.sort(temp);
        double maxEv = temp.get(0).getFirst();
        for (col=0; col<size; col++) {
            // check for round-off errors
            diagonal_.set(col, (Math.abs(temp.get(col).getFirst()/maxEv)<1e-16 ? 0.0 : temp.get(col).getFirst()));
            double sign = 1.0;
            if (temp.get(col).getSecond().get(0)<0.0)
                sign = -1.0;
            for (row=0; row<size; row++) {
                eigenVectors_.set(row,col, sign *  temp.get(col).getSecond().get(row));
            }
        }
    }

    public final Array eigenvalues() { return diagonal_; }
    public final Matrix eigenvectors() { return eigenVectors_; }

    private void jacobiRotate_(Matrix  m, double rot, double dil,
                               int j1, int k1, int j2, int k2) {
        double x1, x2;
        x1 = m.get(j1,k1); //[j1][k1];
        x2 = m.get(j2,k2); //[j2][k2];
        m.set(j1,k1, x1 - dil*(x2 + x1*rot));
        m.set(j2,k2, x2 + dil*(x1 - x2*rot));
    }

}
