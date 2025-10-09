package jquant.math.randomnumbers.impl;

import jquant.methods.montecarlo.SampleReal;

public interface RngImpl {
    SampleReal next();
    default long nextInt32() {
        return 0;
    }
}
