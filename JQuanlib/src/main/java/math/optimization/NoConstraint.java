package math.optimization;

import math.Array;

public class NoConstraint extends Constraint{

    private static class Impl extends Constraint.Impl {
        @Override
        public boolean test(Array params) {
            return true;
        }
    }
    public NoConstraint() {
        super(new Impl());
    }
}
