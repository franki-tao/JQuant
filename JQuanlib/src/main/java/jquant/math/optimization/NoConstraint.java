package jquant.math.optimization;

import jquant.math.optimization.impl.NoConstraintImpl;

public class NoConstraint extends Constraint{
    public NoConstraint() {
        super(new NoConstraintImpl());
    }
}
