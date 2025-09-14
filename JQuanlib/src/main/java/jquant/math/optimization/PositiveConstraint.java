package jquant.math.optimization;

import jquant.math.optimization.impl.PositiveConstraintImpl;

public class PositiveConstraint extends Constraint{

    public PositiveConstraint() {
        super(new PositiveConstraintImpl());
    }

}
