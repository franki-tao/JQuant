package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm2M extends Bkbm {
    public Bkbm2M() {
        this(null);
    }

    public Bkbm2M(Handle<YieldTermStructure> h) {
        super(new Period(2, TimeUnit.MONTHS), h);
    }
}
