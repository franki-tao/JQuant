package jquant;

import jquant.math.Array;

/** Maximum-element basket payoff. */
public class MaxBasketPayoff extends BasketPayoff {
    public MaxBasketPayoff(Payoff p) {
        super(p);
    }

    @Override
    public double accumulate(Array a) {
        double max = a.get(0);
        for (int i = 1; i < a.size(); ++i) {
            max = Math.max(max, a.get(i));
        }
        return max;
    }
}
