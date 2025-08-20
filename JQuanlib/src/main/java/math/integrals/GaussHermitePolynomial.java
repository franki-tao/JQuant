package math.integrals;

import math.distributions.GammaFunction;

import static math.CommonUtil.QL_REQUIRE;

public class GaussHermitePolynomial extends GaussianOrthogonalPolynomial {
    private double mu_;

    public GaussHermitePolynomial(double mu) {
        this.mu_ = mu;
        QL_REQUIRE(mu > -0.5, "mu must be bigger than -0.5");
    }

    public GaussHermitePolynomial() {
        this.mu_ = 0d;
    }

    @Override
    public double alpha(int i) {
        return 0;
    }

    @Override
    public double beta(int i) {
        return (i % 2) != 0 ? (double) (i / 2.0 + mu_) : (double) (i / 2.0);
    }

    @Override
    public double w(double x) {
        return Math.pow(Math.abs(x), 2 * mu_) * Math.exp(-x * x);
    }

    @Override
    public double mu_0() {
        return Math.exp(new GammaFunction().logValue(mu_ + 0.5));
    }
}
