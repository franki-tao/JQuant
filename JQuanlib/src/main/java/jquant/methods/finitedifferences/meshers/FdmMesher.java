package jquant.methods.finitedifferences.meshers;

import jquant.math.Array;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;

/**
 * Mesher for a finite-difference grid.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/fdmmesher.hpp}.
 */
public abstract class FdmMesher {
    protected final FdmLinearOpLayout layout_;

    public FdmMesher(FdmLinearOpLayout layout) {
        layout_ = layout;
    }

    public abstract double dplus(FdmLinearOpIterator iter, int direction);

    public abstract double dminus(FdmLinearOpIterator iter, int direction);

    public abstract double location(FdmLinearOpIterator iter, int direction);

    public abstract Array locations(int direction);

    public FdmLinearOpLayout layout() {
        return layout_;
    }
}
