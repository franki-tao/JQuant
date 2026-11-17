package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm5M extends Bkbm {
    public Bkbm5M() {
        this(null);
    }

    public Bkbm5M(Handle<YieldTermStructure> h) {
        super(new Period(5, TimeUnit.MONTHS), h);
    }
}
