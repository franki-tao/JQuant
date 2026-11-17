package jquant.methods.finitedifferences.solvers;

import jquant.math.Array;
import jquant.math.interpolations.BicubicSpline;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.patterns.LazyObject;

import java.util.List;

/**
 * Two-dimensional finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdm2dimsolver.{hpp,cpp}}.
 */
public class Fdm2DimSolver extends LazyObject {
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final FdmLinearOpComposite op_;

    private final FdmSnapshotCondition thetaCondition_;
    private final FdmStepConditionComposite conditions_;

    private final double[] x_, y_, initialValues_;
    private double[][] resultValues_;
    private BicubicSpline interpolation_;

    public Fdm2DimSolver(final FdmSolverDesc solverDesc,
                         final FdmSchemeDesc schemeDesc,
                         FdmLinearOpComposite op) {
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        op_ = op;

        final List<Double> stoppingTimes = solverDesc.condition.stoppingTimes();
        final double firstStoppingTime = stoppingTimes.isEmpty()
                ? solverDesc.maturity
                : stoppingTimes.get(0);
        thetaCondition_ = new FdmSnapshotCondition(
                0.99 * Math.min(1.0 / 365.0, firstStoppingTime));
        conditions_ = FdmStepConditionComposite.joinConditions(
                thetaCondition_, solverDesc.condition);

        final int dim0 = solverDesc.mesher.layout().dim()[0];
        final int dim1 = solverDesc.mesher.layout().dim()[1];
        x_ = new double[dim0];
        y_ = new double[dim1];
        initialValues_ = new double[solverDesc.mesher.layout().size()];
        resultValues_ = new double[dim1][dim0];

        for (FdmLinearOpIterator iter : solverDesc.mesher.layout()) {
            initialValues_[iter.index()] =
                    solverDesc_.calculator.avgInnerValue(iter, solverDesc_.maturity);

            if (iter.coordinates()[1] == 0) {
                x_[iter.coordinates()[0]] =
                        solverDesc_.mesher.location(iter, 0);
            }
            if (iter.coordinates()[0] == 0) {
                y_[iter.coordinates()[1]] =
                        solverDesc_.mesher.location(iter, 1);
            }
        }
    }

    @Override
    protected void performCalculations() {
        final Array rhs = new Array(initialValues_.clone());

        new FdmBackwardSolver(op_, solverDesc_.bcSet, conditions_, schemeDesc_)
                .rollback(rhs, solverDesc_.maturity, 0.0,
                        solverDesc_.timeSteps, solverDesc_.dampingSteps);

        final int dim0 = x_.length;
        final int dim1 = y_.length;
        final double[] values = rhs.toArray();
        resultValues_ = new double[dim1][dim0];
        for (int i = 0; i < values.length; ++i) {
            resultValues_[i / dim0][i % dim0] = values[i];
        }
        interpolation_ = new BicubicSpline(x_, y_, resultValues_);
    }

    public double interpolateAt(double x, double y) {
        calculate();
        return interpolation_.value(x, y, false);
    }

    public double thetaAt(double x, double y) {
        if (conditions_.stoppingTimes().get(0) == 0.0) {
            return Double.NaN;
        }

        calculate();
        final int dim0 = x_.length;
        final int dim1 = y_.length;
        final double[] values = thetaCondition_.getValues().toArray();
        final double[][] thetaValues = new double[dim1][dim0];
        for (int i = 0; i < values.length; ++i) {
            thetaValues[i / dim0][i % dim0] = values[i];
        }

        return (new BicubicSpline(x_, y_, thetaValues).value(x, y, false)
                - interpolateAt(x, y))
                / thetaCondition_.getTime();
    }

    public double derivativeX(double x, double y) {
        calculate();
        return interpolation_.derivativeX(x, y);
    }

    public double derivativeY(double x, double y) {
        calculate();
        return interpolation_.derivativeY(x, y);
    }

    public double derivativeXX(double x, double y) {
        calculate();
        return interpolation_.secondDerivativeX(x, y);
    }

    public double derivativeYY(double x, double y) {
        calculate();
        return interpolation_.secondDerivativeY(x, y);
    }

    public double derivativeXY(double x, double y) {
        calculate();
        return interpolation_.derivativeXY(x, y);
    }
}
