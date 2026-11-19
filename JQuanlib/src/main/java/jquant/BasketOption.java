package jquant;

import jquant.pricingengines.GenericEngine;

/**
 * Option on a weighted basket of assets.
 *
 * <p>Ported from QuantLib {@code ql/instruments/basketoption.{hpp,cpp}}.
 */
public class BasketOption extends MultiAssetOption {
    public BasketOption(BasketPayoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    /** Base class for basket-option engines. */
    public abstract static class engine
            extends GenericEngine<Option.arguments, MultiAssetOption.results> {
        protected engine() {
            super(new Option.arguments(), new MultiAssetOption.results());
        }
    }
}
