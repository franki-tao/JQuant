package jquant.pricingengines.barrier;

import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.Exercise;
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
import jquant.methods.finitedifferences.stepconditions.FdmStepCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmDirichletBoundary;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.HestonProcess;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-differences Heston double-barrier option engine.
 *
 * <p>Port of
 * {@code ql/pricingengines/barrier/fdhestondoublebarrierengine.{hpp,cpp}}.
 * Only Knock-Out double-barrier options are supported.
 */
public class FdHestonDoubleBarrierEngine extends DoubleBarrierOption.engine {
    private final HestonModel model_;
    private final int tGrid_, xGrid_, vGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final LocalVolTermStructure leverageFct_;
    private final double mixingFactor_;

    public FdHestonDoubleBarrierEngine(final HestonModel model) {
        this(model, 100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonDoubleBarrierEngine(final HestonModel model,
                                       int tGrid, int xGrid, int vGrid) {
        this(model, tGrid, xGrid, vGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonDoubleBarrierEngine(final HestonModel model,
                                       int tGrid, int xGrid, int vGrid,
                                       int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       LocalVolTermStructure leverageFct,
                                       double mixingFactor) {
        model_ = model;
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

        QL_REQUIRE(arguments_.barrierType == DoubleBarrier.Type.KnockOut,
                "only Knock-Out double barrier options are supported");

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
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;

        double xMin = Math.log(arguments_.barrier_lo);
        double xMax = Math.log(arguments_.barrier_hi);

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_,
                FdmBlackScholesMesher.processHelper(
                        process.s0(), process.dividendYield(),
                        process.riskFreeRate(), vMesher.volaEstimate()),
                maturity, payoff.strike(), xMin, xMax,
                0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                Collections.<Dividend>emptyList(), null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(
                Arrays.asList(equityMesher, vMesher));

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final List<FdmStepCondition> stepConditions = new ArrayList<>();
        final List<List<Double>> stoppingTimes = new ArrayList<>();

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "only european style option are supported");

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        boundaries.add(new FdmDirichletBoundary(
                mesher, arguments_.rebate, 0,
                FdmDirichletBoundary.Side.Lower));
        boundaries.add(new FdmDirichletBoundary(
                mesher, arguments_.rebate, 0,
                FdmDirichletBoundary.Side.Upper));

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
