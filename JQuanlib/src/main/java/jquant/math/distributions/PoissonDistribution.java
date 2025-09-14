package jquant.math.distributions;

import jquant.math.Factorial;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class PoissonDistribution {
    private double mu_;
    private double logMu_;

    public PoissonDistribution(double mu) {
        QL_REQUIRE(mu >= 0.0,
                "mu must be non negative not allowed)");
        this.mu_ = mu;
        if (mu_ != 0.0) logMu_ = Math.log(mu_);
    }

    public double value(int k) {
        if (mu_ == 0.0) {
            if (k == 0) return 1.0;
            else return 0.0;
        }
        double logFactorial = Factorial.ln(k);
        return Math.exp(k * Math.log(mu_) - logFactorial - mu_);
    }
}
