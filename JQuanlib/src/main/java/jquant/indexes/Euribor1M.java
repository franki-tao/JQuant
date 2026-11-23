package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Euribor1M extends Euribor {
    public Euribor1M() {
        this(null);
    }

    public Euribor1M(Handle<YieldTermStructure> h) {
        super(new Period(1, TimeUnit.MONTHS), h);
    }
}
