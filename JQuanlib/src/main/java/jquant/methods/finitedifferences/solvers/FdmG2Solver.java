package jquant.methods.finitedifferences.solvers;

import jquant.Handle;
import jquant.methods.finitedifferences.operators.FdmG2Op;
import jquant.models.shortrate.twofactormodels.G2;
import jquant.patterns.LazyObject;

/**
 * Finite-difference solver for the G2 two-factor model.
 *
 * <p>Ported from QuantLib
 * {@code ql/methods/finitedifferences/solvers/fdmg2solver.{hpp,cpp}}.
 */
public class FdmG2Solver extends LazyObject {

    private final Handle<G2> model_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm2DimSolver solver_;

    public FdmG2Solver(Handle<G2> model,
                       FdmSolverDesc solverDesc) {
        this(model, solverDesc, FdmSchemeDesc.Hundsdorfer());
    }

    public FdmG2Solver(Handle<G2> model,
                       FdmSolverDesc solverDesc,
                       FdmSchemeDesc schemeDesc) {
        model_ = model;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        registerWith(model_.get());
    }

    @Override
    protected void performCalculations() {
        final FdmG2Op op = new FdmG2Op(
                solverDesc_.mesher, model_.currentLink(), 0, 1);

        solver_ = new Fdm2DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double x, double y) {
        calculate();
        return solver_.interpolateAt(x, y);
    }
}
