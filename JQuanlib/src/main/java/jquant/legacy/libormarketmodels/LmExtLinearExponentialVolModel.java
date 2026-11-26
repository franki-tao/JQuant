package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.ConstantParameter;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/legacy/libormarketmodels/lmextlinexpvolmodel.hpp/.cpp} port.
 *
 * <p>Extended linear exponential volatility model:
 * {@code sigma_i(t) = k_i*((a*(T_i-t)+d)*exp(-b*(T_i-t)) + c)}.
 */
public class LmExtLinearExponentialVolModel extends LmLinearExponentialVolatilityModel {

    public LmExtLinearExponentialVolModel(final List<Double> fixingTimes,
                                          double a, double b, double c, double d) {
        super(fixingTimes, a, b, c, d);

        for (int i = 0; i < size_; ++i)
            arguments_.add(new ConstantParameter(1.0, new PositiveConstraint()));
    }

    @Override
    public Array volatility(double t, final Array x) {
        Array tmp = super.volatility(t, x);
        for (int i = 0; i < size_; ++i)
            tmp.set(i, tmp.get(i) * arguments_.get(i + 4).value(0.0));

        return tmp;
    }

    @Override
    public double volatility(int i, double t, final Array x) {
        return arguments_.get(i + 4).value(0.0)
                * super.volatility(i, t, x);
    }

    @Override
    public double integratedVariance(int i, int j, double u, final Array x) {
        return arguments_.get(i + 4).value(0.0) * arguments_.get(j + 4).value(0.0)
                * super.integratedVariance(i, j, u, x);
    }
}
