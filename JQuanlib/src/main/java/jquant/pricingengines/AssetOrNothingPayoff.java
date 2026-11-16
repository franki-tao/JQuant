package jquant.pricingengines;

import jquant.Option;

/**
 * Binary asset-or-nothing payoff.
 *
 * <p>Pays the underlying price when the option finishes in the money.
 * Ported from QuantLib {@code ql/instruments/payoffs.hpp} and
 * {@code payoffs.cpp}.
 */
public class AssetOrNothingPayoff implements StrikedTypePayoff {
    private final Option.Type optionType_;
    private final double strike_;

    public AssetOrNothingPayoff(Option.Type optionType, double strike) {
        optionType_ = optionType;
        strike_ = strike;
    }

    @Override
    public Option.Type optionType() {
        return optionType_;
    }

    @Override
    public double strike() {
        return strike_;
    }

    @Override
    public String name() {
        return "AssetOrNothing";
    }

    @Override
    public String description() {
        return optionType_ + " " + strike_ + " strike";
    }

    @Override
    public double value(double price) {
        return switch (optionType_) {
            case Call -> price - strike_ > 0.0 ? price : 0.0;
            case Put -> strike_ - price > 0.0 ? price : 0.0;
            default -> throw new IllegalArgumentException("unknown/illegal option type");
        };
    }
}
