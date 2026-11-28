package jquant.math.interpolations.impl;

import jquant.math.Array;
import jquant.termstructures.volatility.ZabrEvaluation;

import java.util.List;

import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.pricingengines.BlackFormula.blackFormulaStdDevDerivative;

/**
 * ZABR model specification for {@link XABRInterpolationImpl}.
 *
 * <p>Ported from the {@code detail::ZabrSpecs} template in QuantLib
 * {@code ql/math/interpolations/zabrinterpolation.hpp}.
 */
public class ZabrSpecs implements SarbModel {

    private final ZabrEvaluation evaluation_;

    public ZabrSpecs(ZabrEvaluation evaluation) {
        evaluation_ = evaluation;
    }

    @Override
    public int dimension() {
        return 5;
    }

    @Override
    public void defaultValues(List<Double> params, List<Boolean> paramIsFixed,
                              double forward, double expiryTime,
                              List<Double> addParams) {
        if (params.get(1) == NULL_REAL)
            params.set(1, 0.5);
        if (params.get(0) == NULL_REAL)
            // adapt alpha to beta level
            params.set(0, 0.2 * (params.get(1) < 0.9999
                    ? Math.pow(forward, 1.0 - params.get(1)) : 1.0));
        if (params.get(2) == NULL_REAL)
            params.set(2, Math.sqrt(0.4));
        if (params.get(3) == NULL_REAL)
            params.set(3, 0.0);
        if (params.get(4) == NULL_REAL)
            params.set(4, 1.0);
    }

    @Override
    public void guess(Array values, List<Boolean> paramIsFixed,
                      double forward, double expiryTime,
                      List<Double> r, List<Double> addParams) {
        int j = 0;
        if (!paramIsFixed.get(1))
            values.set(1, (1.0 - 2E-6) * r.get(j++) + 1E-6);
        if (!paramIsFixed.get(0)) {
            values.set(0, (1.0 - 2E-6) * r.get(j++) + 1E-6); // lognormal vol guess
            // adapt this to beta level
            if (values.get(1) < 0.999)
                values.set(0, values.get(0) * Math.pow(forward, 1.0 - values.get(1)));
        }
        if (!paramIsFixed.get(2))
            values.set(2, 1.5 * r.get(j++) + 1E-6);
        if (!paramIsFixed.get(3))
            values.set(3, (2.0 * r.get(j++) - 1.0) * (1.0 - 1E-6));
        if (!paramIsFixed.get(4))
            values.set(4, r.get(j++) * 2.0);
    }

    public double eps1() {
        return .0000001;
    }

    public double eps2() {
        return .9999;
    }

    public double dilationFactor() {
        return 0.001;
    }

    @Override
    public Array inverse(Array y, List<Boolean> temp, List<Double> tmp, double tp) {
        final Array x = new Array(5);
        x.set(0, y.get(0) < 25.0 + eps1() ? Math.sqrt(y.get(0) - eps1())
                : (y.get(0) - eps1() + 25.0) / 10.0);
        x.set(1, Math.sqrt(-Math.log(y.get(1))));
        x.set(2, Math.tan(M_PI * (y.get(2) / 5.0 - 0.5)));
        x.set(3, Math.asin(y.get(3) / eps2()));
        x.set(4, Math.tan(M_PI * (y.get(4) / 1.9 - 0.5)));
        return x;
    }

    @Override
    public Array direct(Array x, List<Boolean> temp, List<Double> tmp, double tp) {
        final Array y = new Array(5);
        y.set(0, Math.abs(x.get(0)) < 5.0 ? x.get(0) * x.get(0) + eps1()
                : (10.0 * Math.abs(x.get(0)) - 25.0) + eps1());
        y.set(1, Math.abs(x.get(1)) < Math.sqrt(-Math.log(eps1()))
                ? Math.exp(-(x.get(1) * x.get(1))) : eps1());
        // limit nu to 5.00
        y.set(2, (Math.atan(x.get(2)) / M_PI + 0.5) * 5.0);
        y.set(3, Math.abs(x.get(3)) < 2.5 * M_PI
                ? eps2() * Math.sin(x.get(3))
                : eps2() * (x.get(3) > 0.0 ? 1.0 : -1.0));
        // limit gamma to 1.9
        y.set(4, (Math.atan(x.get(4)) / M_PI + 0.5) * 1.9);
        return y;
    }

    @Override
    public double weight(double strike, double forward, double stdDev,
                         List<Double> addParams) {
        return blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, 0.0);
    }

    @Override
    public XABRModel instance(double t, double forward, List<Double> params,
                              List<Double> addParams) {
        return new ZabrWrapper(t, forward, params, evaluation_);
    }
}
