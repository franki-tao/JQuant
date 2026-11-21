package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Euribor6M extends Euribor {
    public Euribor6M() {
        this(null);
    }

    public Euribor6M(Handle<YieldTermStructure> h) {
        super(new Period(6, TimeUnit.MONTHS), h);
    }
}
