package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm4M extends Bkbm {
    public Bkbm4M() {
        this(null);
    }

    public Bkbm4M(Handle<YieldTermStructure> h) {
        super(new Period(4, TimeUnit.MONTHS), h);
    }
}
