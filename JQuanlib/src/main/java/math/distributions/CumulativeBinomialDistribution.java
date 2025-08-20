package math.distributions;

import static math.MathUtils.incompleteBetaFunction;

public class CumulativeBinomialDistribution {
    private int n_;
    private double p_;

    public CumulativeBinomialDistribution(double p, int n) {
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("p must be in [0.0, 1.0].");
        }
        this.p_ = p;
        this.n_ = n;
    }
    public double value(int k) {
        if (k >= n_)
            return 1.0;
        else
            return 1.0 - incompleteBetaFunction(k+1, n_-k, p_, 1e-16, 100);
    }

    public static void main(String[] args) {
        CumulativeBinomialDistribution distribution = new CumulativeBinomialDistribution(0.1, 5);
        System.out.println(distribution.value(3));
    }
}
