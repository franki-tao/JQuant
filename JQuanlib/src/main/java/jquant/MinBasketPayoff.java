package jquant;

import jquant.math.Array;

/** Minimum-element basket payoff. */
public class MinBasketPayoff extends BasketPayoff {
    public MinBasketPayoff(Payoff p) {
        super(p);
    }

    @Override
    public double accumulate(Array a) {
        double min = a.get(0);
        for (int i = 1; i < a.size(); ++i) {
            min = Math.min(min, a.get(i));
        }
        return min;
    }
}
