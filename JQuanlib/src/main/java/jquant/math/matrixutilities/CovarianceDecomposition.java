package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Covariance decomposition into correlation and variances
/*! Extracts the correlation matrix and the vector of variances
    out of the input covariance matrix.

    Note that only the lower symmetric part of the covariance matrix is
    used.

    \pre The covariance matrix must be symmetric.

    \test cross checked with getCovariance
*/
public class CovarianceDecomposition {
    private Array variances_, stdDevs_;
    private Matrix correlationMatrix_;

    /*! \pre covarianceMatrix must be symmetric */
    // default tolerance = 1.0e-12
    public CovarianceDecomposition(Matrix covarianceMatrix,
                                   double tolerance) {
        variances_ = new Array(covarianceMatrix.diagonal());
        stdDevs_ = new Array(covarianceMatrix.rows());
        correlationMatrix_ = new Matrix(covarianceMatrix.rows(), covarianceMatrix.rows(), Double.NaN);
        int size = covarianceMatrix.rows();
        QL_REQUIRE(size==covarianceMatrix.cols(),
                "input covariance matrix must be square, it is [" +
                        size + "x" + covarianceMatrix.rows() + "]");

        for (int i=0; i<size; ++i)
        {
            stdDevs_.set(i, Math.sqrt(variances_.get(i)));
            // stdDevs_[i] = std::sqrt(variances_[i]);
            correlationMatrix_.set(i,i,1d);
            for (int j=0; j<i; ++j)
            {
                QL_REQUIRE(Math.abs(covarianceMatrix.get(i,j)-covarianceMatrix.get(j,i)) <= tolerance,
                    "invalid covariance matrix:" +
                            "\nc[" + i + ", " + j + "] = " +
                            covarianceMatrix.get(i,j) + "\nc[" + j + ", " + i +
                            "] = " + covarianceMatrix.get(j,i));
                correlationMatrix_.set(i,j, covarianceMatrix.get(i,j)/(stdDevs_.get(i)*stdDevs_.get(j)));
                correlationMatrix_.set(j,i, correlationMatrix_.get(i,j));
                // correlationMatrix_[i][j] = correlationMatrix_[j][i] = cov[i][j]/(stdDevs_[i]*stdDevs_[j]);
            }
        }
    }

    /*! returns the variances Array */
    public final Array variances() { return variances_; }
    /*! returns the standard deviations Array */
    public final Array standardDeviations() {return stdDevs_; }
    /*! returns the correlation matrix */
    public final Matrix correlationMatrix() { return correlationMatrix_; }
}
