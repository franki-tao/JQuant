package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Euribor3M extends Euribor {
    public Euribor3M() {
        this(null);
    }

    public Euribor3M(Handle<YieldTermStructure> h) {
        super(new Period(3, TimeUnit.MONTHS), h);
    }
}
