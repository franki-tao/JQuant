package jquant.math.randomnumbers.impl;

import jquant.methods.montecarlo.SampleVector;

public interface UsgImpl {
    SampleVector nextSequence();
    int dimension();
}
