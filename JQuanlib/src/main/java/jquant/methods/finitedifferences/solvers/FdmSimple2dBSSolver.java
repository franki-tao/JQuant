package jquant.methods.finitedifferences.solvers;

import jquant.math.MathUtils;
import jquant.methods.finitedifferences.operators.FdmBlackScholesOp;
import jquant.patterns.LazyObject;
import jquant.processes.GeneralizedBlackScholesProcess;

/**
 * Two-dimensional Black-Scholes finite-difference solver.
 *
 * <p>Port of {@code FdmSimple2dBSSolver} from
 * {@code ql/methods/finitedifferences/solvers/fdmsimple2dbssolver.{hpp,cpp}}.
 */
public class FdmSimple2dBSSolver extends LazyObject {
    private final GeneralizedBlackScholesProcess process_;
    private final double strike_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm2DimSolver solver_;

    public FdmSimple2dBSSolver(GeneralizedBlackScholesProcess process,
                               double strike,
                               FdmSolverDesc solverDesc,
                               FdmSchemeDesc schemeDesc) {
        process_ = process;
        strike_ = strike;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;

        registerWith(process_);
    }

    @Override
    protected void performCalculations() {
        final FdmBlackScholesOp op = new FdmBlackScholesOp(
                solverDesc_.mesher, process_, strike_,
                false, -MathUtils.NULL_REAL, 0, null);

        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double s, double a) {
        calculate();
        return solver_.interpolateAt(Math.log(s), Math.log(a));
    }

    public double deltaAt(double s, double a, double eps) {
        return (valueAt(s + eps, a) - valueAt(s - eps, a)) / (2.0 * eps);
    }

    public double gammaAt(double s, double a, double eps) {
        return (valueAt(s + eps, a) + valueAt(s - eps, a)
                - 2.0 * valueAt(s, a)) / (eps * eps);
    }

    public double thetaAt(double s, double a) {
        calculate();
        return solver_.thetaAt(Math.log(s), Math.log(a));
    }
}
