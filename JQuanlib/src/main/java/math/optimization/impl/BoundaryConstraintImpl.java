package math.optimization.impl;

import math.Array;
import math.CommonUtil;
import math.Compare;

public class BoundaryConstraintImpl extends ConstraintImpl {
    private double high_;
    private double low_;

    public BoundaryConstraintImpl(double high_, double low_) {
        this.high_ = high_;
        this.low_ = low_;
    }

    @Override
    public boolean test(Array params) {
        Compare<Double> compare = new Compare<Double>() {
            @Override
            public boolean call(Double x) {
                return low_ <= x && x <= high_;
            }
        };
        return CommonUtil.All_of(params.getList(), compare);
    }

    @Override
    public Array upperBound(Array params) {
        return new Array(params.size(), high_);
    }

    @Override
    public Array lowerBound(Array params) {
        return new Array(params.size(), low_);
    }

}
