package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class BasisIncompleteOrdered {
    private List<Array> currentBasis_;
    private int euclideanDimension_;
    private Array newVector_;

    public BasisIncompleteOrdered(int euclideanDimension) {
        euclideanDimension_ = euclideanDimension;
    }

    //! return value indicates if the vector was linearly independent
    public boolean addVector(final Array newVector1) {
        QL_REQUIRE(newVector1.size() == euclideanDimension_,
                "missized vector passed to " +
                        "BasisIncompleteOrdered::addVector");

        newVector_ = newVector1;

        if (currentBasis_.size() == euclideanDimension_)
            return false;

        for (Array currentBasi : currentBasis_) {
            double innerProd = 0d;
            for (int i = 0; i < newVector_.size(); i++) {
                innerProd += newVector_.get(i) * currentBasi.get(i);
            }
            // Real innerProd = std::inner_product(newVector_.begin(), newVector_.end(), currentBasi.begin(), Real(0.0));

            for (int k = 0; k < euclideanDimension_; ++k) {
                newVector_.set(k, newVector_.get(k) - innerProd * currentBasi.get(k));
                // newVector_[k] -= innerProd * currentBasi[k];
            }
        }
        double norm = CommonUtil.DotProduct(newVector_, newVector_);
        // Real norm = std::sqrt(std::inner_product(newVector_.begin(), newVector_.end(), newVector_.begin(), Real(0.0)));

        if (norm < 1e-12) // maybe this should be a tolerance
            return false;

        for (int l = 0; l < euclideanDimension_; ++l)
            newVector_.set(l, newVector_.get(l) / norm);

        currentBasis_.add(newVector_);

        return true;
    }

    public int basisSize() {
        return currentBasis_.size();
    }

    public int euclideanDimension() {
        return euclideanDimension_;
    }

    public Matrix getBasisAsRowsInMatrix() {
        Matrix basis = new Matrix(currentBasis_.size(), euclideanDimension_, Double.NaN);
        for (int i = 0; i < basis.rows(); ++i)
            for (int j = 0; j < basis.cols(); ++j)
                basis.set(i,j, currentBasis_.get(i).get(j));
//                basis[i][j] = currentBasis_[i][j];

        return basis;
    }
}
