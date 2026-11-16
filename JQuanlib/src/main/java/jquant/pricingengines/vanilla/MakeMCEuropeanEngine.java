package jquant.pricingengines.vanilla;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo European engine factory.
 *
 * <p>Port of QuantLib {@code MakeMCEuropeanEngine} from
 * {@code ql/pricingengines/vanilla/mceuropeanengine.hpp}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCEuropeanEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private boolean brownianBridge_ = false;
    private long seed_ = 0;

    public MakeMCEuropeanEngine(GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCEuropeanEngine(GeneralizedBlackScholesProcess process, RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCEuropeanEngine withSteps(int steps) {
        steps_ = steps;
        return this;
    }

    public MakeMCEuropeanEngine withStepsPerYear(int steps) {
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEuropeanEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCEuropeanEngine withAbsoluteTolerance(double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCEuropeanEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCEuropeanEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCEuropeanEngine withBrownianBridge(boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCEuropeanEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCEuropeanEngine withAntitheticVariate(boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCEuropeanEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MCEuropeanEngine build() {
        QL_REQUIRE(steps_ != MathUtils.NULL_SIZE
                        || stepsPerYear_ != MathUtils.NULL_SIZE,
                "number of steps not given");
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE
                        || stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps overspecified");
        return new MCEuropeanEngine(process_,
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
