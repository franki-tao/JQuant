package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;

/**
 * Fed Funds rate fixed by the FED (for balances held at the Federal Reserve).
 *
 * <p>Port of QuantLib's {@code FedFunds} in {@code ql/indexes/ibor/fedfunds.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/ibor/fedfunds.cpp
 */
public class FedFunds extends OvernightIndex {
    public FedFunds() {
        this(new Handle<>(null, false));
    }

    public FedFunds(Handle<YieldTermStructure> h) {
        super("FedFunds", 0, Currencies.usd(),
                new UnitedStates(UnitedStates.Market.FederalReserve),
                new Actual360(false), h);
    }
}
