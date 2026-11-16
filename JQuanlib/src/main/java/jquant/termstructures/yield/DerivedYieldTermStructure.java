package jquant.termstructures.yield;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for derived term structures.
 *
 * <p>The day counter and the default maximum date are taken from an original
 * curve, which is also observed so that changes propagate to the derived
 * structure. Extrapolation follows the original curve as well.
 *
 * <p>This is the Java counterpart of QuantLib's
 * {@code DerivedYieldTermStructure<Base>} for the only base used here,
 * {@link YieldTermStructure}. The official template's jump-related part of
 * {@code YieldTermStructure::update()} is skipped because derived structures
 * built on this class do not have jumps; the remaining behaviour is that of
 * {@code TermStructure::update()}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/derivedtermstructure.hpp
 */
public abstract class DerivedYieldTermStructure extends YieldTermStructure {
    protected final Handle<YieldTermStructure> originalCurve_;

    public DerivedYieldTermStructure(Handle<YieldTermStructure> originalCurve) {
        // As in QuantLib, the reference date is provided by the derived class
        // (DerivedYieldTermStructure::referenceDate is not overridden here;
        // RelativeDerivedYieldTermStructure forwards it from the original).
        super((DayCounter) null);
        QL_REQUIRE(originalCurve != null, "null original curve handle");
        originalCurve_ = originalCurve;
        registerWith(originalCurve_.get());
        setupExtrapolation();
    }

    @Override
    public DayCounter dayCounter() {
        return originalCurve_.currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return originalCurve_.currentLink().maxDate();
    }

    @Override
    public void update() {
        setupExtrapolation();
        // TermStructure::update(): YieldTermStructure::update() also adjusts
        // jumps, but this class is constructed without any.
        if (moving_)
            updated_ = false;
        notifyObservers();
    }

    private void setupExtrapolation() {
        if (!originalCurve_.empty()) {
            if (originalCurve_.currentLink().allowsExtrapolation())
                enableExtrapolation();
            else
                disableExtrapolation();
        }
    }
}
