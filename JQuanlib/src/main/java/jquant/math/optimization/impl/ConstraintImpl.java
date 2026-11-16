package jquant.math.optimization.impl;

import jquant.math.Array;

public abstract class ConstraintImpl {
    public abstract boolean test(Array params);

    //! Returns upper bound for given parameters
    public Array upperBound(Array params) {
        // official: std::numeric_limits<Array::value_type>::max()
        return new Array(params.size(), Double.MAX_VALUE);
    }

    //! Returns lower bound for given parameters
    public Array lowerBound(Array params) {
        // official: -std::numeric_limits<Array::value_type>::max()
        return new Array(params.size(), -Double.MAX_VALUE);
    }
}
