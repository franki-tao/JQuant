package jquant.methods.finitedifferences.utilities;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOp;

import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Time-dependent Dirichlet boundary condition for FDM operators.
 *
 * <p>Port of {@code ql/methods/finitedifferences/utilities/fdmtimedepdirichletboundary.{hpp,cpp}}.
 */
public class FdmTimeDepDirichletBoundary implements FdmBoundaryCondition {
    private final int[] indices_;
    private final DoubleUnaryOperator valueOnBoundary_;
    private final Function<Double, Array> valuesOnBoundary_;

    private Array values_;

    public FdmTimeDepDirichletBoundary(final FdmMesher mesher,
                                       DoubleUnaryOperator valueOnBoundary,
                                       int direction,
                                       FdmDirichletBoundary.Side side) {
        indices_ = new FdmIndicesOnBoundary(mesher.layout(), direction, side).getIndices();
        valueOnBoundary_ = valueOnBoundary;
        valuesOnBoundary_ = null;
        values_ = new Array(indices_.length);
    }

    public FdmTimeDepDirichletBoundary(final FdmMesher mesher,
                                       Function<Double, Array> valuesOnBoundary,
                                       int direction,
                                       FdmDirichletBoundary.Side side) {
        indices_ = new FdmIndicesOnBoundary(mesher.layout(), direction, side).getIndices();
        valueOnBoundary_ = null;
        valuesOnBoundary_ = valuesOnBoundary;
        values_ = new Array(indices_.length);
    }

    @Override
    public void setTime(double t) {
        if (valueOnBoundary_ != null) {
            final double value = valueOnBoundary_.applyAsDouble(t);
            for (int i = 0; i < values_.size(); ++i)
                values_.set(i, value);
        } else if (valuesOnBoundary_ != null) {
            values_ = valuesOnBoundary_.apply(t);
        } else {
            QL_FAIL("no boundary values defined");
        }
    }

    @Override
    public void applyBeforeApplying(FdmLinearOp op) {
    }

    @Override
    public void applyBeforeSolving(FdmLinearOp op, Array a) {
    }

    @Override
    public void applyAfterApplying(Array a) {
        QL_REQUIRE(indices_.length == values_.size(),
                "values on boundary size (" + values_.size()
                + ") does not match hypersurface size (" + indices_.length + ")");
        for (int i = 0; i < indices_.length; ++i)
            a.set(indices_[i], values_.get(i));
    }

    @Override
    public void applyAfterSolving(Array a) {
        applyAfterApplying(a);
    }
}
