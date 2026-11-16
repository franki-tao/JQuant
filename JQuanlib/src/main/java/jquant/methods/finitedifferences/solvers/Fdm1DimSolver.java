package jquant.methods.finitedifferences.solvers;

import jquant.math.Array;
import jquant.math.interpolations.MonotonicCubicNaturalSpline;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.stepconditions.FdmSnapshotCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.patterns.LazyObject;

import java.util.List;

/**
 * One-dimensional finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdm1dimsolver.{hpp,cpp}}.
 */
public class Fdm1DimSolver extends LazyObject {
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final FdmLinearOpComposite op_;

    private final FdmSnapshotCondition thetaCondition_;
    private final FdmStepConditionComposite conditions_;

    private final double[] x_;
    private final double[] initialValues_;
    private Array resultValues_;
    private MonotonicCubicNaturalSpline interpolation_;

    public Fdm1DimSolver(FdmSolverDesc solverDesc,
                         FdmSchemeDesc schemeDesc,
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

        final int size = solverDesc.mesher.layout().size();
        x_ = new double[size];
        initialValues_ = new double[size];
        resultValues_ = new Array(size);

        for (FdmLinearOpIterator iter : solverDesc.mesher.layout()) {
            initialValues_[iter.index()] = solverDesc_.calculator
                    .avgInnerValue(iter, solverDesc_.maturity);
            x_[iter.index()] = solverDesc_.mesher.location(iter, 0);
        }
    }

    @Override
    protected void performCalculations() {
        final Array rhs = new Array(initialValues_.length);
        for (int i = 0; i < initialValues_.length; ++i) {
            rhs.set(i, initialValues_[i]);
        }

        new FdmBackwardSolver(op_, solverDesc_.bcSet, conditions_, schemeDesc_)
                .rollback(rhs, solverDesc_.maturity, 0.0,
                        solverDesc_.timeSteps, solverDesc_.dampingSteps);

        resultValues_ = rhs;
        interpolation_ = new MonotonicCubicNaturalSpline(x_, resultValues_.toArray());
    }

    public double interpolateAt(double x) {
        calculate();
        return interpolation_.value(x, false);
    }

    public double thetaAt(double x) {
        if (conditions_.stoppingTimes().get(0) == 0.0) {
            return Double.NaN;
        }

        calculate();
        final double[] thetaValues = thetaCondition_.getValues().toArray();

        final double temp = new MonotonicCubicNaturalSpline(x_, thetaValues)
                .value(x, false);
        return (temp - interpolateAt(x)) / thetaCondition_.getTime();
    }

    public double derivativeX(double x) {
        calculate();
        return interpolation_.derivative(x, false);
    }

    public double derivativeXX(double x) {
        calculate();
        return interpolation_.secondDerivative(x, false);
    }
}
