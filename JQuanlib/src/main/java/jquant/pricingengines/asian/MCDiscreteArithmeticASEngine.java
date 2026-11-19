package jquant.pricingengines.asian;

import jquant.EuropeanExercise;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

/**
 * Monte Carlo pricing engine for discrete arithmetic average-strike Asian
 * options.
 *
 * <p>Port of {@code MCDiscreteArithmeticASEngine} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_strike.hpp}.
 */
public class MCDiscreteArithmeticASEngine
        extends MCDiscreteAveragingAsianEngineBase {

    public MCDiscreteArithmeticASEngine(
            GeneralizedBlackScholesProcess process,
            boolean brownianBridge,
            boolean antitheticVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            RngType rngType) {
        super(process, brownianBridge, antitheticVariate, false,
                requiredSamples, requiredTolerance, maxSamples, seed,
                NULL_SIZE, NULL_SIZE, true, rngType);
    }

    @Override
    protected PathPricer pathPricer() {
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        QL_REQUIRE(arguments_.exercise instanceof EuropeanExercise,
                "wrong exercise given");
        final EuropeanExercise exercise =
                (EuropeanExercise) arguments_.exercise;

        QL_REQUIRE(process_ instanceof GeneralizedBlackScholesProcess,
                "Black-Scholes process required");
        final GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;

        // When the exercise date was added to the time grid (i.e., it
        // falls after the last fixing), tell the path pricer how many
        // grid points are fixings so it can exclude the exercise point
        // from the average.
        int fixingCount = MathUtils.NULL_SIZE;
        if (includeExerciseDate_) {
            QL_REQUIRE(timeSteps_ == MathUtils.NULL_SIZE
                            && timeStepsPerYear_ == MathUtils.NULL_SIZE,
                    "extra time steps are not supported when "
                            + "includeExerciseDate is enabled");
            final TimeGrid grid = timeGrid();
            final Date lastFixingDate =
                    arguments_.fixingDates.get(arguments_.fixingDates.size() - 1);
            final double lastFixing = process.time(lastFixingDate);
            final double exerciseTime = process.time(exercise.lastDate());
            if (exerciseTime > lastFixing) {
                // exercise date was added to the grid; path has one
                // extra point at the end that is NOT a fixing
                fixingCount = grid.size() - 1;
            }
        }

        return new ArithmeticASOPathPricer(
                payoff.optionType(),
                process.riskFreeRate().getValue()
                        .discount(exercise.lastDate(), false),
                arguments_.runningAccumulator,
                arguments_.pastFixings,
                fixingCount);
    }
}
