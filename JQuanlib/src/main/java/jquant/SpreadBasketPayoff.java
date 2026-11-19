package jquant;

import jquant.math.Array;

import static jquant.math.CommonUtil.QL_REQUIRE;

/** Spread basket payoff on two underlyings. */
public class SpreadBasketPayoff extends BasketPayoff {
    public SpreadBasketPayoff(Payoff p) {
        super(p);
    }

    @Override
    public double accumulate(Array a) {
        QL_REQUIRE(a.size() == 2,
                "payoff is only defined for two underlyings");
        return a.get(0) - a.get(1);
    }
}
