package jquant.math.optimization.impl;

import jquant.math.Array;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class NonhomogeneousBoundaryConstraintImpl extends ConstraintImpl {
    private Array low_;
    private Array high_;

    public NonhomogeneousBoundaryConstraintImpl(Array low, Array high) {
        this.low_ = low;
        this.high_ = high;
        QL_REQUIRE(low_.size() == high_.size(),
                "Upper and lower boundaries sizes are inconsistent.");
    }

    @Override
    public boolean test(Array params) {
        QL_REQUIRE(params.size() == low_.size(),
                "Number of parameters and boundaries sizes are inconsistent.");
        for (int i = 0; i < params.size(); i++) {
            if ((params.get(i) < low_.get(i)) || (params.get(i) > high_.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Array upperBound(Array p) {
        return high_;
    }

    @Override
    public Array lowerBound(Array p) {
        return low_;
    }
}
