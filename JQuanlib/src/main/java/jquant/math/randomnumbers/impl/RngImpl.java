package jquant.math.randomnumbers.impl;

import jquant.methods.montecarlo.SampleReal;

public abstract class RngImpl {
    public RngImpl(){}
    public RngImpl(long seed) {}
    public abstract SampleReal next();
    public long nextInt32() {
        return 0;
    }
}
