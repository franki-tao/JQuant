package jquant.models.shortrate;

import jquant.math.Array;
import jquant.models.AffineModel;

//! Single-factor affine base class
/*! Single-factor models with an analytical formula for discount bonds
    should inherit from this class. They must then implement the
    functions \f$ A(t,T) \f$ and \f$ B(t,T) \f$ such that
    \f[
        P(t, T, r_t) = A(t,T)e^{ -B(t,T) r_t}.
    \f]

    \ingroup shortrate
*/
public abstract class OneFactorAffineModel extends OneFactorModel implements AffineModel {
    public OneFactorAffineModel(int nArguments) {
        super(nArguments);
    }

    @Override
    public double discountBond(double now, double maturity, Array factors) {
        return discountBond(now, maturity, factors.get(0));
    }

    public double discountBond(double now, double maturity, double rate) {
        return A(now, maturity) * Math.exp(-B(now, maturity) * rate);
    }

    public double discount(double t) {
        double x0 = dynamics().process().x0();
        double r0 = dynamics().shortRate(0.0, x0);
        return discountBond(0.0, t, r0);
    }

    protected abstract double A(double t, double T);

    protected abstract double B(double t, double T);
}
