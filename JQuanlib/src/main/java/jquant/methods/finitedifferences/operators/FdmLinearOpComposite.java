package jquant.methods.finitedifferences.operators;

import jquant.math.Array;

/**
 * Composite operator used by the operator-splitting schemes.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmlinearopcomposite.hpp}.
 */
public interface FdmLinearOpComposite extends FdmLinearOp {
    int size();

    //! Time t1 &lt;= t2 is required.
    void setTime(double t1, double t2);

    Array apply_mixed(Array r);

    Array apply_direction(int direction, Array r);

    Array solve_splitting(int direction, Array r, double s);

    Array preconditioner(Array r, double s);
}
