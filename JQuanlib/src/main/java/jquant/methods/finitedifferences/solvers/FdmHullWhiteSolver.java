package jquant.methods.finitedifferences.solvers;

import jquant.Handle;
import jquant.methods.finitedifferences.operators.FdmHullWhiteOp;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.patterns.LazyObject;

/**
 * Finite-difference solver for the Hull-White model.
 *
 * <p>Ported from QuantLib
 * {@code ql/methods/finitedifferences/solvers/fdmhullwhitesolver.{hpp,cpp}}.
 */
public class FdmHullWhiteSolver extends LazyObject {

    private final Handle<HullWhite> model_;
    private final FdmSolverDesc solverDesc_;
    private final FdmSchemeDesc schemeDesc_;

    private Fdm1DimSolver solver_;

    public FdmHullWhiteSolver(Handle<HullWhite> model,
                              FdmSolverDesc solverDesc) {
        this(model, solverDesc, FdmSchemeDesc.Hundsdorfer());
    }

    public FdmHullWhiteSolver(Handle<HullWhite> model,
                              FdmSolverDesc solverDesc,
                              FdmSchemeDesc schemeDesc) {
        model_ = model;
        solverDesc_ = solverDesc;
        schemeDesc_ = schemeDesc;
        registerWith(model_.get());
    }

    @Override
    protected void performCalculations() {
        final FdmHullWhiteOp op = new FdmHullWhiteOp(
                solverDesc_.mesher, model_.currentLink(), 0);
        solver_ = new Fdm1DimSolver(solverDesc_, schemeDesc_, op);
    }

    public double valueAt(double r) {
        calculate();
        return solver_.interpolateAt(r);
    }
}
