package jquant.pricingengines.vanilla;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.HestonProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo Heston European engine factory.
 *
 * <p>Port of QuantLib {@code MakeMCEuropeanHestonEngine} from
 * {@code ql/pricingengines/vanilla/mceuropeanhestonengine.hpp}. Java has
 * no template parameters: the official {@code RNG} template argument
 * ({@code PseudoRandom} or {@code LowDiscrepancy}) is represented by
 * {@link RngType}, defaulting to {@link RngType#PSEUDO_RANDOM} the way the
 * official template defaults to {@code PseudoRandom}. The official
 * conversion operator maps to {@link #build()}, as in
 * {@link MakeMCEuropeanEngine}.
 */
public class MakeMCEuropeanHestonEngine {
    private final HestonProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCEuropeanHestonEngine(HestonProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCEuropeanHestonEngine(HestonProcess process,
                                      RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCEuropeanHestonEngine withSteps(int steps) {
        QL_REQUIRE(stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps per year already set");
        steps_ = steps;
        return this;
    }

    public MakeMCEuropeanHestonEngine withStepsPerYear(int steps) {
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE,
                "number of steps already set");
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEuropeanHestonEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAbsoluteTolerance(double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCEuropeanHestonEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCEuropeanHestonEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAntitheticVariate(boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCEuropeanHestonEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MCEuropeanHestonEngine build() {
        QL_REQUIRE(steps_ != MathUtils.NULL_SIZE
                        || stepsPerYear_ != MathUtils.NULL_SIZE,
                "number of steps not given");
        return new MCEuropeanHestonEngine(process_,
                steps_,
                stepsPerYear_,
                antithetic_,
                samples_,
                tolerance_,
                maxSamples_,
                seed_,
                rngType_);
    }
}
