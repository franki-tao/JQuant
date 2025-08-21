package math.optimization;

import math.Array;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;

public class Constraint {

    protected static abstract class Impl {
        //! Tests if params satisfy the constraint
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

    protected Constraint.Impl impl_;

    public Constraint(Constraint.Impl impl) {
        this.impl_ = impl;
    }

    public boolean empty() {
        return impl_ == null;
    }

    public boolean test(Array p) {
        return impl_.test(p);
    }

    public Array upperBound(Array params) {
        Array result = impl_.upperBound(params);
        QL_REQUIRE(params.size() == result.size(),
                "upper bound size (" + result.size()
                        + ") not equal to params size ("
                        + params.size() + ")");
        return result;
    }

    public Array lowerBound(Array params) {
        Array result = impl_.lowerBound(params);
        QL_REQUIRE(params.size() == result.size(),
                "lower bound size (" + result.size()
                        + ") not equal to params size ("
                        + params.size() + ")");
        return result;
    }

    public double update(Array params, Array direction, double beta) {
        double diff = beta;
        Array newParams = params.add(direction.mutiply(diff));
        boolean valid = test(newParams);
        int icount = 0;
        while (!valid) {
            if (icount > 200)
                QL_FAIL("can't update parameter vector");
            diff *= 0.5;
            icount++;
            newParams = params.add(direction.mutiply(diff));
            valid = test(newParams);
        }
        params = params.add(direction.mutiply(diff));
        return diff;
    }
}
