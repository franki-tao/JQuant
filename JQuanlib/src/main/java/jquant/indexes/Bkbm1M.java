package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm1M extends Bkbm {
    public Bkbm1M() {
        this(null);
    }

    public Bkbm1M(Handle<YieldTermStructure> h) {
        super(new Period(1, TimeUnit.MONTHS), h);
    }
}
