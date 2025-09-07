package math.interpolations.impl;

import math.Array;
import math.CommonUtil;
import math.templateImpl;

import static java.lang.Math.abs;
import static math.CommonUtil.QL_FAIL;
import static math.MathUtils.QL_EPSILON;
import static math.MathUtils.close_enough;

public class LagrangeInterpolationImpl extends templateImpl implements UpdatedYInterpolation {

    private int n_;
    private Array lambda_;

    public LagrangeInterpolationImpl(double[] x, double[] y) {
        super(x, y, 2);
        n_ = x.length;
        lambda_ = new Array(n_);
    }

    @Override
    public void update() {
        final double cM1 = 4.0 / (xValue[n_ - 1] - xValue[0]); //(*(this->xEnd_-1) - *(this->xBegin_));

        for (int i = 0; i < n_; ++i) {
            lambda_.set(i, 1.0);

            final double x_i = xValue[i];
            for (int j = 0; j < n_; ++j) {
                if (i != j)
                    lambda_.set(i, lambda_.get(i) * cM1 * (x_i - xValue[j]));
                //lambda_[i] *= cM1 * (x_i - this->xBegin_[j]);
            }
            lambda_.set(i, 1.0 / lambda_.get(i));
            //lambda_[i] = 1.0 / lambda_[i];
        }
    }

    @Override
    public double value(double v) {
        return _value(yValue, v);
    }

    @Override
    public double primitive(double x) {
        double n = 0.0, d = 0.0, nd = 0.0, dd = 0.0;
        for (int i = 0; i < n_; ++i) {
            final double x_i = xValue[i];
            if (close_enough(x, x_i)) {
                double p = 0.0;
                for (int j = 0; j < n_; ++j)
                    if (i != j) {
                        p += lambda_.get(j) / (x - xValue[j]) * (yValue[j] - yValue[i]);
                    }
                return p / lambda_.get(i);
            }
            final double alpha = lambda_.get(i) / (x - x_i);
            final double alphad = -alpha / (x - x_i);
            n += alpha * yValue[i];
            d += alpha;
            nd += alphad * yValue[i];
            dd += alphad;
        }
        return (nd * d - n * dd) / (d * d);
    }

    @Override
    public double derivative(double v) {
        QL_FAIL("LagrangeInterpolation primitive is not implemented");
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        QL_FAIL("LagrangeInterpolation secondDerivative "+
                "is not implemented");
        return 0;
    }

    @Override
    public double value(Array yValues, double x) {
        return _value(yValues.toArray(), x);
    }

    private double _value(double[] y, double x) {
        final double eps = 10 * QL_EPSILON * abs(x);
        int iter = CommonUtil.lowerBound(xValue, x - eps);//std::lower_bound(this->xBegin_, this->xEnd_, x - eps);
        if (iter != xValue.length && xValue[iter] - x < eps) {
            return yValue[iter];
        }

        double n = 0.0, d = 0.0;
        for (int i = 0; i < n_; ++i) {
            final double alpha = lambda_.get(i) / (x - xValue[i]);
            n += alpha * yValue[i];
            d += alpha;
        }
        return n / d;
    }
}
