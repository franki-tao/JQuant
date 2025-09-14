package jquant.math.distributions;

import static jquant.math.MathUtils.incompleteGammaFunction;

public class CumulativePoissonDistribution {
    private double mu_;

    public CumulativePoissonDistribution(double mu) {
        this.mu_ = mu;
    }
    public double value(int k) {
        return 1.0 - incompleteGammaFunction(k+1, mu_);
    }
}
