package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm3M extends Bkbm {
    public Bkbm3M() {
        this(null);
    }

    public Bkbm3M(Handle<YieldTermStructure> h) {
        super(new Period(3, TimeUnit.MONTHS), h);
    }
}
