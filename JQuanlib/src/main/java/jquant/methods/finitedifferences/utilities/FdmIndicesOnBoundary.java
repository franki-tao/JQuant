package jquant.methods.finitedifferences.utilities;

import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Indices of the grid points lying on one side of an FDM layout.
 *
 * <p>Port of {@code ql/methods/finitedifferences/utilities/fdmindicesonboundary.{hpp,cpp}}.
 */
public class FdmIndicesOnBoundary {
    private final int[] indices_;

    public FdmIndicesOnBoundary(FdmLinearOpLayout layout,
                                int direction,
                                FdmDirichletBoundary.Side side) {
        final int[] newDim = layout.dim().clone();
        newDim[direction] = 1;

        int hyperSize = 1;
        for (int d : newDim)
            hyperSize *= d;

        indices_ = new int[hyperSize];

        int i = 0;
        for (FdmLinearOpIterator iter : layout) {
            if ((side == FdmDirichletBoundary.Side.Lower
                    && iter.coordinates()[direction] == 0)
                    || (side == FdmDirichletBoundary.Side.Upper
                    && iter.coordinates()[direction] == layout.dim()[direction] - 1)) {
                QL_REQUIRE(hyperSize > i, "index missmatch");
                indices_[i++] = iter.index();
            }
        }
    }

    public int[] getIndices() {
        return indices_;
    }
}
