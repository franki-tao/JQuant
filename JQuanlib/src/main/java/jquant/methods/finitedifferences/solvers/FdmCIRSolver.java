package jquant.methods.finitedifferences.solvers;

import jquant.methods.finitedifferences.operators.FdmCIROp;
import jquant.patterns.LazyObject;
import jquant.processes.CoxIngersollRossProcess;
import jquant.processes.GeneralizedBlackScholesProcess;

/**
 * CIR finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdmcirsolver.{hpp,cpp}}.
 * QuantLib holds {@code Handle}s for both processes; Java stores the
 * processes directly, mirroring {@link FdmHestonSolver}.
 */
public class FdmCIRSolver extends LazyObject {
    private final CoxIngersollRossProcess cirProcess_;
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final double rho_;
    private final double strike_;

    private Fdm2DimSolver solver_;

    public FdmCIRSolver(CoxIngersollRossProcess cirProcess,
                        GeneralizedBlackScholesProcess bsProcess,
                        FdmSolverDesc solverDesc,
                        FdmSchemeDesc schemeDesc,
                        double rho,
                        double strike) {
        cirProcess_ = cirProcess;
        bsProcess_ = bsProcess;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        rho_ = rho;
        strike_ = strike;

        registerWith(bsProcess_);
        registerWith(cirProcess_);
    }

    @Override
    protected void performCalculations() {
        final FdmCIROp op = new FdmCIROp(
                solverDesc_.mesher, cirProcess_, bsProcess_,
                rho_, strike_);

        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double s, double r) {
        calculate();
        return solver_.interpolateAt(Math.log(s), r);
    }

    public double deltaAt(double s, double r) {
        calculate();
        return solver_.derivativeX(Math.log(s), r) / s;
    }

    public double gammaAt(double s, double r) {
        calculate();
        final double x = Math.log(s);
        return (solver_.derivativeXX(x, r) - solver_.derivativeX(x, r)) / (s * s);
    }

    public double thetaAt(double s, double r) {
        calculate();
        return solver_.thetaAt(Math.log(s), r);
    }
}
