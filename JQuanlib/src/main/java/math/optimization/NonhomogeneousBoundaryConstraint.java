package math.optimization;

import math.Array;
import math.optimization.impl.NonhomogeneousBoundaryConstraintImpl;

//! %Constraint imposing i-th argument to be in [low_i,high_i] for all i
public class NonhomogeneousBoundaryConstraint extends Constraint{

    public NonhomogeneousBoundaryConstraint(Array high, Array low) {
        super(new NonhomogeneousBoundaryConstraintImpl(low, high));
    }
}
