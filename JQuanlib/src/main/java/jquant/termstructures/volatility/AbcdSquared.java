package jquant.termstructures.volatility;

import jquant.math.Function;

/**
 * Official QuantLib {@code ql/termstructures/volatility/abcd.hpp/.cpp} helper class port.
 *
 * <p>Helper class used by unit tests.
 */
public class AbcdSquared implements Function {

    private final AbcdFunction abcd_;
    private final double T_;
    private final double S_;

    public AbcdSquared(double a, double b, double c, double d, double T, double S) {
        abcd_ = new AbcdFunction(a, b, c, d);
        T_ = T;
        S_ = S;
    }

    @Override
    public double value(double t) {
        return abcd_.covariance(t, T_, S_);
    }
}
