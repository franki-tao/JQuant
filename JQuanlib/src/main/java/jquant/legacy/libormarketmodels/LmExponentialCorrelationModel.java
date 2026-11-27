package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.ConstantParameter;

/**
 * Exponential correlation model.
 *
 * <p>Official QuantLib
 * {@code ql/legacy/libormarketmodels/lmexpcorrmodel.hpp/.cpp} port:
 * {@code rho_{i,j} = exp(-beta*|i-j|)}.
 */
public class LmExponentialCorrelationModel extends LmCorrelationModel {

    private Matrix corrMatrix_;
    private Matrix pseudoSqrt_;

    public LmExponentialCorrelationModel(int size, double rho) {
        super(size, 1);
        corrMatrix_ = new Matrix(size_, size_, 0.0);
        pseudoSqrt_ = new Matrix(size_, size_, 0.0);
        arguments_.set(0, new ConstantParameter(rho, new PositiveConstraint()));
        generateArguments();
    }

    @Override
    public Matrix correlation(double t, final Array x) {
        return new Matrix(corrMatrix_.matrix.copy());
    }

    @Override
    public double correlation(int i, int j, double t, final Array x) {
        return corrMatrix_.get(i, j);
    }

    @Override
    public boolean isTimeIndependent() {
        return true;
    }

    @Override
    public Matrix pseudoSqrt(double t, final Array x) {
        return new Matrix(pseudoSqrt_.matrix.copy());
    }

    @Override
    protected void generateArguments() {
        final double rho = arguments_.get(0).value(0.0);

        for (int i = 0; i < size_; ++i) {
            for (int j = i; j < size_; ++j) {
                final double value = Math.exp(-rho * Math.abs((double) i - (double) j));
                corrMatrix_.set(i, j, value);
                corrMatrix_.set(j, i, value);
            }
        }

        pseudoSqrt_ = MatrixUtil.pseudoSqrt(corrMatrix_,
                SalvagingAlgorithm.Type.Spectral);
    }
}
