package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Period;
import jquant.time.TimeUnit;

public class Bkbm6M extends Bkbm {
    public Bkbm6M() {
        this(null);
    }

    public Bkbm6M(Handle<YieldTermStructure> h) {
        super(new Period(6, TimeUnit.MONTHS), h);
    }
}
