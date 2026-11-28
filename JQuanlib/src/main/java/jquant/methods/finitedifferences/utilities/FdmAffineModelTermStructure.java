package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.models.AffineModel;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;

/**
 * Yield term structure implied by an affine short-rate model.
 *
 * <p>Ported from QuantLib
 * {@code ql/methods/finitedifferences/utilities/fdmaffinemodeltermstructure.{hpp,cpp}}.
 */
public class FdmAffineModelTermStructure extends YieldTermStructure {

    private Array r_;
    private final double t_;
    private final AffineModel model_;

    public FdmAffineModelTermStructure(Array r,
                                       Calendar cal,
                                       DayCounter dayCounter,
                                       Date referenceDate,
                                       Date modelReferenceDate,
                                       AffineModel model) {
        super(referenceDate, cal, dayCounter, new ArrayList<>(), new ArrayList<>());
        r_ = r;
        t_ = dayCounter.yearFraction(modelReferenceDate, referenceDate);
        model_ = model;
        registerWith(model_);
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    public void setVariable(Array r) {
        r_ = r;
        notifyObservers();
    }

    @Override
    protected double discountImpl(double T) {
        return model_.discountBond(t_, T + t_, r_);
    }
}
