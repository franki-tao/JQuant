package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

/**
 * 1-year Euribor index.
 *
 * <p>Ported from QuantLib {@code ql/indexes/ibor/euribor.hpp}.
 */
public class Euribor1Y extends Euribor {
    public Euribor1Y() {
        this(null);
    }

    public Euribor1Y(Handle<YieldTermStructure> h) {
        super(new Period(1, TimeUnit.YEARS), h);
    }
}
