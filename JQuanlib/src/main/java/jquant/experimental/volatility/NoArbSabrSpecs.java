package jquant.experimental.volatility;

import jquant.math.Array;
import jquant.math.interpolations.impl.SABRSpecs;
import jquant.math.interpolations.impl.SarbModel;
import jquant.math.interpolations.impl.XABRModel;

import java.util.List;

import static jquant.math.MathUtils.M_PI;
import static jquant.pricingengines.BlackFormula.blackFormulaStdDevDerivative;

/**
 * Port of QuantLib's {@code detail::NoArbSabrSpecs} from
 * {@code ql/experimental/volatility/noarbsabrinterpolation.hpp}.
 */
public class NoArbSabrSpecs implements SarbModel {

    @Override
    public int dimension() {
        return 4;
    }

    public double eps() {
        return 0.000001;
    }

    @Override
    public void defaultValues(List<Double> params,
                              List<Boolean> paramIsFixed,
                              final double forward,
                              final double expiryTime,
                              final List<Double> addParams) {
        new SABRSpecs().defaultValues(params, paramIsFixed, forward, expiryTime, addParams);
        // check if alpha / beta is admissable, otherwise adjust
        // if possible (i.e. not fixed, otherwise an exception will
        // be thrown from the model constructor anyway)
        double sigmaI = params.get(0) * Math.pow(forward, params.get(1) - 1.0);
        if (sigmaI < NoArbSabrModel.sigmaI_min) {
            if (!paramIsFixed.get(0))
                params.set(0, NoArbSabrModel.sigmaI_min * (1.0 + eps()) /
                        Math.pow(forward, params.get(1) - 1.0));
            else {
                if (!paramIsFixed.get(1))
                    params.set(1, 1.0 +
                            Math.log(NoArbSabrModel.sigmaI_min * (1.0 + eps()) /
                                    params.get(0)) /
                                    Math.log(forward));
            }
        }
        if (sigmaI > NoArbSabrModel.sigmaI_max) {
            if (!paramIsFixed.get(0))
                params.set(0, NoArbSabrModel.sigmaI_max * (1.0 - eps()) /
                        Math.pow(forward, params.get(1) - 1.0));
            else {
                if (!paramIsFixed.get(1))
                    params.set(1, 1.0 +
                            Math.log(NoArbSabrModel.sigmaI_max * (1.0 - eps()) /
                                    params.get(0)) /
                                    Math.log(forward));
            }
        }
    }

    @Override
    public void guess(Array values, final List<Boolean> paramIsFixed,
                      final double forward, final double expiryTime,
                      final List<Double> r, final List<Double> addParams) {
        int j = 0;
        if (!paramIsFixed.get(1))
            values.set(1, NoArbSabrModel.beta_min +
                    (NoArbSabrModel.beta_max - NoArbSabrModel.beta_min) *
                            r.get(j++));
        if (!paramIsFixed.get(0)) {
            double sigmaI = NoArbSabrModel.sigmaI_min +
                    (NoArbSabrModel.sigmaI_max - NoArbSabrModel.sigmaI_min) *
                            r.get(j++);
            sigmaI *= (1.0 - eps());
            sigmaI += eps() / 2.0;
            values.set(0, sigmaI / Math.pow(forward, values.get(1) - 1.0));
        }
        if (!paramIsFixed.get(2))
            values.set(2, NoArbSabrModel.nu_min +
                    (NoArbSabrModel.nu_max - NoArbSabrModel.nu_min) *
                            r.get(j++));
        if (!paramIsFixed.get(3))
            values.set(3, NoArbSabrModel.rho_min +
                    (NoArbSabrModel.rho_max - NoArbSabrModel.rho_min) *
                            r.get(j++));
    }

    @Override
    public Array inverse(final Array y, final List<Boolean> paramIsFixed,
                         final List<Double> params, final double forward) {
        Array x = new Array(4);
        x.set(1, Math.tan((y.get(1) - NoArbSabrModel.beta_min) /
                (NoArbSabrModel.beta_max - NoArbSabrModel.beta_min) *
                M_PI + M_PI / 2.0));
        x.set(0, Math.tan((y.get(0) * Math.pow(forward, y.get(1) - 1.0) -
                NoArbSabrModel.sigmaI_min) /
                (NoArbSabrModel.sigmaI_max - NoArbSabrModel.sigmaI_min) *
                M_PI - M_PI / 2.0));
        x.set(2, Math.tan((y.get(2) - NoArbSabrModel.nu_min) /
                (NoArbSabrModel.nu_max - NoArbSabrModel.nu_min) *
                M_PI + M_PI / 2.0));
        x.set(3, Math.tan((y.get(3) - NoArbSabrModel.rho_min) /
                (NoArbSabrModel.rho_max - NoArbSabrModel.rho_min) *
                M_PI + M_PI / 2.0));
        return x;
    }

    @Override
    public Array direct(final Array x, final List<Boolean> paramIsFixed,
                        final List<Double> params, final double forward) {
        Array y = new Array(4);
        if (paramIsFixed.get(1))
            y.set(1, params.get(1));
        else
            y.set(1, NoArbSabrModel.beta_min +
                    (NoArbSabrModel.beta_max - NoArbSabrModel.beta_min) *
                            (Math.atan(x.get(1)) + M_PI / 2.0) / M_PI);
        // we compute alpha from sigmaI using beta
        // if alpha is fixed we have to check if beta is admissable
        // and adjust if need be
        if (paramIsFixed.get(0)) {
            y.set(0, params.get(0));
            double sigmaI = y.get(0) * Math.pow(forward, y.get(1) - 1.0);
            if (sigmaI < NoArbSabrModel.sigmaI_min) {
                y.set(1, (1.0 +
                        Math.log(NoArbSabrModel.sigmaI_min * (1.0 + eps()) /
                                y.get(0)) /
                                Math.log(forward)));
            }
            if (sigmaI > NoArbSabrModel.sigmaI_max) {
                y.set(1, (1.0 +
                        Math.log(NoArbSabrModel.sigmaI_max * (1.0 - eps()) /
                                y.get(0)) /
                                Math.log(forward)));
            }
        } else {
            double sigmaI = NoArbSabrModel.sigmaI_min +
                    (NoArbSabrModel.sigmaI_max - NoArbSabrModel.sigmaI_min) *
                            (Math.atan(x.get(0)) + M_PI / 2.0) / M_PI;
            y.set(0, sigmaI / Math.pow(forward, y.get(1) - 1.0));
        }
        if (paramIsFixed.get(2))
            y.set(2, params.get(2));
        else
            y.set(2, NoArbSabrModel.nu_min +
                    (NoArbSabrModel.nu_max - NoArbSabrModel.nu_min) *
                            (Math.atan(x.get(2)) + M_PI / 2.0) / M_PI);
        if (paramIsFixed.get(3))
            y.set(3, params.get(3));
        else
            y.set(3, NoArbSabrModel.rho_min +
                    (NoArbSabrModel.rho_max - NoArbSabrModel.rho_min) *
                            (Math.atan(x.get(3)) + M_PI / 2.0) / M_PI);
        return y;
    }

    @Override
    public double weight(final double strike, final double forward,
                         final double stdDev, final List<Double> addParams) {
        return blackFormulaStdDevDerivative(strike, forward, stdDev, 1.0, 0.0);
    }

    @Override
    public XABRModel instance(final double t, final double forward,
                              final List<Double> params,
                              final List<Double> addParams) {
        return new NoArbSabrSmileSection(t, forward, params);
    }
}
