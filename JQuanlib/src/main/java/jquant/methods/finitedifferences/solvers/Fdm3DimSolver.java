package jquant.methods.finitedifferences.solvers;

import jquant.math.Array;
import jquant.math.interpolations.BicubicSpline;
import jquant.math.interpolations.MonotonicCubicNaturalSpline;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.patterns.LazyObject;

import java.util.List;

/**
 * Three-dimensional finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdm3dimsolver.{hpp,cpp}}.
 */
public class Fdm3DimSolver extends LazyObject {
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final FdmLinearOpComposite op_;

    private final FdmSnapshotCondition thetaCondition_;
    private final FdmStepConditionComposite conditions_;

    private final double[] x_, y_, z_, initialValues_;
    private double[][][] resultValues_;
    private BicubicSpline[] interpolation_;

    public Fdm3DimSolver(final FdmSolverDesc solverDesc,
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
        final int dim2 = solverDesc.mesher.layout().dim()[2];
        x_ = new double[dim0];
        y_ = new double[dim1];
        z_ = new double[dim2];
        initialValues_ = new double[solverDesc.mesher.layout().size()];
        resultValues_ = new double[dim2][dim1][dim0];
        interpolation_ = new BicubicSpline[dim2];

        for (FdmLinearOpIterator iter : solverDesc.mesher.layout()) {
            initialValues_[iter.index()] =
                    solverDesc_.calculator.avgInnerValue(iter, solverDesc_.maturity);

            final int[] coordinates = iter.coordinates();
            if (coordinates[1] == 0 && coordinates[2] == 0) {
                x_[coordinates[0]] = solverDesc_.mesher.location(iter, 0);
            }
            if (coordinates[0] == 0 && coordinates[2] == 0) {
                y_[coordinates[1]] = solverDesc_.mesher.location(iter, 1);
            }
            if (coordinates[0] == 0 && coordinates[1] == 0) {
                z_[coordinates[2]] = solverDesc_.mesher.location(iter, 2);
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
        final int dim2 = z_.length;
        final int planeSize = dim1 * dim0;

        resultValues_ = new double[dim2][dim1][dim0];
        for (int i = 0; i < values.length; ++i) {
            final int k = i / planeSize;
            final int rem = i % planeSize;
            resultValues_[k][rem / dim0][rem % dim0] = values[i];
        }

        interpolation_ = new BicubicSpline[dim2];
        for (int i = 0; i < dim2; ++i) {
            interpolation_[i] = new BicubicSpline(x_, y_, resultValues_[i]);
        }
    }

    public double interpolateAt(double x, double y, double z) {
        calculate();

        final double[] zArray = new double[z_.length];
        for (int i = 0; i < z_.length; ++i) {
            zArray[i] = interpolation_[i].value(x, y, false);
        }
        return new MonotonicCubicNaturalSpline(z_, zArray).value(z, false);
    }

    public double thetaAt(double x, double y, double z) {
        if (conditions_.stoppingTimes().get(0) == 0.0) {
            return Double.NaN;
        }

        calculate();

        final int dim0 = x_.length;
        final int dim1 = y_.length;
        final int planeSize = dim1 * dim0;
        final double[] values = thetaCondition_.getValues().toArray();
        final double[][][] thetaValues = new double[z_.length][dim1][dim0];
        for (int i = 0; i < values.length; ++i) {
            final int k = i / planeSize;
            final int rem = i % planeSize;
            thetaValues[k][rem / dim0][rem % dim0] = values[i];
        }

        final double[] zArray = new double[z_.length];
        for (int i = 0; i < z_.length; ++i) {
            zArray[i] = new BicubicSpline(x_, y_, thetaValues[i])
                    .value(x, y, false);
        }

        return (new MonotonicCubicNaturalSpline(z_, zArray).value(z, false)
                - interpolateAt(x, y, z)) / thetaCondition_.getTime();
    }
}
