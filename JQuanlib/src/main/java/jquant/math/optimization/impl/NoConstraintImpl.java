package jquant.math.optimization.impl;

import jquant.math.Array;

public class NoConstraintImpl extends ConstraintImpl {
    @Override
    public boolean test(Array params) {
        return true;
    }
}
