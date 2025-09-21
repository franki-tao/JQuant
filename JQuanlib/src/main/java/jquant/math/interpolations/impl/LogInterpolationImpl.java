package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.templateImpl;

import java.util.List;

import static java.lang.Math.exp;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public class LogInterpolationImpl extends templateImpl {
    private List<Double> logY_;
    private Interpolation interpolation_;
    // 此处用来获取泛型中requiredPoints
    private Interpolator interpolator;

    public LogInterpolationImpl(double[] x, double[] y, Interpolator factory) {
        super(x, y, factory.getRequiredPoints());
        logY_ = CommonUtil.ArrayInit(x.length);
        interpolation_ = factory.interpolate(x, y);
    }
    @Override
    public void update() {
        for (int i=0; i<logY_.size(); ++i) {
            QL_REQUIRE(yValue[i]>0.0,
                    "invalid value (" + yValue[i] +
                     ") at index " + i);
            logY_.set(i, Math.log(yValue[i]));
            // logY_[i] = std::log(this->yBegin_[i]);
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
