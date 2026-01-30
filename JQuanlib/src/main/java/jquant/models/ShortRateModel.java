package jquant.models;

import jquant.Lattice;
import jquant.TimeGrid;

//! Abstract short-rate model class
/*! \ingroup shortrate */
public abstract class ShortRateModel extends CalibratedModel{
    public ShortRateModel(int nArguments) {
        super(nArguments);
    }

    public abstract Lattice tree(final TimeGrid t);
}
