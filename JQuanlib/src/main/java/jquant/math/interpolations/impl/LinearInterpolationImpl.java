package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.templateImpl;

import java.util.List;

public class LinearInterpolationImpl extends templateImpl {
    private List<Double> primitiveConst_;
    private List<Double> s_;

    public LinearInterpolationImpl(double[] x, double[] y) {
        super(x, y, Linear.requiredPoints);
        primitiveConst_ = CommonUtil.ArrayInit(x.length);
        s_ = CommonUtil.ArrayInit(x.length);
    }

    @Override
    public void update() {
        primitiveConst_.set(0, 0d);
        for (int i = 1; i < super.xValue.length; ++i) {
            double dx = super.xValue[i] - super.xValue[i - 1];
            s_.set(i - 1, (super.yValue[i] - super.yValue[i - 1]) / dx);
            primitiveConst_.set(i, primitiveConst_.get(i - 1) + dx * (super.yValue[i - 1] + 0.5 * dx * s_.get(i - 1)));
        }
    }

    @Override
    public double value(double x) {
        int i = super.locale(x);
        return super.yValue[i] + (x - super.xValue[i]) * s_.get(i);
    }

    @Override
    public double primitive(double x) {
        int i = super.locale(x);
        double dx = x - super.xValue[i];
        return primitiveConst_.get(i) +
                dx * (super.yValue[i] + 0.5 * dx * s_.get(i));
    }

    @Override
    public double derivative(double x) {
        int i = super.locale(x);
        return s_.get(i);
    }

    @Override
    public double secondDerivative(double v) {
        return 0;
    }
}
