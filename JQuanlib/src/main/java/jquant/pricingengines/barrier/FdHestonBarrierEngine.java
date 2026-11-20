package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.EuropeanOption;
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
import jquant.methods.finitedifferences.utilities.FdmDividendHandler;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.FdHestonVanillaEngine;
import jquant.processes.HestonProcess;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-differences Heston single-barrier option engine.
 *
 * <p>Port of {@code ql/pricingengines/barrier/fdhestonbarrierengine.{hpp,cpp}}.
 */
public class FdHestonBarrierEngine extends jquant.BarrierOption.engine {
    private final HestonModel model_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, vGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final LocalVolTermStructure leverageFct_;
    private final double mixingFactor_;

    public FdHestonBarrierEngine(final HestonModel model) {
        this(model, Collections.emptyList(), 100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonBarrierEngine(final HestonModel model,
                                 int tGrid, int xGrid, int vGrid) {
        this(model, Collections.emptyList(), tGrid, xGrid, vGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonBarrierEngine(final HestonModel model,
                                 int tGrid, int xGrid, int vGrid,
                                 int dampingSteps,
                                 FdmSchemeDesc schemeDesc,
                                 LocalVolTermStructure leverageFct,
                                 double mixingFactor) {
        this(model, Collections.emptyList(), tGrid, xGrid, vGrid,
                dampingSteps, schemeDesc, leverageFct, mixingFactor);
    }

    public FdHestonBarrierEngine(final HestonModel model,
                                 List<Dividend> dividends) {
        this(model, dividends, 100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonBarrierEngine(final HestonModel model,
                                 List<Dividend> dividends,
                                 int tGrid, int xGrid, int vGrid) {
        this(model, dividends, tGrid, xGrid, vGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);
    }

    public FdHestonBarrierEngine(final HestonModel model,
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
        final double barrier = arguments_.barrier;
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.DownOut) {
            xMin = Math.log(barrier);
        }
        if (arguments_.barrierType == Barrier.Type.UpIn
                || arguments_.barrierType == Barrier.Type.UpOut) {
            xMax = Math.log(barrier);
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
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final List<FdmStepCondition> stepConditions = new ArrayList<>();
        final List<List<Double>> stoppingTimes = new ArrayList<>();

        // 3.1 Step condition if discrete dividends
        final FdmDividendHandler dividendCondition = new FdmDividendHandler(
                dividends_, mesher,
                process.riskFreeRate().getValue().referenceDate(),
                process.riskFreeRate().getValue().dayCounter(), 0);

        if (!dividends_.isEmpty()) {
            stepConditions.add(dividendCondition);
            final List<Double> dividendTimes =
                    new ArrayList<>(dividendCondition.dividendTimes());
            // this effectively excludes times after maturity
            for (int i = 0; i < dividendTimes.size(); ++i)
                dividendTimes.set(i, Math.min(maturity, dividendTimes.get(i)));
            stoppingTimes.add(dividendTimes);
        }

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "only european style option are supported");

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

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

        // 6. Calculate vanilla option and rebate for in-barriers
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.UpIn) {
            // Cast the payoff
            final StrikedTypePayoff strikedPayoff =
                    (StrikedTypePayoff) arguments_.payoff;

            // Calculate the vanilla option
            final EuropeanOption vanillaOption =
                    new EuropeanOption(strikedPayoff, arguments_.exercise);
            vanillaOption.setPricingEngine(new FdHestonVanillaEngine(
                    model_, dividends_,
                    tGrid_, xGrid_, vGrid_, dampingSteps_,
                    schemeDesc_, null, 1.0));

            // Calculate the rebate value
            final jquant.BarrierOption rebateOption =
                    new jquant.BarrierOption(arguments_.barrierType,
                            barrier,
                            arguments_.rebate,
                            strikedPayoff, arguments_.exercise);
            final int xGridMin = 20;
            final int vGridMin = 10;
            final int rebateDampingSteps =
                    (dampingSteps_ > 0) ? Math.min(1, dampingSteps_ / 2) : 0;
            rebateOption.setPricingEngine(new FdHestonRebateEngine(
                    model_, dividends_,
                    tGrid_,
                    Math.max(xGridMin, xGrid_ / 4),
                    Math.max(vGridMin, vGrid_ / 4),
                    rebateDampingSteps,
                    schemeDesc_, null, 1.0));

            results_.value = vanillaOption.NPV() + rebateOption.NPV() - results_.value;
            results_.delta = vanillaOption.delta() + rebateOption.delta() - results_.delta;
            results_.gamma = vanillaOption.gamma() + rebateOption.gamma() - results_.gamma;
            results_.theta = vanillaOption.theta() + rebateOption.theta() - results_.theta;
        }
    }
}
