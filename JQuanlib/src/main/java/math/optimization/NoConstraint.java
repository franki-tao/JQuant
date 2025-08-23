package math.optimization;

import math.optimization.impl.NoConstraintImpl;

public class NoConstraint extends Constraint{
    public NoConstraint() {
        super(new NoConstraintImpl());
    }
}
