package math.optimization.impl;

import math.Array;

public abstract class ConstraintImpl {
    public abstract boolean test(Array params);

    //! Returns upper bound for given parameters
    public Array upperBound(Array params) {
        return new Array(params.size(),
                params.max());
    }

    //! Returns lower bound for given parameters
    public Array lowerBound(Array params) {
        return new Array(params.size(), -params.max());
    }
}
