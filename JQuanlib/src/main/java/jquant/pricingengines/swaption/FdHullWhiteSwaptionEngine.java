package jquant.pricingengines.swaption;

import jquant.Handle;
import jquant.instruments.Swaption;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import jquant.methods.finitedifferences.solvers.FdmHullWhiteSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmAffineModelSwapInnerValue;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.processes.OrnsteinUhlenbeckProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-difference swaption engine for the Hull-White model.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/fdhullwhiteswaptionengine.{hpp,cpp}}.
 */
public class FdHullWhiteSwaptionEngine extends Swaption.engine {

    private final HullWhite model_;
    private final int tGrid_;
    private final int xGrid_;
    private final int dampingSteps_;
    private final double invEps_;
    private final FdmSchemeDesc schemeDesc_;

    public FdHullWhiteSwaptionEngine(HullWhite model) {
        this(model, 100, 100, 0, 1e-5, FdmSchemeDesc.Douglas());
    }

    public FdHullWhiteSwaptionEngine(HullWhite model,
                                     int tGrid, int xGrid,
                                     int dampingSteps, double invEps,
                                     FdmSchemeDesc schemeDesc) {
        model_ = model;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        dampingSteps_ = dampingSteps;
        invEps_ = invEps;
        schemeDesc_ = schemeDesc;
        registerWith(model_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(model_ != null, "no model specified");

        // 1. Term structure
        final Handle<YieldTermStructure> ts = model_.termStructure();

        // 2. Mesher
        final DayCounter dc = ts.currentLink().dayCounter();
        final Date referenceDate = ts.currentLink().referenceDate();
        final double maturity = dc.yearFraction(referenceDate,
                arguments_.exercise.lastDate());

        final OrnsteinUhlenbeckProcess process =
                new OrnsteinUhlenbeckProcess(model_.a(), model_.sigma(), 0.0, 0.0);
        final FdmSimpleProcess1dMesher shortRateMesher =
                new FdmSimpleProcess1dMesher(xGrid_, process, maturity, 1, invEps_);
        final FdmMesherComposite mesher =
                new FdmMesherComposite(shortRateMesher);

        // 3. Inner Value Calculator
        final Map<Double, Date> t2d = new HashMap<>();
        for (Date exerciseDate : arguments_.exercise.dates()) {
            final double t = dc.yearFraction(referenceDate, exerciseDate);
            QL_REQUIRE(t >= 0, "exercise dates must not contain past date");
            t2d.put(t, exerciseDate);
        }

        final Handle<YieldTermStructure> disTs = model_.termStructure();
        final Handle<YieldTermStructure> fwdTs =
                arguments_.swap.iborIndex().forwardingTermStructure();

        QL_REQUIRE(TimeUtils.equals(fwdTs.currentLink().dayCounter(), disTs.currentLink().dayCounter()),
                "day counter of forward and discount curve must match");
        QL_REQUIRE(TimeUtils.equals(fwdTs.currentLink().referenceDate(), disTs.currentLink().referenceDate()),
                "reference date of forward and discount curve must match");

        final HullWhite fwdModel = new HullWhite(fwdTs, model_.a(), model_.sigma());
        final FdmAffineModelSwapInnerValue<HullWhite> calculator =
                new FdmAffineModelSwapInnerValue<>(
                        model_, fwdModel, arguments_.swap, t2d, mesher, 0);

        // 4. Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new ArrayList<>(), arguments_.exercise,
                        mesher, calculator, referenceDate, dc);

        // 5. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, dampingSteps_);

        final FdmHullWhiteSolver solver = new FdmHullWhiteSolver(
                new Handle<>(model_, false), solverDesc, schemeDesc_);

        results_.value = solver.valueAt(0.0);
    }
}
