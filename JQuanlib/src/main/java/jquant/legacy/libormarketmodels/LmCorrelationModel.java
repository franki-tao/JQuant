package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.models.Parameter;

import java.util.List;

//! %libor forward correlation model
public abstract class LmCorrelationModel {
    protected int size_;
    protected List<Parameter> arguments_;

    protected abstract void generateArguments();

    public LmCorrelationModel(int size, int nArguments) {
        size_ = size;
        arguments_ = CommonUtil.ArrayInit(nArguments);
    }

    public int size() {
        return size_;
    }

    public int factors() {
        return size_;
    }

    public List<Parameter> params() {
        return arguments_;
    }

    public void setParams(final List<Parameter> arguments) {
        arguments_ = arguments;
        generateArguments();
    }

    public abstract Matrix correlation(double t, final Array x);

    /** Official default argument {@code x = {}}: empty state array. */
    public Matrix correlation(double t) {
        return correlation(t, new Array(0));
    }

    public Matrix pseudoSqrt(double t, final Array x) {
        return MatrixUtil.pseudoSqrt(correlation(t, x), SalvagingAlgorithm.Type.Spectral);
    }

    /** Official default argument {@code x = {}}: empty state array. */
    public Matrix pseudoSqrt(double t) {
        return pseudoSqrt(t, new Array(0));
    }

    public double correlation(int i, int j, double t, final Array x) {
        // inefficient implementation, please overload in derived classes
        return correlation(t, x).get(i, j);
    }

    /** Official default argument {@code x = {}}: empty state array. */
    public double correlation(int i, int j, double t) {
        return correlation(i, j, t, new Array(0));
    }

    public boolean isTimeIndependent() {
        return false;
    }
}
