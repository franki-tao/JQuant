package jquant.pricingengines.vanilla;

import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * European option pricing engine using Monte Carlo simulation.
 *
 * <p>Port of QuantLib {@code MCEuropeanEngine} from
 * {@code ql/pricingengines/vanilla/mceuropeanengine.hpp}.
 */
public class MCEuropeanEngine extends McVanillaEngine {

    public MCEuropeanEngine(GeneralizedBlackScholesProcess process,
                            int timeSteps,
                            int timeStepsPerYear,
                            boolean brownianBridge,
                            boolean antitheticVariate,
                            int requiredSamples,
                            double requiredTolerance,
                            int maxSamples,
                            long seed,
                            RngType rngType) {
        super(process,
                timeSteps,
                timeStepsPerYear,
                brownianBridge,
                antitheticVariate,
                false,
                requiredSamples,
                requiredTolerance,
                maxSamples,
                seed,
                rngType);
    }

    @Override
    protected PathPricer pathPricer() {
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        QL_REQUIRE(process_ instanceof GeneralizedBlackScholesProcess,
                "Black-Scholes process required");

        return new EuropeanPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process_.riskFreeRate().getValue().discount(timeGrid().back(), false));
    }
}
