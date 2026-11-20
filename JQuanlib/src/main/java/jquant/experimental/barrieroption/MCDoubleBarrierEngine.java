package jquant.experimental.barrieroption;

import jquant.DoubleBarrierOption;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.PathGenerator;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.McSimulation;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for double-barrier options using Monte Carlo simulation.
 *
 * <p>Java port of {@code MCDoubleBarrierEngine<RNG,S>} from
 * {@code ql/experimental/barrieroption/mcdoublebarrierengine.{hpp,cpp}}. The
 * random-number generator policy is selected with {@link RngType}; Java has
 * no multiple inheritance, so the {@link McSimulation} behaviour is
 * implemented by an inner delegate.
 */
public class MCDoubleBarrierEngine extends DoubleBarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final int timeSteps_;
    private final int timeStepsPerYear_;
    private final int requiredSamples_;
    private final int maxSamples_;
    private final double requiredTolerance_;
    private final boolean brownianBridge_;
    private final boolean antitheticVariate_;
    private final long seed_;
    private final RngType rngType_;
    private final Simulation simulation_;

    public MCDoubleBarrierEngine(GeneralizedBlackScholesProcess process,
                                 int timeSteps,
                                 int timeStepsPerYear,
                                 boolean brownianBridge,
                                 boolean antitheticVariate,
                                 int requiredSamples,
                                 double requiredTolerance,
                                 int maxSamples,
                                 long seed,
                                 RngType rngType) {
        process_ = process;
        timeSteps_ = timeSteps;
        timeStepsPerYear_ = timeStepsPerYear;
        requiredSamples_ = requiredSamples;
        maxSamples_ = maxSamples;
        requiredTolerance_ = requiredTolerance;
        brownianBridge_ = brownianBridge;
        antitheticVariate_ = antitheticVariate;
        seed_ = seed;
        rngType_ = rngType;
        simulation_ = new Simulation(antitheticVariate);

        QL_REQUIRE(timeSteps != MathUtils.NULL_SIZE
                        || timeStepsPerYear != MathUtils.NULL_SIZE,
                "no time steps provided");
        QL_REQUIRE(timeSteps == MathUtils.NULL_SIZE
                        || timeStepsPerYear == MathUtils.NULL_SIZE,
                "both time steps and time steps per year were provided");
        QL_REQUIRE(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL_REQUIRE(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, " + timeStepsPerYear
                        + " not allowed");
        registerWith(process_);
    }

    @Override
    public void calculate() {
        double spot = process_.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(spot), "barrier touched");
        simulation_.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        results_.value = simulation_.sampleAccumulator().mean();
        if (rngType_.allowsErrorEstimate())
            results_.errorEstimate =
                    simulation_.sampleAccumulator().errorEstimate();
    }

    /** McSimulation delegate (Java has no multiple inheritance). */
    private class Simulation extends McSimulation {
        Simulation(boolean antitheticVariate) {
            super(antitheticVariate, false);
        }

        @Override
        protected TimeGrid timeGrid() {
            double residualTime = process_.time(arguments_.exercise.lastDate());
            if (timeSteps_ != MathUtils.NULL_SIZE) {
                return new TimeGrid(residualTime, timeSteps_);
            } else if (timeStepsPerYear_ != MathUtils.NULL_SIZE) {
                int steps = (int) (timeStepsPerYear_ * residualTime);
                return new TimeGrid(residualTime, Math.max(steps, 1));
            } else {
                QL_FAIL("time steps not specified");
                return null;
            }
        }

        @Override
        protected PathGenerator pathGenerator() {
            TimeGrid grid = timeGrid();
            InverseCumulativeRsg gen =
                    rngType_.makeSequenceGenerator(grid.size() - 1, seed_);
            return new PathGenerator(process_, grid, gen, brownianBridge_);
        }

        @Override
        protected PathPricer pathPricer() {
            QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                    "non-plain payoff given");
            PlainVanillaPayoff payoff =
                    (PlainVanillaPayoff) arguments_.payoff;

            TimeGrid grid = timeGrid();
            List<Double> discounts = new ArrayList<>(grid.size());
            for (int i = 0; i < grid.size(); i++)
                discounts.add(process_.riskFreeRate().getValue()
                        .discount(grid.get(i)));

            return new DoubleBarrierPathPricer(
                    arguments_.barrierType,
                    arguments_.barrier_lo,
                    arguments_.barrier_hi,
                    arguments_.rebate,
                    payoff.optionType(),
                    payoff.strike(),
                    discounts);
        }
    }
}
