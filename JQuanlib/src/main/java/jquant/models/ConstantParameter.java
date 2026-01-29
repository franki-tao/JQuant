package jquant.models;

import jquant.math.Array;
import jquant.math.optimization.Constraint;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Standard constant parameter \f$ a(t) = a \f$
public class ConstantParameter extends Parameter {
    private static class Impl extends Parameter.Impl {
        @Override
        public double value(final Array params, double t) {
            return params.get(0);
        }
    }

    public ConstantParameter(final Constraint constraint) {
        super(1, new ConstantParameter.Impl(), constraint);
    }

    public ConstantParameter(double value, final Constraint constraint) {
        super(1, new ConstantParameter.Impl(), constraint);
        params_.set(0, value);
        QL_REQUIRE(testParams(params_), value + ": invalid value");
    }
}
