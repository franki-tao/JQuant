package jquant.termstructures.yield;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;

/**
 * Base class for derived term structures whose reference date, calendar and
 * settlement days are forwarded from an original curve.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/derivedtermstructure.hpp
 */
public abstract class RelativeDerivedYieldTermStructure extends DerivedYieldTermStructure {

    public RelativeDerivedYieldTermStructure(Handle<YieldTermStructure> originalCurve) {
        super(originalCurve);
    }

    @Override
    public Date referenceDate() {
        return originalCurve_.currentLink().referenceDate();
    }

    @Override
    public Calendar calendar() {
        return originalCurve_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return originalCurve_.currentLink().settlementDays();
    }

    @Override
    public void update() {
        if (!originalCurve_.empty()) {
            super.update();
        } else {
            // The inherited YieldTermStructure::update() asks for our
            // reference date, which we don't have since the original curve is
            // still not set. Skip over that and use TermStructure::update().
            if (moving_)
                updated_ = false;
            notifyObservers();
        }
    }
}
