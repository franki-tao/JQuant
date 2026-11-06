package jquant.pricingengines;

import jquant.Option;

/**
 * Vanilla strike payoff used by {@link BlackCalculator}. Exotic payoffs
 * (cash-or-nothing, asset-or-nothing, gap) are not represented here.
 */
public class PlainVanillaPayoff {
    private final Option.Type optionType_;
    private final double strike_;

    public PlainVanillaPayoff(Option.Type optionType, double strike) {
        optionType_ = optionType;
        strike_ = strike;
    }

    public Option.Type optionType() {
        return optionType_;
    }

    public double strike() {
        return strike_;
    }
}
