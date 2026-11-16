package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOp;

/**
 * Boundary condition for finite-difference operators.
 *
 * <p>Port of the {@code BoundaryCondition<FdmLinearOp>} interface used by
 * {@code ql/methods/finitedifferences/utilities/fdmboundaryconditionset.hpp}.
 */
public interface FdmBoundaryCondition {
    void applyBeforeApplying(FdmLinearOp op);

    void applyBeforeSolving(FdmLinearOp op, Array a);

    void applyAfterApplying(Array a);

    void applyAfterSolving(Array a);

    void setTime(double t);
}
