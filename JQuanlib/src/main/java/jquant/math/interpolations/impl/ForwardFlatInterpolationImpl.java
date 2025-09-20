package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.templateImpl;

import java.util.List;

public class ForwardFlatInterpolationImpl extends templateImpl {
    private List<Double> primitive_;
    private int n_;

    public ForwardFlatInterpolationImpl(double[] x, double[] y) {
        super(x, y, ForwardFlat.requiredPoints);
        primitive_ = CommonUtil.ArrayInit(x.length);
        n_ = x.length;
    }

    @Override
    public void update() {
        primitive_.set(0, 0.0);
        for (int i = 1; i < n_; i++) {
            double dx = xValue[i] - xValue[i - 1];
            primitive_.set(i, primitive_.get(i - 1) + dx * yValue[i - 1]);
            // primitive_[i] = primitive_[i-1] + dx*this->yBegin_[i-1];
        }
    }

    @Override
    public double value(double x) {
        if (x >= xValue[n_ - 1])
            return yValue[n_ - 1];

        int i = locale(x);
        return yValue[i];
    }

    @Override
    public double primitive(double x) {
        int i = locale(x);
        double dx = x - xValue[i];
        return primitive_.get(i) + dx * yValue[i];
    }

    @Override
    public double derivative(double v) {
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        return 0;
    }
}
