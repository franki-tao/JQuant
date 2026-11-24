package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.calendars.Australia;
import jquant.time.daycounters.Actual360;

/**
 * AUD LIBOR, fixed in London against the Australian settlement calendar.
 *
 * <p>Mirrors {@code ql/indexes/ibor/audlibor.hpp}: 2 settlement days, AUD
 * currency, Australian settlement calendar, Actual/360. The bond day counter
 * in the official fixing-convention test is Actual/365 Fixed; the index and
 * the bond payment day counter are deliberately distinct.
 *
 * <p>Australian Dollar LIBOR was discontinued as of 2013.
 */
public class AUDLibor extends Libor {
    public AUDLibor(Period tenor) {
        this(tenor, null);
    }

    public AUDLibor(Period tenor, Handle<YieldTermStructure> h) {
        super("AUDLibor", tenor, 2, Currencies.aud(),
                new Australia(), new Actual360(), h);
    }
}
