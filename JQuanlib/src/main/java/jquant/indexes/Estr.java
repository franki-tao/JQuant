package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;

/**
 * ESTR (Euro Short-Term Rate) overnight index.
 *
 * <p>Port of QuantLib {@code ql/indexes/ibor/estr.{hpp,cpp}}.
 */
public class Estr extends OvernightIndex {
    public Estr(Handle<YieldTermStructure> h) {
        super("ESTR", 0, Currencies.eur(), new Target(), new Actual360(), h);
    }

    @Override
    public Estr clone(Handle<YieldTermStructure> h) {
        return new Estr(h);
    }
}
