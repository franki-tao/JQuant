package jquant.pricingengines;

import jquant.Option;
import jquant.Payoff;

/**
 * Vanilla strike payoff. Cash-or-nothing, asset-or-nothing and gap payoffs
 * are not represented here.
 */
public class PlainVanillaPayoff implements Payoff {
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

    @Override
    public String name() {
        return "Vanilla";
    }

    @Override
    public String description() {
        return optionType_ + " " + strike_;
    }

    @Override
    public double value(double price) {
        if (optionType_ == Option.Type.Call) {
            return Math.max(price - strike_, 0.0);
        }
        return Math.max(strike_ - price, 0.0);
    }
}
