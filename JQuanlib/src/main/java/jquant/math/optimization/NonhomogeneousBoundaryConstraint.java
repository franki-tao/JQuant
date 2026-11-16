package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.optimization.impl.NonhomogeneousBoundaryConstraintImpl;

//! %Constraint imposing i-th argument to be in [low_i,high_i] for all i
public class NonhomogeneousBoundaryConstraint extends Constraint{

    //! official argument order: (low, high)
    public NonhomogeneousBoundaryConstraint(Array low, Array high) {
        super(new NonhomogeneousBoundaryConstraintImpl(low, high));
    }
}
