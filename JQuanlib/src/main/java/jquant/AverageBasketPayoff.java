package jquant;

import jquant.math.Array;

import static jquant.math.CommonUtil.DotProduct;

/** Weighted-average basket payoff. */
public class AverageBasketPayoff extends BasketPayoff {
    private final Array weights_;

    public AverageBasketPayoff(Payoff p, Array weights) {
        super(p);
        weights_ = new Array(weights);
    }

    public AverageBasketPayoff(Payoff p, int n) {
        this(p, new Array(n, 1.0 / n));
    }

    @Override
    public double accumulate(Array a) {
        return DotProduct(weights_, a);
    }

    public Array weights() {
        return weights_;
    }
}
