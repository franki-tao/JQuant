package math.optimization.impl;

import math.Array;
import math.optimization.Constraint;

public class CompositeConstraintImpl extends ConstraintImpl {
    private Constraint c1_;
    private Constraint c2_;

    public CompositeConstraintImpl(Constraint c1, Constraint c2) {
        this.c1_ = c1;
        this.c2_ = c2;

    }

    @Override
    public boolean test(Array params) {
        return c1_.test(params) && c2_.test(params);
    }

    @Override
    public Array upperBound(Array params) {
        Array c1ub = c1_.upperBound(params);
        Array c2ub = c2_.upperBound(params);
        Array rtrnArray = new Array(c1ub.size(), 0.0);
        for (int iter = 0; iter < c1ub.size(); iter++) {
            rtrnArray.set(iter, Math.min(c1ub.get(iter), c2ub.get(iter)));
        }
        return rtrnArray;
    }

    @Override
    public Array lowerBound(Array params) {
        Array c1lb = c1_.lowerBound(params);
        Array c2lb = c2_.lowerBound(params);
        Array rtrnArray = new Array(c1lb.size(), 0.0);
        for (int iter = 0; iter < c1lb.size(); iter++) {
            rtrnArray.set(iter, Math.max(c1lb.get(iter), c2lb.get(iter)));
        }
        return rtrnArray;
    }

}
