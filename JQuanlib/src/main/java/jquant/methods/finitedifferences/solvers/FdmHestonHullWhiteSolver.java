package jquant.methods.finitedifferences.solvers;

import jquant.methods.finitedifferences.operators.FdmHestonHullWhiteOp;
import jquant.methods.finitedifferences.operators.FdmLinearOpComposite;
import jquant.patterns.LazyObject;
import jquant.processes.HestonProcess;
import jquant.processes.HullWhiteProcess;

/**
 * Three-dimensional finite-difference solver for the Heston-Hull-White model.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/solvers/fdmhestonhullwhitesolver.{hpp,cpp}}.
 */
public class FdmHestonHullWhiteSolver extends LazyObject {
    private final HestonProcess hestonProcess_;
    private final HullWhiteProcess hwProcess_;
    private final double corrEquityShortRate_;

    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm3DimSolver solver_;

    public FdmHestonHullWhiteSolver(final HestonProcess hestonProcess,
                                    final HullWhiteProcess hwProcess,
                                    double corrEquityShortRate,
                                    FdmSolverDesc solverDesc) {
        this(hestonProcess, hwProcess, corrEquityShortRate, solverDesc,
                FdmSchemeDesc.Hundsdorfer());
    }

    public FdmHestonHullWhiteSolver(final HestonProcess hestonProcess,
                                    final HullWhiteProcess hwProcess,
                                    double corrEquityShortRate,
                                    FdmSolverDesc solverDesc,
                                    final FdmSchemeDesc schemeDesc) {
        hestonProcess_ = hestonProcess;
        hwProcess_ = hwProcess;
        corrEquityShortRate_ = corrEquityShortRate;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;

        registerWith(hestonProcess);
        registerWith(hwProcess);
    }

    @Override
    protected void performCalculations() {
        final FdmLinearOpComposite op = new FdmHestonHullWhiteOp(
                solverDesc_.mesher,
                hestonProcess_,
                hwProcess_,
                corrEquityShortRate_);

        solver_ = new Fdm3DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double s, double v, double r) {
        calculate();

        final double x = Math.log(s);
        return solver_.interpolateAt(x, v, r);
    }

    public double deltaAt(double s, double v, double r, double eps) {
        return (valueAt(s + eps, v, r) - valueAt(s - eps, v, r)) / (2 * eps);
    }

    public double gammaAt(double s, double v, double r, double eps) {
        return (valueAt(s + eps, v, r) + valueAt(s - eps, v, r)
                - 2 * valueAt(s, v, r)) / (eps * eps);
    }

    public double thetaAt(double s, double v, double r) {
        calculate();

        final double x = Math.log(s);
        return solver_.thetaAt(x, v, r);
    }
}
