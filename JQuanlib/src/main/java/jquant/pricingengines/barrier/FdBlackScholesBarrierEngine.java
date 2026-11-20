package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.EarlyExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmAmericanStepCondition;
import jquant.methods.finitedifferences.stepconditions.FdmBermudanStepCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmDirichletBoundary;
import jquant.methods.finitedifferences.utilities.FdmDividendHandler;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.close_enough;

/**
 * Finite-differences Black-Scholes single-barrier option engine.
 *
 * <p>Port of {@code ql/pricingengines/barrier/fdblackscholesbarrierengine.{hpp,cpp}}.
 */
public class FdBlackScholesBarrierEngine extends jquant.BarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean localVol_;
    private final double illegalLocalVolOverwrite_;

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process) {
        this(process, Collections.emptyList(), 100, 100, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid) {
        this(process, Collections.emptyList(), tGrid, xGrid, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc) {
        this(process, Collections.emptyList(), tGrid, xGrid, dampingSteps,
                schemeDesc, false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       boolean localVol,
                                       double illegalLocalVolOverwrite) {
        this(process, Collections.emptyList(), tGrid, xGrid, dampingSteps,
                schemeDesc, localVol, illegalLocalVolOverwrite);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends) {
        this(process, dividends, 100, 100, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends,
                                       int tGrid, int xGrid) {
        this(process, dividends, tGrid, xGrid, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc) {
        this(process, dividends, tGrid, xGrid, dampingSteps,
                schemeDesc, false, -NULL_REAL);
    }

    public FdBlackScholesBarrierEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       boolean localVol,
                                       double illegalLocalVolOverwrite) {
        process_ = process;
        dividends_ = new ArrayList<>(dividends);
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        localVol_ = localVol;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;

        registerWith(process_);
    }

    @Override
    public void calculate() {
        // 1. Mesher
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked type payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        QL_REQUIRE(payoff.strike() > 0.0, "strike must be positive");

        final Exercise.Type exerciseType = arguments_.exercise.type();
        QL_REQUIRE(exerciseType == Exercise.Type.European
                        || exerciseType == Exercise.Type.American
                        || exerciseType == Exercise.Type.Bermudan,
                "exercise type is not supported");

        // knock-in options are priced via in-out parity,
        // which does not hold for early exercise
        QL_REQUIRE(exerciseType == Exercise.Type.European
                        || arguments_.barrierType == Barrier.Type.DownOut
                        || arguments_.barrierType == Barrier.Type.UpOut,
                "only european style knock-in options are supported");

        final double spot = process_.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(spot), "barrier touched");

        final Date settlementDate = process_.riskFreeRate().getValue().referenceDate();
        boolean exercisableToday = false;
        if (exerciseType == Exercise.Type.American) {
            exercisableToday = TimeUtils.leq(arguments_.exercise.date(0), settlementDate)
                    && TimeUtils.leq(settlementDate, arguments_.exercise.lastDate());
        } else if (exerciseType == Exercise.Type.Bermudan) {
            for (Date exerciseDate : arguments_.exercise.dates()) {
                if (TimeUtils.equals(exerciseDate, settlementDate)) {
                    exercisableToday = true;
                    break;
                }
            }
        }

        // on the barrier the option is knocked out before it can be
        // exercised; when the intrinsic value there exceeds the rebate,
        // the holder would exercise just inside it instead, so the value
        // jumps at the barrier and the greeks are undefined
        final double barrier = arguments_.barrier;
        if (exercisableToday
                && close_enough(spot, barrier)
                && payoff.value(barrier) > arguments_.rebate) {
            results_.value = arguments_.rebate;
            return;
        }

        final double maturity = process_.time(arguments_.exercise.lastDate());

        double xMin = Double.NaN;
        double xMax = Double.NaN;
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.DownOut) {
            xMin = Math.log(barrier);
        }
        if (arguments_.barrierType == Barrier.Type.UpIn
                || arguments_.barrierType == Barrier.Type.UpOut) {
            xMax = Math.log(barrier);
        }

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, process_, maturity, payoff.strike(),
                xMin, xMax, 0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                dividends_, null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final List<FdmStepCondition> stepConditions = new ArrayList<>();
        final List<List<Double>> stoppingTimes = new ArrayList<>();

        // 3.1 Step condition if discrete dividends
        final FdmDividendHandler dividendCondition = new FdmDividendHandler(
                dividends_, mesher,
                process_.riskFreeRate().getValue().referenceDate(),
                process_.riskFreeRate().getValue().dayCounter(), 0);

        if (!dividends_.isEmpty()) {
            stepConditions.add(dividendCondition);
            final List<Double> dividendTimes =
                    new ArrayList<>(dividendCondition.dividendTimes());
            // this effectively excludes times after maturity
            for (int i = 0; i < dividendTimes.size(); ++i)
                dividendTimes.set(i, Math.min(maturity, dividendTimes.get(i)));
            stoppingTimes.add(dividendTimes);
        }

        // 3.2 Step condition if early exercise (knock-out options only)
        double exerciseStart = Double.NaN;
        if (exerciseType == Exercise.Type.American) {
            exerciseStart = process_.time(arguments_.exercise.date(0));
            stepConditions.add(
                    new FdmAmericanStepCondition(mesher, calculator, exerciseStart));
        } else if (exerciseType == Exercise.Type.Bermudan) {
            final FdmBermudanStepCondition bermudanCondition =
                    new FdmBermudanStepCondition(
                            arguments_.exercise.dates(),
                            process_.riskFreeRate().getValue().referenceDate(),
                            process_.riskFreeRate().getValue().dayCounter(),
                            mesher, calculator);
            stepConditions.add(bermudanCondition);
            stoppingTimes.add(bermudanCondition.exerciseTimes());
        }

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions
        final FdmDirichletBoundary.Side side =
                (arguments_.barrierType == Barrier.Type.DownIn
                        || arguments_.barrierType == Barrier.Type.DownOut)
                        ? FdmDirichletBoundary.Side.Lower
                        : FdmDirichletBoundary.Side.Upper;

        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();
        if (exerciseType == Exercise.Type.American) {
            // once exercisable, the holder exercises rather than being
            // knocked out for less than the intrinsic value at the barrier
            final double rebate = arguments_.rebate;
            final double exerciseValue = Math.max(rebate, payoff.value(barrier));
            final double start = exerciseStart;
            boundaries.add(new FdmTimeDepDirichletBoundary(
                    mesher,
                    (java.util.function.DoubleUnaryOperator)
                            t -> t >= start ? exerciseValue : rebate,
                    0, side));
        } else {
            boundaries.add(
                    new FdmDirichletBoundary(mesher, arguments_.rebate, 0, side));
        }

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, dampingSteps_);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_,
                localVol_, illegalLocalVolOverwrite_, null);

        results_.value = solver.valueAt(spot);
        results_.delta = solver.deltaAt(spot);
        results_.gamma = solver.gammaAt(spot);
        results_.theta = solver.thetaAt(spot);

        // As in FdBlackScholesVanillaEngine, the step condition enforces
        // the exercise value on grid nodes only and the interpolant can
        // undershoot it in between; floor the value when the holder can
        // exercise on the reference date and the payoff is paid then.
        // Greeks stay on the interpolant.
        boolean exercisable = exercisableToday;
        if (exercisable) {
            if (arguments_.exercise instanceof EarlyExercise early
                    && early.payoffAtExpiry())
                exercisable = false;
        }
        if (exercisable) {
            final double exerciseValue = payoff.value(spot);
            if (results_.value < exerciseValue)
                results_.value = exerciseValue;
        }

        // 6. Calculate vanilla option and rebate for in-barriers
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.UpIn) {
            // Cast the payoff
            final StrikedTypePayoff strikedPayoff =
                    (StrikedTypePayoff) arguments_.payoff;

            // Calculate the vanilla option
            final EuropeanOption vanillaOption =
                    new EuropeanOption(strikedPayoff, arguments_.exercise);

            vanillaOption.setPricingEngine(
                    new FdBlackScholesVanillaEngine(
                            process_, dividends_, tGrid_, xGrid_,
                            0, // dampingSteps
                            schemeDesc_, localVol_, illegalLocalVolOverwrite_,
                            FdBlackScholesVanillaEngine.CashDividendModel.Spot));

            // Calculate the rebate value
            final jquant.BarrierOption rebateOption =
                    new jquant.BarrierOption(arguments_.barrierType,
                            barrier,
                            arguments_.rebate,
                            strikedPayoff, arguments_.exercise);

            final int minGridSize = 50;
            final int rebateDampingSteps =
                    (dampingSteps_ > 0) ? Math.min(1, dampingSteps_ / 2) : 0;

            rebateOption.setPricingEngine(new FdBlackScholesRebateEngine(
                    process_, dividends_, tGrid_,
                    Math.max(minGridSize, xGrid_ / 5),
                    rebateDampingSteps, schemeDesc_, localVol_,
                    illegalLocalVolOverwrite_));

            results_.value = vanillaOption.NPV() + rebateOption.NPV() - results_.value;
            results_.delta = vanillaOption.delta() + rebateOption.delta() - results_.delta;
            results_.gamma = vanillaOption.gamma() + rebateOption.gamma() - results_.gamma;
            results_.theta = vanillaOption.theta() + rebateOption.theta() - results_.theta;
        }
    }
}
