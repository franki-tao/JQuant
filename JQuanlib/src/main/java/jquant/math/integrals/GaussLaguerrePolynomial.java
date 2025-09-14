package jquant.math.integrals;

import jquant.math.distributions.GammaFunction;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class GaussLaguerrePolynomial extends GaussianOrthogonalPolynomial {
    private double s_;

    public GaussLaguerrePolynomial(double s) {
        this.s_ = s;
        QL_REQUIRE(s > -1.0, "s must be bigger than -1");

    }

    public GaussLaguerrePolynomial() {
        s_ = 0;
    }

    @Override
    public double alpha(int i) {
        return 2 * i + 1 + s_;
    }

    @Override
    public double beta(int i) {
        return i * (i + s_);
    }

    @Override
    public double w(double x) {
        return Math.pow(x, s_) * Math.exp(-x);
    }

    @Override
    public double mu_0() {
        return Math.exp(new GammaFunction().logValue(s_ + 1));
    }
}
