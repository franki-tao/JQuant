package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.templateImpl;

import static java.lang.Math.exp;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class LogInterpolationImpl extends templateImpl {
    private final double[] logY_;
    private final Interpolation interpolation_;

    public LogInterpolationImpl(double[] x, double[] y, Interpolator factory) {
        super(x, y, factory.getRequiredPoints());
        logY_ = new double[x.length];
        // The inner interpolation is on log(y). value() exponentiates it.
        interpolation_ = factory.interpolate(x, logY_);
    }
    @Override
    public void update() {
        for (int i=0; i<logY_.length; ++i) {
            QL_REQUIRE(yValue[i]>0.0,
                    "invalid value (" + yValue[i] +
                     ") at index " + i);
            logY_[i] = Math.log(yValue[i]);
        }
        interpolation_.update();
    }

    @Override
    public double value(double x) {
        return exp(interpolation_.value(x, true));
    }

    @Override
    public double primitive(double v) {
        QL_FAIL("LogInterpolation primitive not implemented");
        return 0;
    }

    @Override
    public double derivative(double x) {
        return value(x)*interpolation_.derivative(x, true);
    }

    @Override
    public double secondDerivative(double x) {
        return derivative(x)*interpolation_.derivative(x, true) +
                value(x)*interpolation_.secondDerivative(x, true);
    }
}
