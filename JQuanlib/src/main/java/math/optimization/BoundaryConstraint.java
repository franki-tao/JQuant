package math.optimization;


import math.optimization.Constraint;
import math.optimization.impl.BoundaryConstraintImpl;

public class BoundaryConstraint extends Constraint {
    public BoundaryConstraint(double low, double high) {
        super(new BoundaryConstraintImpl(high, low));
    }
}
