package jquant.models;

import jquant.Option;
import jquant.math.Array;
import jquant.patterns.Observable;

//! Affine model class
/*! Base class for analytically tractable models.

    \ingroup shortrate
*/
public interface AffineModel extends Observable {
    double discountBond(double now,
                        double maturity,
                        Array factors);

    double discountBondOption(Option.Type type, double strike, double maturity, double bondMaturity);

    double discountBondOption(Option.Type type, double strike, double maturity, double bondStart, double bondMaturity);
}
