package jquant;

import jquant.math.Array;

/**
 * Base class for basket payoffs.
 *
 * <p>Ported from QuantLib {@code ql/instruments/basketoption.hpp}.
 */
public abstract class BasketPayoff implements Payoff {
    private final Payoff basePayoff_;

    protected BasketPayoff(Payoff p) {
        basePayoff_ = p;
    }

    @Override
    public String name() {
        return basePayoff_.name();
    }

    @Override
    public String description() {
        return basePayoff_.description();
    }

    @Override
    public double value(double price) {
        return basePayoff_.value(price);
    }

    public double value(Array a) {
        return basePayoff_.value(accumulate(a));
    }

    public abstract double accumulate(Array a);

    public Payoff basePayoff() {
        return basePayoff_;
    }
}
