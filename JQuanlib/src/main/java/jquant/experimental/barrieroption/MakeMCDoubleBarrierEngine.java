package jquant.experimental.barrieroption;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo double-barrier-option engine factory.
 *
 * <p>Port of QuantLib {@code MakeMCDoubleBarrierEngine<RNG,S>} from
 * {@code ql/experimental/barrieroption/mcdoublebarrierengine.hpp}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCDoubleBarrierEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCDoubleBarrierEngine(GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDoubleBarrierEngine(GeneralizedBlackScholesProcess process,
                                     RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDoubleBarrierEngine withSteps(int steps) {
        steps_ = steps;
        return this;
    }

    public MakeMCDoubleBarrierEngine withStepsPerYear(int steps) {
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCDoubleBarrierEngine withBrownianBridge(boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCDoubleBarrierEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDoubleBarrierEngine withAntitheticVariate(boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDoubleBarrierEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDoubleBarrierEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDoubleBarrierEngine withAbsoluteTolerance(double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDoubleBarrierEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDoubleBarrierEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MCDoubleBarrierEngine build() {
        QL_REQUIRE(steps_ != MathUtils.NULL_SIZE
                        || stepsPerYear_ != MathUtils.NULL_SIZE,
                "number of steps not given");
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE
                        || stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps overspecified");
        return new MCDoubleBarrierEngine(process_,
                steps_,
                stepsPerYear_,
                brownianBridge_,
                antithetic_,
                samples_,
                tolerance_,
                maxSamples_,
                seed_,
                rngType_);
    }
}
