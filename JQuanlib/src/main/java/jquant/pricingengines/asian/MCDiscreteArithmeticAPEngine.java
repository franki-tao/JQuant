package jquant.pricingengines.asian;

import jquant.EuropeanExercise;
import jquant.PricingEngine;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

/**
 * Monte Carlo pricing engine for discrete arithmetic average price Asian
 * options.
 *
 * <p>It uses the analytic discrete geometric average-price engine and a
 * geometric path pricer for control variation.
 * Port of {@code MCDiscreteArithmeticAPEngine} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_price.hpp}.
 */
public class MCDiscreteArithmeticAPEngine
        extends MCDiscreteAveragingAsianEngineBase {

    public MCDiscreteArithmeticAPEngine(
            GeneralizedBlackScholesProcess process,
            boolean brownianBridge,
            boolean antitheticVariate,
            boolean controlVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            RngType rngType) {
        super(process, brownianBridge, antitheticVariate, controlVariate,
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

        return new ArithmeticAPOPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().getValue()
                        .discount(exercise.lastDate(), false),
                arguments_.runningAccumulator,
                arguments_.pastFixings);
    }

    @Override
    protected PathPricer controlPathPricer() {
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        QL_REQUIRE(arguments_.exercise instanceof EuropeanExercise,
                "wrong exercise given");

        QL_REQUIRE(process_ instanceof GeneralizedBlackScholesProcess,
                "Black-Scholes process required");
        final GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;

        // for seasoned option the geometric strike might be rescaled
        // to obtain an equivalent arithmetic strike.
        // Any change applied here MUST be applied to the analytic engine too
        return new GeometricAPOPathPricer(
                payoff.optionType(),
                payoff.strike(),
                process.riskFreeRate().getValue()
                        .discount(timeGrid().back(), false));
    }

    @Override
    protected PricingEngine controlPricingEngine() {
        QL_REQUIRE(process_ instanceof GeneralizedBlackScholesProcess,
                "Black-Scholes process required");
        final GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;
        return new AnalyticDiscreteGeometricAveragePriceAsianEngine(process);
    }
}
