package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.math.optimization.BoundaryConstraint;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.ConstantParameter;

/**
 * Linear exponential correlation model.
 *
 * <p>Official QuantLib
 * {@code ql/legacy/libormarketmodels/lmlinexpcorrmodel.hpp/.cpp} port:
 * {@code rho_{i,j} = rho + (1-rho)*exp(-beta*|i-j|)}. The correlation matrix
 * is replaced by the rank-reduced reconstruction
 * {@code pseudoSqrt*pseudoSqrt^T}, as official.
 */
public class LmLinearExponentialCorrelationModel extends LmCorrelationModel {

    private Matrix corrMatrix_;
    private Matrix pseudoSqrt_;
    private final int factors_;

    public LmLinearExponentialCorrelationModel(int size, double rho, double beta) {
        this(size, rho, beta, size);
    }

    public LmLinearExponentialCorrelationModel(int size, double rho, double beta,
                                               int factors) {
        super(size, 2);
        corrMatrix_ = new Matrix(size_, size_, 0.0);
        pseudoSqrt_ = new Matrix(size_, size_, 0.0);
        factors_ = factors;
        arguments_.set(0, new ConstantParameter(rho,
                new BoundaryConstraint(-1.0, 1.0)));
        arguments_.set(1, new ConstantParameter(beta, new PositiveConstraint()));
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
    public int factors() {
        return factors_;
    }

    @Override
    public Matrix pseudoSqrt(double t, final Array x) {
        return new Matrix(pseudoSqrt_.matrix.copy());
    }

    @Override
    protected void generateArguments() {
        final double rho = arguments_.get(0).value(0.0);
        final double beta = arguments_.get(1).value(0.0);

        for (int i = 0; i < size_; ++i) {
            for (int j = i; j < size_; ++j) {
                final double value = rho
                        + (1 - rho) * Math.exp(-beta * Math.abs((double) i - (double) j));
                corrMatrix_.set(i, j, value);
                corrMatrix_.set(j, i, value);
            }
        }

        pseudoSqrt_ = MatrixUtil.rankReducedSqrt(corrMatrix_, factors_,
                1.0, SalvagingAlgorithm.Type.None);

        corrMatrix_ = pseudoSqrt_.multipy(pseudoSqrt_.transpose());
    }
}
