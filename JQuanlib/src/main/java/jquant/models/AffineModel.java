package jquant.models;

import jquant.Option;
import jquant.math.Array;
import jquant.patterns.Observable;

//! Affine model class
/*! Base class for analytically tractable models.

    \ingroup shortrate
*/
public interface AffineModel extends Observable {
    //! Implied discount curve
    double discount(double t);

    double discountBond(double now,
                        double maturity,
                        Array factors);

    double discountBondOption(Option.Type type, double strike, double maturity, double bondMaturity);

    /**
     * Official {@code AffineModel::discountBondOption(type, strike, maturity,
     * bondStart, bondMaturity)} defaults to the four-argument overload,
     * ignoring {@code bondStart}.
     */
    default double discountBondOption(Option.Type type, double strike,
                                      double maturity, double bondStart,
                                      double bondMaturity) {
        return discountBondOption(type, strike, maturity, bondMaturity);
    }
}
