package jquant.math.integrals;

import static jquant.math.MathUtils.squared;

public class GaussLaguerreSinePolynomial extends GaussLaguerreTrigonometricBase {
    private double m0_;

    public GaussLaguerreSinePolynomial(double u) {
        super(u);
        m0_ = 1.0 + u / (1.0 + u * u);
    }

    @Override
    protected double m0() {
        return super.u_ / (1 + super.u_ * super.u_);
    }

    @Override
    protected double m1() {
        return 2 * super.u_ / squared(1 + super.u_ * super.u_);
    }

    @Override
    public double w(double x) {
        return Math.exp(-x) * (1 + Math.sin(super.u_ * x)) / m0_;
    }

    @Override
    public double moment(int n) {
        return (super.moment_(n) + super.fact(n)) / m0_;
    }
}
