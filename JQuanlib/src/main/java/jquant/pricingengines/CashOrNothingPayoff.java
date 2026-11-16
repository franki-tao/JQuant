package jquant.pricingengines;

import jquant.Option;

/**
 * Binary cash-or-nothing payoff.
 *
 * <p>Pays a fixed cash amount when the option finishes in the money.
 * Ported from QuantLib {@code ql/instruments/payoffs.hpp} and
 * {@code payoffs.cpp}.
 */
public class CashOrNothingPayoff implements StrikedTypePayoff {
    private final Option.Type optionType_;
    private final double strike_;
    private final double cashPayoff_;

    public CashOrNothingPayoff(Option.Type optionType, double strike, double cashPayoff) {
        optionType_ = optionType;
        strike_ = strike;
        cashPayoff_ = cashPayoff;
    }

    @Override
    public Option.Type optionType() {
        return optionType_;
    }

    @Override
    public double strike() {
        return strike_;
    }

    public double cashPayoff() {
        return cashPayoff_;
    }

    @Override
    public String name() {
        return "CashOrNothing";
    }

    @Override
    public String description() {
        return optionType_ + " " + strike_ + " strike, " + cashPayoff_ + " cash payoff";
    }

    @Override
    public double value(double price) {
        return switch (optionType_) {
            case Call -> price - strike_ > 0.0 ? cashPayoff_ : 0.0;
            case Put -> strike_ - price > 0.0 ? cashPayoff_ : 0.0;
            default -> throw new IllegalArgumentException("unknown/illegal option type");
        };
    }
}
