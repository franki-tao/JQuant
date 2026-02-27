package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.models.Parameter;

import java.util.List;

//! caplet volatility model
public abstract class LmVolatilityModel {
    protected int size_;
    protected List<Parameter> arguments_;

    public LmVolatilityModel(int size, int nArguments) {
        size_ = size;
        arguments_ = CommonUtil.ArrayInit(nArguments);
    }
    public int size() {
        return size_;
    }

    public List<Parameter> params() {
        return arguments_;
    }

    public void setParams(final List<Parameter> arguments) {
        arguments_ = arguments;
        generateArguments();
    }

    public abstract Array volatility(double t, final Array x);
    public double volatility(int i, double t, final Array x) {
        // inefficient implementation, please overload in derived classes
        return volatility(t,x).get(i);
    }
    public abstract double integratedVariance(int i, int j, double u, final Array x);

    protected abstract void generateArguments();
}
