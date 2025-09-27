package jquant.math.interpolations.impl;

import jquant.math.Array;

import java.util.List;

import static jquant.math.MathUtils.M_PI;
import static jquant.pricingengines.BlackFormula.blackFormulaStdDevDerivative;

public class SABRSpecs implements SarbModel {
    @Override
    public int dimension() {
        return 4;
    }

    @Override
    public void defaultValues(List<Double> params, List<Double> temp, double forward, double expiryTime, List<Double> addParams) {
        if (params.get(1) == Double.NaN) {
            params.set(1, 0.5);
        }
        if (params.get(0) == Double.NaN) {
            // adapt alpha to beta level
            params.set(0, 0.2 * (params.get(1) < 0.9999 ?
                    (double) (Math.pow(forward + (addParams.isEmpty() ? 0.0 : addParams.get(0)),
                            1.0 - params.get(1))) : 1.0));
        }
//                    params[0] = 0.2 * (params[1] < 0.9999 ?
//                            Real(std::pow (forward + (addParams.empty() ? 0.0 : addParams[0]),
//                1.0 - params[1])) :
//        1.0);
        if (params.get(2) == Double.NaN) {
            params.set(2, Math.sqrt(0.4));
        }
        // params[2] = std::sqrt (0.4);
        if (params.get(3) == Double.NaN) {
            params.set(3, 0.0);
        }
//            params[3] = 0.0;
    }

    @Override
    public void guess(Array values, List<Boolean> paramIsFixed, double forward, double expiryTime, List<Double> r, List<Double> addParams) {
        int j = 0;
        if (!paramIsFixed.get(1))
            values.set(1, (1.0 - 2E-6) * r.get(j++) + 1E-6);
        //values[1] = (1.0 - 2E-6) * r[j++] + 1E-6;
        if (!paramIsFixed.get(0)) {
            values.set(0, (1.0 - 2E-6) * r.get(j++) + 1E-6);
            //values[0] = (1.0 - 2E-6) * r[j++] + 1E-6; // lognormal vol guess
            // adapt this to beta level
            if (values.get(1) < 0.999)
                values.set(0, values.get(0) * Math.pow(forward + (addParams.isEmpty() ? 0.0 : addParams.get(0)), 1.0 - values.get(1)));
            //values[0] *= std::pow(forward + (addParams.empty() ? 0.0 : addParams[0]), 1.0 - values[1]);
        }
        if (!paramIsFixed.get(2))
            values.set(2, 1.5 * r.get(j++) + 1E-6);
        //values[2] = 1.5 * r[j++] + 1E-6;
        if (!paramIsFixed.get(3))
            values.set(3, (2.0 * r.get(j++) - 1.0) * (1.0 - 1E-6));
        //values[3] = (2.0 * r[j++] - 1.0) * (1.0 - 1E-6);
    }

    @Override
    public double eps1() {
        return .0000001;
    }

    @Override
    public double eps2() {
        return .9999;
    }

    @Override
    public double dilationFactor() {
        return 0.001;
    }

    @Override
    public Array inverse(Array y, List<Boolean> temp, List<Double> tmp, double tp) {
        Array x = new Array(4);
        x.set(0, y.get(0) < 25.0 + eps1() ? (double) (Math.sqrt(y.get(0) - eps1())) : (double) ((y.get(0) - eps1() + 25.0) / 10.0));
        //x[0] = y[0] < 25.0 + eps1() ? Real(std::sqrt(y[0] - eps1())) : Real((y[0] - eps1() + 25.0) / 10.0);
        // y_[1] = std::tan(M_PI*(x[1] - 0.5))/dilationFactor();
        x.set(1, Math.sqrt(-Math.log(y.get(1))));
        // x[1] = std::sqrt(-std::log(y[1]));
        x.set(2, y.get(2) < 25.0 + eps1() ? (double) (Math.sqrt(y.get(2) - eps1())) : (double) ((y.get(2) - eps1() + 25.0) / 10.0));
        // x[2] = y[2] < 25.0 + eps1() ? Real(std::sqrt(y[2] - eps1())) : Real((y[2] - eps1() + 25.0) / 10.0);
        x.set(3, Math.asin(y.get(3) / eps2()));
        // x[3] = std::asin(y[3] / eps2());
        return x;
    }

    @Override
    public Array direct(Array x, List<Boolean> temp, List<Double> tmp, double tp) {
        Array y = new Array(4);
        y.set(0, Math.abs(x.get(0)) < 5.0 ? (double) (x.get(0) * x.get(0) + eps1()) : (double) ((10.0 * Math.abs(x.get(0)) - 25.0) + eps1()));
        // y[0] = std::fabs(x[0]) < 5.0 ? Real(x[0] * x[0] + eps1()) : Real((10.0 * std::fabs(x[0]) - 25.0) + eps1());
        // y_[1] = std::atan(dilationFactor_*x[1])/M_PI + 0.5;
        y.set(1, Math.abs(x.get(1)) < Math.sqrt(-Math.log(eps1())) ? Math.exp(-(x.get(1) * x.get(1))) : eps1());
        // y[1] = std::fabs(x[1]) < std::sqrt(-std::log(eps1())) ? std::exp(-(x[1] * x[1])) : eps1();
        y.set(2, Math.abs(x.get(2)) < 5.0 ? (x.get(2) * x.get(2) + eps1()) : ((10.0 * Math.abs(x.get(2)) - 25.0) + eps1()));
        // y[2] = std::fabs(x[2]) < 5.0 ? Real(x[2] * x[2] + eps1()) : Real((10.0 * std::fabs(x[2]) - 25.0) + eps1());
        y.set(3, Math.abs(x.get(3)) < 2.5 * M_PI ? (eps2() * Math.sin(x.get(3))) : (eps2() * (x.get(3) > 0.0 ? 1.0 : (-1.0))));
        // y[3] = std::fabs(x[3]) < 2.5 * M_PI ? Real(eps2() * std::sin(x[3])): Real(eps2() * (x[3] > 0.0 ? 1.0 : (-1.0)));
        return y;
    }

    @Override
    public double weight(double strike, double forward, double stdDev, List<Double> addParams) {
        return blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, addParams.get(0));
    }

    @Override
    public SABRWrapper instance(double t, double forward, List<Double> params, List<Double> addParams) {
        return new SABRWrapper(t, forward, params, addParams);
    }
}
