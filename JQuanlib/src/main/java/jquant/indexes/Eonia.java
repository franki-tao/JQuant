package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;

/**
 * Eonia overnight index.
 *
 * <p>Ported from QuantLib {@code ql/indexes/ibor/eonia.{hpp,cpp}}.
 */
public class Eonia extends OvernightIndex {
    public Eonia(Handle<YieldTermStructure> h) {
        super("Eonia", 0, Currencies.eur(), new Target(), new Actual360(), h);
    }

    @Override
    public Eonia clone(Handle<YieldTermStructure> h) {
        return new Eonia(h);
    }
}
