package jquant.pricingengines.asian;

import jquant.Average;
import jquant.DiscreteAveragingAsianOption;
import jquant.Exercise;
import jquant.math.Point;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSimple2dBSSolver;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmArithmeticAverageCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepCondition;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-differences Black-Scholes arithmetic Asian option engine.
 *
 * <p>The problem is solved on a three-dimensional grid (time, spot,
 * running average); the equity and average directions use the
 * two-dimensional solver.
 * Port of {@code FdBlackScholesAsianEngine} from
 * {@code ql/pricingengines/asian/fdblackscholesasianengine.{hpp,cpp}}.
 */
public class FdBlackScholesAsianEngine
        extends DiscreteAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final int tGrid_, xGrid_, aGrid_;
    private final FdmSchemeDesc schemeDesc_;

    public FdBlackScholesAsianEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, 100, 100, 50, FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesAsianEngine(
            GeneralizedBlackScholesProcess process,
            int tGrid, int xGrid, int aGrid) {
        this(process, tGrid, xGrid, aGrid, FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesAsianEngine(
            GeneralizedBlackScholesProcess process,
            int tGrid, int xGrid, int aGrid,
            FdmSchemeDesc schemeDesc) {
        process_ = process;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        aGrid_ = aGrid;
        schemeDesc_ = schemeDesc;

        process_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "European exercise supported only");
        QL_REQUIRE(arguments_.averageType == Average.Type.Arithmetic,
                "Arithmetic averaging supported only");
        QL_REQUIRE(arguments_.runningAccumulator == 0
                        || arguments_.pastFixings > 0,
                "Running average requires at least one past fixing");

        // 1. Mesher
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;
        final double maturity = process_.time(arguments_.exercise.lastDate());
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, process_, maturity, payoff.strike(),
                Double.NaN, Double.NaN, 0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                Collections.emptyList(), null, 0.0);

        final double spot = process_.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        final double avg = (arguments_.runningAccumulator == 0)
                ? spot
                : arguments_.runningAccumulator / arguments_.pastFixings;

        final double normInvEps =
                new InverseCumulativeNormal().value(1 - 0.0001);
        final double sigmaSqrtT = process_.blackVolatility().getValue()
                .blackVol(maturity, payoff.strike(), false)
                * Math.sqrt(maturity);
        final double r = sigmaSqrtT * normInvEps;

        final double xMin = Math.min(Math.log(avg) - 0.25 * r,
                Math.log(spot) - 1.5 * r);
        final double xMax = Math.max(Math.log(avg) + 0.25 * r,
                Math.log(spot) + 1.5 * r);

        final Fdm1dMesher averageMesher = new FdmBlackScholesMesher(
                aGrid_, process_, maturity, payoff.strike(),
                xMin, xMax, 0.0001, 1.5,
                new Point<>(Double.NaN, Double.NaN),
                Collections.emptyList(), null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(
                List.of(equityMesher, averageMesher));

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 1);

        // 3. Step conditions
        final List<FdmStepCondition> stepConditions = new ArrayList<>();
        final List<List<Double>> stoppingTimes = new ArrayList<>();

        // 3.1 Arithmetic average step conditions
        final List<Double> averageTimes = new ArrayList<>();
        for (Date fixingDate : arguments_.fixingDates) {
            final double t = process_.time(fixingDate);
            QL_REQUIRE(t >= 0, "Fixing dates must not contain past date");
            averageTimes.add(t);
        }
        stoppingTimes.add(averageTimes);
        stepConditions.add(new FdmArithmeticAverageCondition(
                averageTimes, arguments_.runningAccumulator,
                arguments_.pastFixings, mesher, 0));

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries =
                new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, 0);

        final FdmSimple2dBSSolver solver = new FdmSimple2dBSSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_);

        results_.value = solver.valueAt(spot, avg);
        results_.delta = solver.deltaAt(spot, avg, spot * 0.01);
        results_.gamma = solver.gammaAt(spot, avg, spot * 0.01);
    }
}
