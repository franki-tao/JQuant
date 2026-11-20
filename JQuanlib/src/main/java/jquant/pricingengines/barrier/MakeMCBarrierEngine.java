package jquant.pricingengines.barrier;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo barrier-option engine factory.
 *
 * <p>Port of QuantLib {@code MakeMCBarrierEngine} from
 * {@code ql/pricingengines/barrier/mcbarrierengine.hpp}. The RNG template
 * parameter is represented by {@link RngType}.
 */
public class MakeMCBarrierEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private boolean biased_ = false;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCBarrierEngine(GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCBarrierEngine(GeneralizedBlackScholesProcess process,
                               RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCBarrierEngine withSteps(int steps) {
        steps_ = steps;
        return this;
    }

    public MakeMCBarrierEngine withStepsPerYear(int steps) {
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCBarrierEngine withBrownianBridge(boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCBarrierEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCBarrierEngine withAntitheticVariate(boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCBarrierEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCBarrierEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCBarrierEngine withAbsoluteTolerance(double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCBarrierEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCBarrierEngine withBias(boolean biased) {
        biased_ = biased;
        return this;
    }

    public MakeMCBarrierEngine withBias() {
        return withBias(true);
    }

    public MakeMCBarrierEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MCBarrierEngine build() {
        QL_REQUIRE(steps_ != MathUtils.NULL_SIZE
                        || stepsPerYear_ != MathUtils.NULL_SIZE,
                "number of steps not given");
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE
                        || stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps overspecified");
        return new MCBarrierEngine(process_,
                steps_,
                stepsPerYear_,
                brownianBridge_,
                antithetic_,
                samples_,
                tolerance_,
                maxSamples_,
                biased_,
                seed_,
                rngType_);
    }
}
