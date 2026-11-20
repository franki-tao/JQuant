package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOp;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Dirichlet boundary condition for FDM operators.
 *
 * <p>Port of {@code ql/methods/finitedifferences/utilities/fdmdirichletboundary.{hpp,cpp}}.
 */
public class FdmDirichletBoundary implements FdmBoundaryCondition {
    public enum Side {
        Lower,
        Upper
    }

    private final Side side_;
    private final double valueOnBoundary_;
    private final int[] indices_;
    private final double xExtreme_;

    public FdmDirichletBoundary(final FdmMesher mesher,
                                double valueOnBoundary,
                                int direction,
                                Side side) {
        side_ = side;
        valueOnBoundary_ = valueOnBoundary;

        final FdmLinearOpLayout layout = mesher.layout();
        indices_ = new FdmIndicesOnBoundary(layout, direction, side).getIndices();

        final Array locations = mesher.locations(direction);
        double xExtreme = 0.0;
        if (side == Side.Lower)
            xExtreme = locations.get(0);
        else if (side == Side.Upper)
            xExtreme = locations.get(locations.size() - 1);
        else
            QL_FAIL("internal error");
        xExtreme_ = xExtreme;
    }

    @Override
    public void applyBeforeApplying(FdmLinearOp op) {
    }

    @Override
    public void applyBeforeSolving(FdmLinearOp op, Array a) {
    }

    @Override
    public void applyAfterApplying(Array a) {
        for (int index : indices_)
            a.set(index, valueOnBoundary_);
    }

    @Override
    public void applyAfterSolving(Array a) {
        applyAfterApplying(a);
    }

    @Override
    public void setTime(double t) {
    }

    public double applyAfterApplying(double x, double value) {
        if ((side_ == Side.Lower && x < xExtreme_)
                || (side_ == Side.Upper && x > xExtreme_))
            return valueOnBoundary_;
        return value;
    }
}
