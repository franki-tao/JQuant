package jquant.methods.finitedifferences.operators;

import jquant.math.Array;

/**
 * Linear operator used to model a finite-difference PDE.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmlinearop.hpp}.
 */
public interface FdmLinearOp {
    Array apply(Array r);
}
