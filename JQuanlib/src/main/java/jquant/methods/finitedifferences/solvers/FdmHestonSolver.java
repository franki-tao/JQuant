package jquant.methods.finitedifferences.solvers;

import jquant.methods.finitedifferences.operators.FdmHestonOp;
import jquant.patterns.LazyObject;
import jquant.processes.HestonProcess;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.utilities.FdmQuantoHelper;

/**
 * Heston finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdmhestonsolver.{hpp,cpp}}.
 * QuantLib holds a {@code Handle<HestonProcess>}; Java stores the process
 * directly, mirroring {@link FdmBlackScholesSolver}.
 */
public class FdmHestonSolver extends LazyObject {
    private final HestonProcess process_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final FdmQuantoHelper quantoHelper_;
    private final LocalVolTermStructure leverageFct_;
    private final double mixingFactor_;

    private Fdm2DimSolver solver_;

    public FdmHestonSolver(HestonProcess process,
                           FdmSolverDesc solverDesc,
                           FdmSchemeDesc schemeDesc,
                           FdmQuantoHelper quantoHelper,
                           LocalVolTermStructure leverageFct,
                           double mixingFactor) {
        process_ = process;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        quantoHelper_ = quantoHelper;
        leverageFct_ = leverageFct;
        mixingFactor_ = mixingFactor;

        registerWith(process_);
        registerWith(quantoHelper_);
    }

    @Override
    protected void performCalculations() {
        final FdmHestonOp op = new FdmHestonOp(
                solverDesc_.mesher, process_, quantoHelper_,
                leverageFct_, mixingFactor_);

        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double s, double v) {
        calculate();
        return solver_.interpolateAt(Math.log(s), v);
    }

    public double deltaAt(double s, double v) {
        calculate();
        return solver_.derivativeX(Math.log(s), v) / s;
    }

    public double gammaAt(double s, double v) {
        calculate();
        final double x = Math.log(s);
        return (solver_.derivativeXX(x, v) - solver_.derivativeX(x, v)) / (s * s);
    }

    public double meanVarianceDeltaAt(double s, double v) {
        calculate();

        final double alpha = process_.rho() * process_.sigma() / s;
        return deltaAt(s, v) + alpha * solver_.derivativeY(Math.log(s), v);
    }

    public double meanVarianceGammaAt(double s, double v) {
        calculate();

        final double x = Math.log(s);
        final double alpha = process_.rho() * process_.sigma() / s;
        return gammaAt(s, v)
                + solver_.derivativeYY(x, v) * alpha * alpha
                + 2 * solver_.derivativeXY(x, v) * alpha / s;
    }

    public double thetaAt(double s, double v) {
        calculate();
        return solver_.thetaAt(Math.log(s), v);
    }
}
