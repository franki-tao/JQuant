package jquant.models;

import jquant.math.Array;
import jquant.math.optimization.NoConstraint;

//! %Parameter which is always zero \f$ a(t) = 0 \f$
public class NullParameter extends Parameter {
    private static class Impl extends Parameter.Impl {
        @Override
        public double value(Array params, double t) {
            return 0.0;
        }
    }

    public NullParameter() {
        super(0, new NullParameter.Impl(), new NoConstraint());
    }
}
