package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Exercise;
import jquant.Option;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmHestonLocalVolatilityVarianceMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmHestonSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmDirichletBoundary;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.HestonProcess;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-differences Heston rebate option engine.
 *
 * <p>Port of {@code ql/pricingengines/barrier/fdhestonrebateengine.{hpp,cpp}}.
 */
public class FdHestonRebateEngine extends jquant.BarrierOption.engine {
    private final HestonModel model_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, vGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final LocalVolTermStructure leverageFct_;
    private final double mixingFactor_;

    public FdHestonRebateEngine(final HestonModel model) {
        this(model, Collections.emptyList(), 100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonRebateEngine(final HestonModel model,
                                int tGrid, int xGrid, int vGrid) {
        this(model, Collections.emptyList(), tGrid, xGrid, vGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonRebateEngine(final HestonModel model,
                                int tGrid, int xGrid, int vGrid,
                                int dampingSteps,
                                FdmSchemeDesc schemeDesc,
                                LocalVolTermStructure leverageFct,
                                double mixingFactor) {
        this(model, Collections.emptyList(), tGrid, xGrid, vGrid,
                dampingSteps, schemeDesc, leverageFct, mixingFactor);
    }

    public FdHestonRebateEngine(final HestonModel model,
                                List<Dividend> dividends) {
        this(model, dividends, 100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonRebateEngine(final HestonModel model,
                                List<Dividend> dividends,
                                int tGrid, int xGrid, int vGrid,
                                int dampingSteps,
                                FdmSchemeDesc schemeDesc,
                                LocalVolTermStructure leverageFct,
                                double mixingFactor) {
        model_ = model;
        dividends_ = new ArrayList<>(dividends);
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        vGrid_ = vGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        leverageFct_ = leverageFct;
        mixingFactor_ = mixingFactor;

        registerWith(model_);
    }

    @Override
    public void calculate() {
        // 1. Mesher
        final HestonProcess process = model_.process();
        final double maturity = process.time(arguments_.exercise.lastDate());

        // 1.1 The variance mesher
        final int tGridMin = 5;
        final int tGridAvgSteps = Math.max(tGridMin, tGrid_ / 50);

        final FdmHestonLocalVolatilityVarianceMesher vMesher =
                new FdmHestonLocalVolatilityVarianceMesher(
                        vGrid_, process, leverageFct_, maturity,
                        tGridAvgSteps, 0.0001, mixingFactor_);

        // 1.2 The equity mesher
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;

        double xMin = Double.NaN;
        double xMax = Double.NaN;
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.DownOut) {
            xMin = Math.log(arguments_.barrier);
        }
        if (arguments_.barrierType == Barrier.Type.UpIn
                || arguments_.barrierType == Barrier.Type.UpOut) {
            xMax = Math.log(arguments_.barrier);
        }

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_,
                FdmBlackScholesMesher.processHelper(
                        process.s0(), process.dividendYield(),
                        process.riskFreeRate(), vMesher.volaEstimate()),
                maturity, payoff.strike(),
                xMin, xMax, 0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                dividends_, null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(
                Arrays.asList(equityMesher, vMesher));

        // 2. Calculator
        final CashOrNothingPayoff rebatePayoff =
                new CashOrNothingPayoff(Option.Type.Call, 0.0, arguments_.rebate);
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(rebatePayoff, mesher, 0);

        // 3. Step conditions
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "only european style option are supported");

        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends_, arguments_.exercise,
                        mesher, calculator,
                        process.riskFreeRate().getValue().referenceDate(),
                        process.riskFreeRate().getValue().dayCounter());

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.DownOut) {
            boundaries.add(new FdmDirichletBoundary(
                    mesher, arguments_.rebate, 0,
                    FdmDirichletBoundary.Side.Lower));
        }
        if (arguments_.barrierType == Barrier.Type.UpIn
                || arguments_.barrierType == Barrier.Type.UpOut) {
            boundaries.add(new FdmDirichletBoundary(
                    mesher, arguments_.rebate, 0,
                    FdmDirichletBoundary.Side.Upper));
        }

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, dampingSteps_);

        final FdmHestonSolver solver = new FdmHestonSolver(
                process, solverDesc, schemeDesc_,
                null, leverageFct_, mixingFactor_);

        final double spot = process.s0().getValue().value();
        results_.value = solver.valueAt(spot, process.v0());
        results_.delta = solver.deltaAt(spot, process.v0());
        results_.gamma = solver.gammaAt(spot, process.v0());
        results_.theta = solver.thetaAt(spot, process.v0());
    }
}
