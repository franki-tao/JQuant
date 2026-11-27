package jquant.pricingengines.vanilla;

import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo Heston-model engine for European options.
 *
 * <p>Port of QuantLib {@code MCEuropeanHestonEngine} from
 * {@code ql/pricingengines/vanilla/mceuropeanhestonengine.hpp}.
 * Java has no template parameters, so the official
 * {@code MCEuropeanHestonEngine<RNG, S, P>} is represented by an
 * {@link RngType} constructor argument and a {@link HestonProcess};
 * the statistics type is always the port's {@code IncrementalStatistics}.
 * The official {@code MCVanillaEngine<MultiVariate,...>::pathPricer()}
 * returns a {@code PathPricer<MultiPath>}, which maps to
 * {@link MultiPathPricer#price} in this port, so the override is
 * {@link #multiPathPricer()}.
 */
public class MCEuropeanHestonEngine extends McVanillaEngine {

    public MCEuropeanHestonEngine(HestonProcess process,
                                  int timeSteps,
                                  int timeStepsPerYear,
                                  boolean antitheticVariate,
                                  int requiredSamples,
                                  double requiredTolerance,
                                  int maxSamples,
                                  long seed,
                                  RngType rngType) {
        super(process,
                timeSteps,
                timeStepsPerYear,
                false,
                antitheticVariate,
                false,
                requiredSamples,
                requiredTolerance,
                maxSamples,
                seed,
                rngType);
    }

    @Override
    protected MultiPathPricer multiPathPricer() {
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        QL_REQUIRE(process_ instanceof HestonProcess,
                "Heston like process required");
        HestonProcess process = (HestonProcess) process_;

        return new EuropeanHestonPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().getValue()
                        .discount(timeGrid().back(), false));
    }
}
