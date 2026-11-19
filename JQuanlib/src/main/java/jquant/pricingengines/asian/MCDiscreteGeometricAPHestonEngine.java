package jquant.pricingengines.asian;

import jquant.EuropeanExercise;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Heston Monte Carlo pricing engine for discrete geometric average price
 * Asian options.
 *
 * <p>Port of {@code MCDiscreteGeometricAPHestonEngine} from
 * {@code ql/pricingengines/asian/mc_discr_geom_av_price_heston.hpp}.
 */
public class MCDiscreteGeometricAPHestonEngine
        extends MCDiscreteAveragingAsianEngineBase {

    public MCDiscreteGeometricAPHestonEngine(
            HestonProcess process,
            boolean antitheticVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            int timeSteps,
            int timeStepsPerYear,
            RngType rngType) {
        super(process, false, antitheticVariate, false,
                requiredSamples, requiredTolerance, maxSamples, seed,
                timeSteps, timeStepsPerYear, false, rngType);
        QL_REQUIRE(timeSteps == MathUtils.NULL_SIZE
                        || timeStepsPerYear == MathUtils.NULL_SIZE,
                "both time steps and time steps per year were provided");
    }

    @Override
    protected MultiPathPricer multiPathPricer() {
        final TimeGrid timeGrid = timeGrid();
        final List<Double> fixingTimes = timeGrid.mandatoryTimes();
        final List<Integer> fixingIndexes = new ArrayList<>();
        for (double fixingTime : fixingTimes) {
            fixingIndexes.add(timeGrid.closestIndex(fixingTime));
        }

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        QL_REQUIRE(arguments_.exercise instanceof EuropeanExercise,
                "wrong exercise given");
        final EuropeanExercise exercise =
                (EuropeanExercise) arguments_.exercise;

        QL_REQUIRE(process_ instanceof HestonProcess,
                "Heston like process required");
        final HestonProcess process = (HestonProcess) process_;

        return new GeometricAPOHestonPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().currentLink()
                        .discount(exercise.lastDate()),
                fixingIndexes,
                arguments_.runningAccumulator,
                arguments_.pastFixings);
    }
}
