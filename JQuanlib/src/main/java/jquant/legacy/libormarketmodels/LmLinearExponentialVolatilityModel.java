package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.ConstantParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/legacy/libormarketmodels/lmlinexpvolmodel.hpp/.cpp} port.
 *
 * <p>Linear exponential volatility model:
 * {@code sigma_i(t) = (a*(T_i-t)+d)*exp(-b*(T_i-t)) + c}.
 */
public class LmLinearExponentialVolatilityModel extends LmVolatilityModel {

    protected final List<Double> fixingTimes_;

    public LmLinearExponentialVolatilityModel(final List<Double> fixingTimes,
                                              double a, double b, double c, double d) {
        super(fixingTimes.size(), 4);
        fixingTimes_ = new ArrayList<>(fixingTimes);
        arguments_.set(0, new ConstantParameter(a, new PositiveConstraint()));
        arguments_.set(1, new ConstantParameter(b, new PositiveConstraint()));
        arguments_.set(2, new ConstantParameter(c, new PositiveConstraint()));
        arguments_.set(3, new ConstantParameter(d, new PositiveConstraint()));
    }

    @Override
    public Array volatility(double t, final Array x) {
        final double a = arguments_.get(0).value(0.0);
        final double b = arguments_.get(1).value(0.0);
        final double c = arguments_.get(2).value(0.0);
        final double d = arguments_.get(3).value(0.0);

        Array tmp = new Array(size_, 0.0);

        for (int i = 0; i < size_; ++i) {
            final double T = fixingTimes_.get(i);
            if (T > t) {
                tmp.set(i, (a * (T - t) + d) * Math.exp(-b * (T - t)) + c);
            }
        }

        return tmp;
    }

    @Override
    public double volatility(int i, double t, final Array x) {
        final double a = arguments_.get(0).value(0.0);
        final double b = arguments_.get(1).value(0.0);
        final double c = arguments_.get(2).value(0.0);
        final double d = arguments_.get(3).value(0.0);

        final double T = fixingTimes_.get(i);

        return (T > t) ? (a * (T - t) + d) * Math.exp(-b * (T - t)) + c : 0.0;
    }

    @Override
    public double integratedVariance(int i, int j, double u, final Array x) {
        final double a = arguments_.get(0).value(0.0);
        final double b = arguments_.get(1).value(0.0);
        final double c = arguments_.get(2).value(0.0);
        final double d = arguments_.get(3).value(0.0);

        final double T = fixingTimes_.get(i);
        final double S = fixingTimes_.get(j);

        final double k1 = Math.exp(b * u);
        final double k2 = Math.exp(b * S);
        final double k3 = Math.exp(b * T);

        return (a * a * (-1 - 2 * b * b * S * T - b * (S + T)
                + k1 * k1 * (1 + b * (S + T - 2 * u) + 2 * b * b * (S - u) * (T - u)))
                + 2 * b * b * (2 * c * d * (k2 + k3) * (k1 - 1)
                + d * d * (k1 * k1 - 1) + 2 * b * c * c * k2 * k3 * u)
                + 2 * a * b * (d * (-1 - b * (S + T) + k1 * k1 * (1 + b * (S + T - 2 * u)))
                - 2 * c * (k3 * (1 + b * S) + k2 * (1 + b * T)
                - k1 * k3 * (1 + b * (S - u))
                - k1 * k2 * (1 + b * (T - u)))
        )
        ) / (4 * b * b * b * k2 * k3);
    }

    @Override
    protected void generateArguments() {
    }
}
