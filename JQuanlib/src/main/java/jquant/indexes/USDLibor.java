package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.math.Rounding;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;

import java.util.Set;

/**
 * USD LIBOR fixed in London against the US Libor-impact calendar.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/indexes/ibor/usdlibor.hpp
 */
public class USDLibor extends Libor {
    public USDLibor(Period tenor) {
        this(tenor, null);
    }

    public USDLibor(Period tenor, Handle<YieldTermStructure> h) {
        super("USDLibor", tenor, 2, usd(),
                new UnitedStates(UnitedStates.Market.LiborImpact),
                new Actual360(false), h);
    }

    private static Currency usd() {
        return new Currency("U.S. dollar", "USD", 840, "$", "\u00A2", 100,
                new Rounding(), new Currency(), Set.of());
    }
}
