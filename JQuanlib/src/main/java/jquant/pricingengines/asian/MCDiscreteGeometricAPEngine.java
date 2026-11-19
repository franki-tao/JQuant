package jquant.pricingengines.asian;

import jquant.EuropeanExercise;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

/**
 * Monte Carlo pricing engine for discrete geometric average price Asian
 * options.
 *
 * <p>Port of {@code MCDiscreteGeometricAPEngine} from
 * {@code ql/pricingengines/asian/mc_discr_geom_av_price.hpp}.
 */
public class MCDiscreteGeometricAPEngine
        extends MCDiscreteAveragingAsianEngineBase {

    public MCDiscreteGeometricAPEngine(
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
                NULL_SIZE, NULL_SIZE, false, rngType);
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

        return new GeometricAPOPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().getValue()
                        .discount(exercise.lastDate(), false),
                arguments_.runningAccumulator,
                arguments_.pastFixings);
    }
}
