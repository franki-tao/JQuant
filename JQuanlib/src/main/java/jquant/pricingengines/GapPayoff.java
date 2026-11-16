package jquant.pricingengines;

import jquant.Option;

/**
 * Binary gap payoff.
 *
 * <p>Equivalent to being long a plain-vanilla payoff at the first strike
 * and short a cash-or-nothing payoff at the first strike with cash payoff
 * equal to the difference between the second and the first strike.
 * Ported from QuantLib {@code ql/instruments/payoffs.hpp} and
 * {@code payoffs.cpp}.
 */
public class GapPayoff implements StrikedTypePayoff {
    private final Option.Type optionType_;
    private final double strike_;
    private final double secondStrike_;

    public GapPayoff(Option.Type optionType, double strike, double secondStrike) {
        optionType_ = optionType;
        strike_ = strike;
        secondStrike_ = secondStrike;
    }

    @Override
    public Option.Type optionType() {
        return optionType_;
    }

    @Override
    public double strike() {
        return strike_;
    }

    public double secondStrike() {
        return secondStrike_;
    }

    @Override
    public String name() {
        return "Gap";
    }

    @Override
    public String description() {
        return optionType_ + " " + strike_ + " strike, " + secondStrike_ + " strike payoff";
    }

    @Override
    public double value(double price) {
        return switch (optionType_) {
            case Call -> price - strike_ >= 0.0 ? price - secondStrike_ : 0.0;
            case Put -> strike_ - price >= 0.0 ? secondStrike_ - price : 0.0;
            default -> throw new IllegalArgumentException("unknown/illegal option type");
        };
    }
}
