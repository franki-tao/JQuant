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
 * Heston Monte Carlo pricing engine for discrete arithmetic average price
 * Asian options.
 *
 * <p>Port of {@code MCDiscreteArithmeticAPHestonEngine} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_price_heston.hpp}.
 */
public class MCDiscreteArithmeticAPHestonEngine
        extends MCDiscreteAveragingAsianEngineBase {

    public MCDiscreteArithmeticAPHestonEngine(
            HestonProcess process,
            boolean antitheticVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            int timeSteps,
            int timeStepsPerYear,
            boolean controlVariate,
            RngType rngType) {
        super(process, false, antitheticVariate, controlVariate,
                requiredSamples, requiredTolerance, maxSamples, seed,
                timeSteps, timeStepsPerYear, false, rngType);
        QL_REQUIRE(timeSteps == MathUtils.NULL_SIZE
                        || timeStepsPerYear == MathUtils.NULL_SIZE,
                "both time steps and time steps per year were provided");
    }

    private List<Integer> fixingIndexes() {
        final TimeGrid timeGrid = timeGrid();
        final List<Double> fixingTimes = timeGrid.mandatoryTimes();
        final List<Integer> fixingIndexes = new ArrayList<>();
        for (double fixingTime : fixingTimes) {
            fixingIndexes.add(timeGrid.closestIndex(fixingTime));
        }
        return fixingIndexes;
    }

    @Override
    protected MultiPathPricer multiPathPricer() {
        final List<Integer> fixingIndexes = fixingIndexes();

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

        return new ArithmeticAPOHestonPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().currentLink()
                        .discount(exercise.lastDate()),
                fixingIndexes,
                arguments_.runningAccumulator,
                arguments_.pastFixings);
    }

    @Override
    protected MultiPathPricer controlMultiPathPricer() {
        // TODO: Currently the analytic pricer does not support seasoned
        // asian options. Once that is available, we will be able to pass
        // seasoning details to the path pricer (and to the analytic pricer).
        final List<Integer> fixingIndexes = fixingIndexes();

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
                fixingIndexes);
    }

    @Override
    protected jquant.PricingEngine controlPricingEngine() {
        QL_REQUIRE(process_ instanceof HestonProcess,
                "Heston-like process required");
        return new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(
                (HestonProcess) process_);
    }
}
