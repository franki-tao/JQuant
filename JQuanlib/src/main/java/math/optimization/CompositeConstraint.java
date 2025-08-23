package math.optimization;

import math.optimization.impl.CompositeConstraintImpl;

//! %Constraint enforcing both given sub-constraints
public class CompositeConstraint extends Constraint{
    public CompositeConstraint(Constraint c1, Constraint c2) {
        super(new CompositeConstraintImpl(c1, c2));
    }
}
