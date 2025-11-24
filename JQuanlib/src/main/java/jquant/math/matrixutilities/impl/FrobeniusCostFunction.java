package jquant.math.matrixutilities.impl;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.CostFunction;

import static jquant.math.CommonUtil.DotProduct;

// Cost function associated with Frobenius norm.
// <http://en.wikipedia.org/wiki/Matrix_norm>
public class FrobeniusCostFunction extends CostFunction {
    private Matrix target_;
    private FrobeniusFunc f_;
    private int matrixSize_;
    private int rank_;

    public FrobeniusCostFunction(final Matrix target, final FrobeniusFunc f, int matrixSize, int rank) {
        target_ = target;
        f_ = f;
        matrixSize_ = matrixSize;
        rank_ = rank;
    }

    @Override
    public double value(Array x) {
        Array temp = values(x);
        return DotProduct(temp, temp);
    }

    @Override
    public Array values(Array x) {
        Array result = new Array((target_.rows() * (target_.cols() - 1)) / 2);
        Matrix pseudoRoot = f_.value(x, matrixSize_, rank_);
        Matrix differences = pseudoRoot.multipy(pseudoRoot.transpose()).subtract(target_);
        int k = 0;
        // then we store the elementwise differences in a vector.
        for (int i = 0; i < target_.rows(); ++i) {
            for (int j = 0; j < i; ++j) {
                result.set(k, differences.get(i, j));
                ++k;
            }
        }
        return result;
    }
}
