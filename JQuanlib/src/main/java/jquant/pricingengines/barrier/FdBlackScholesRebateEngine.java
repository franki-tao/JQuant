package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Exercise;
import jquant.Option;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmDirichletBoundary;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences Black-Scholes rebate option engine.
 *
 * <p>Port of {@code ql/pricingengines/barrier/fdblackscholesrebateengine.{hpp,cpp}}.
 */
public class FdBlackScholesRebateEngine extends jquant.BarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean localVol_;
    private final double illegalLocalVolOverwrite_;

    public FdBlackScholesRebateEngine(GeneralizedBlackScholesProcess process) {
        this(process, Collections.emptyList(), 100, 100, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesRebateEngine(GeneralizedBlackScholesProcess process,
                                      int tGrid, int xGrid) {
        this(process, Collections.emptyList(), tGrid, xGrid, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesRebateEngine(GeneralizedBlackScholesProcess process,
                                      int tGrid, int xGrid, int dampingSteps,
                                      FdmSchemeDesc schemeDesc,
                                      boolean localVol,
                                      double illegalLocalVolOverwrite) {
        this(process, Collections.emptyList(), tGrid, xGrid, dampingSteps,
                schemeDesc, localVol, illegalLocalVolOverwrite);
    }

    public FdBlackScholesRebateEngine(GeneralizedBlackScholesProcess process,
                                      List<Dividend> dividends) {
        this(process, dividends, 100, 100, 0,
                FdmSchemeDesc.Douglas(), false, -NULL_REAL);
    }

    public FdBlackScholesRebateEngine(GeneralizedBlackScholesProcess process,
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
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;
        final double maturity = process_.time(arguments_.exercise.lastDate());

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
                xGrid_, process_, maturity, payoff.strike(),
                xMin, xMax, 0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                dividends_, null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final CashOrNothingPayoff rebatePayoff =
                new CashOrNothingPayoff(Option.Type.Call, 0.0, arguments_.rebate);
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(rebatePayoff, mesher, 0);

        // 3. Step conditions
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "only european style option are supported");

        final Date referenceDate =
                process_.riskFreeRate().getValue().referenceDate();
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends_, arguments_.exercise,
                        mesher, calculator,
                        referenceDate,
                        process_.riskFreeRate().getValue().dayCounter());

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

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_,
                localVol_, illegalLocalVolOverwrite_, null);

        final double spot = process_.x0();
        results_.value = solver.valueAt(spot);
        results_.delta = solver.deltaAt(spot);
        results_.gamma = solver.gammaAt(spot);
        results_.theta = solver.thetaAt(spot);
    }
}
