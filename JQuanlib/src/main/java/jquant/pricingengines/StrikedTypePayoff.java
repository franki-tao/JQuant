package jquant.pricingengines;

import jquant.Option;
import jquant.Payoff;

/**
 * Intermediate class for put/call payoffs based on a fixed strike.
 *
 * <p>Java counterpart of QuantLib {@code StrikedTypePayoff}
 * ({@code ql/instruments/payoffs.hpp}): it adds a strike to a
 * {@code TypePayoff}.
 */
public interface StrikedTypePayoff extends Payoff {
    Option.Type optionType();

    double strike();
}
