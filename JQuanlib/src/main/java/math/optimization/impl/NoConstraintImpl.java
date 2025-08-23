package math.optimization.impl;

import math.Array;
import math.optimization.impl.ConstraintImpl;

public class NoConstraintImpl extends ConstraintImpl {
    @Override
    public boolean test(Array params) {
        return true;
    }
}
