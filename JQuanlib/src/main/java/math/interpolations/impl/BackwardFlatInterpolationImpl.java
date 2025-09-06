package math.interpolations.impl;

import math.CommonUtil;
import math.templateImpl;

import java.util.List;

public class BackwardFlatInterpolationImpl extends templateImpl {

    private List<Double> primitive_;

    public BackwardFlatInterpolationImpl(double[] x, double[] y) {
        super(x, y, BackwardFlat.requiredPoints);
        primitive_ = CommonUtil.ArrayInit(x.length);
    }

    @Override
    public void update() {
        int n = super.xValue.length;
        primitive_.set(0, 0.0);
        for (int i = 1; i < n; i++) {
            double dx = xValue[i] - xValue[i - 1];
            primitive_.set(i, primitive_.get(i - 1) + dx * yValue[i]);
//            primitive_[i] = primitive_[i-1] + dx*this->yBegin_[i];
        }
    }

    @Override
    public double value(double x) {
        if (x <= xValue[0] || xValue.length == 1)
            return yValue[0];

        int i = super.locale(x);
        if (x == xValue[i])
            return yValue[i];
        else
            return yValue[i + 1];
    }

    @Override
    public double primitive(double x) {
        if (xValue.length == 1)
            return (x - xValue[0]) * yValue[0];
        int i = locale(x);
        double dx = x - xValue[i];
        return primitive_.get(i) + dx * yValue[i + 1];
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
