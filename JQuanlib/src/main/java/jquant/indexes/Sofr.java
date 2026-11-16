package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;

/**
 * SOFR (Secured Overnight Financing Rate) index.
 *
 * <p>Port of QuantLib's {@code Sofr} in {@code ql/indexes/ibor/sofr.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/ibor/sofr.cpp
 */
public class Sofr extends OvernightIndex {
    public Sofr() {
        this(new Handle<>(null, false));
    }

    public Sofr(Handle<YieldTermStructure> h) {
        super("SOFR", 0, Currencies.usd(),
                new UnitedStates(UnitedStates.Market.SOFR),
                new Actual360(false), h);
    }
}
