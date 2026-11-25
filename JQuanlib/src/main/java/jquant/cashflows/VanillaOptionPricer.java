package jquant.cashflows;

import jquant.Option;

/**
 * Vanilla option pricer used by Hagan's numerical CMS replication.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp}
 * ({@code VanillaOptionPricer}). Official {@code operator()(strike, type,
 * deflator)} is {@link #price(double, Option.Type, double)} here.
 */
public interface VanillaOptionPricer {

    double price(double strike, Option.Type optionType, double deflator);
}
