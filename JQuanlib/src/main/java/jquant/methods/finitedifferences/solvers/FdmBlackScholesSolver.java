package jquant.methods.finitedifferences.solvers;

import jquant.methods.finitedifferences.operators.FdmBlackScholesOp;
import jquant.patterns.LazyObject;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.utilities.FdmQuantoHelper;

/**
 * Black-Scholes finite-difference solver.
 *
 * <p>Port of {@code ql/methods/finitedifferences/solvers/fdmblackscholessolver.{hpp,cpp}}.
 */
public class FdmBlackScholesSolver extends LazyObject {
    private final GeneralizedBlackScholesProcess process_;
    private final double strike_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean localVol_;
    private final double illegalLocalVolOverwrite_;
    private final FdmQuantoHelper quantoHelper_;

    private Fdm1DimSolver solver_;

    public FdmBlackScholesSolver(GeneralizedBlackScholesProcess process,
                                 double strike,
                                 FdmSolverDesc solverDesc,
                                 FdmSchemeDesc schemeDesc,
                                 boolean localVol,
                                 double illegalLocalVolOverwrite,
                                 FdmQuantoHelper quantoHelper) {
        process_ = process;
        strike_ = strike;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        localVol_ = localVol;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        quantoHelper_ = quantoHelper;
    }

    @Override
    protected void performCalculations() {
        final FdmBlackScholesOp op = new FdmBlackScholesOp(
                solverDesc_.mesher, process_, strike_,
                localVol_, illegalLocalVolOverwrite_, 0, quantoHelper_);

        solver_ = new Fdm1DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double s) {
        calculate();
        return solver_.interpolateAt(Math.log(s));
    }

    public double deltaAt(double s) {
        calculate();
        return solver_.derivativeX(Math.log(s)) / s;
    }

    public double gammaAt(double s) {
        calculate();
        return (solver_.derivativeXX(Math.log(s))
                - solver_.derivativeX(Math.log(s))) / (s * s);
    }

    public double thetaAt(double s) {
        calculate();
        return solver_.thetaAt(Math.log(s));
    }
}
