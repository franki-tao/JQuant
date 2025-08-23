package math.optimization;

import math.optimization.impl.PositiveConstraintImpl;

public class PositiveConstraint extends Constraint{

    public PositiveConstraint() {
        super(new PositiveConstraintImpl());
    }

}
