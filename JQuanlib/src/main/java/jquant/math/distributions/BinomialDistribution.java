package jquant.math.distributions;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.binomialCoefficientLn;

public class BinomialDistribution {
    private int n_;
    private double logP_;
    private double logOneMinusP_;

    public BinomialDistribution(double p, int n) {
        n_ = n;
        if (p == 0.0) {
            logP_ = -QL_MAX_REAL;
            logOneMinusP_ = 0.0;
        } else if (p == 1.0) {
            logP_ = 0.0;
            logOneMinusP_ = -QL_MAX_REAL;
        } else {
            if (p < 0 || p > 1) {
                throw new IllegalArgumentException("p must be in [0.0, 1.0].");
            }
            logP_ = Math.log(p);
            logOneMinusP_ = Math.log(1.0 - p);
        }
    }

    public double value(int k) {
        if (k > n_) return 0.0;

        // p==1.0
        if (logP_ == 0.0)
            return (k == n_ ? 1.0 : 0.0);
            // p==0.0
        else if (logOneMinusP_ == 0.0)
            return (k == 0 ? 1.0 : 0.0);
        else
            return Math.exp(binomialCoefficientLn(n_, k) +
                    k * logP_ + (n_ - k) * logOneMinusP_);
    }
}
