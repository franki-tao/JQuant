package math.optimization.impl;

import math.Array;
import math.CommonUtil;
import math.Compare;
import math.optimization.impl.ConstraintImpl;

public class PositiveConstraintImpl extends ConstraintImpl {
    @Override
    public boolean test(Array params) {
        Compare<Double> compare = new Compare<Double>() {
            @Override
            public boolean call(Double x) {
                return x > 0.0;
            }
        };
        return CommonUtil.All_of(params.getList(), compare);
    }

    @Override
    public Array upperBound(Array params) {
        return new Array(params.size(), Double.MAX_VALUE);
    }

    @Override
    public Array lowerBound(Array params) {
        return new Array(params.size(), 0d);
    }
}
