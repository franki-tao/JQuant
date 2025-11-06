package jquant.math.matrixutilities.impl;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.CostFunction;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.transpose;

//cost function for hypersphere and lower-diagonal algorithm
public class HypersphereCostFunction extends CostFunction {
    private int size_;
    private boolean lowerDiagonal_;
    private Matrix targetMatrix_;
    private Array targetVariance_;
    private Matrix currentRoot_, tempMatrix_, currentMatrix_;

    public HypersphereCostFunction(Matrix targetMatrix,
                                   Array targetVariance,
                                   boolean lowerDiagonal) {
        size_ = targetMatrix.rows();
        lowerDiagonal_ = lowerDiagonal;
        targetMatrix_ = targetMatrix;
        targetVariance_ = targetVariance;
        currentRoot_ = new Matrix(size_, size_, Double.NaN);
        tempMatrix_ = new Matrix(size_, size_, Double.NaN);
        currentMatrix_ = new Matrix(size_, size_, Double.NaN);
    }

    @Override
    public Array values(Array x) {
        QL_FAIL("values method not implemented");
        return null;
    }

    @Override
    public double value(Array x) {
        int i, j, k;
        currentRoot_.fill(1.0);
        // fill(currentRoot_.begin(), currentRoot_.end(), 1.0);
        if (lowerDiagonal_) {
            for (i = 0; i < size_; i++) {
                for (k = 0; k < size_; k++) {
                    if (k > i) {
                        currentRoot_.set(i, k, 0);
                        // currentRoot_[i][k]=0;
                    } else {
                        for (j = 0; j <= k; j++) {
                            if (j == k && k != i)
                                currentRoot_.multipyEq(i, k, Math.cos(x.get(i * (i - 1) / 2 + j)));
                                // currentRoot_[i][k] *= std::cos(x[i*(i-1)/2+j]);
                            else if (j != i)
                                currentRoot_.multipyEq(i, k, Math.sin(x.get(i * (i - 1) / 2 + j)));
                            // currentRoot_[i][k] *= std::sin(x[i*(i-1)/2+j]);
                        }
                    }
                }
            }
        } else {
            for (i = 0; i < size_; i++) {
                for (k = 0; k < size_; k++) {
                    for (j = 0; j <= k; j++) {
                        if (j == k && k != size_ - 1)
                            currentRoot_.multipyEq(i, k, Math.cos(x.get(j * size_ + i)));
                            // currentRoot_[i][k] *= std::cos(x[j*size_+i]);
                        else if (j != size_ - 1)
                            currentRoot_.multipyEq(i, k, Math.sin(x.get(j * size_ + i)));
                        // currentRoot_[i][k] *= std::sin(x[j*size_+i]);
                    }
                }
            }
        }
        double temp, error = 0;
        tempMatrix_ = transpose(currentRoot_);
        currentMatrix_ = currentRoot_.multipy(tempMatrix_); //currentRoot_ * tempMatrix_;
        for (i = 0; i < size_; i++) {
            for (j = 0; j < size_; j++) {
                temp = currentMatrix_.get(i, j) * targetVariance_.get(i) * targetVariance_.get(j) - targetMatrix_.get(i, j);
                error += temp * temp;
            }
        }
        return error;
    }
}
