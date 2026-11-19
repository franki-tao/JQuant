package jquant.pricingengines.asian;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.HestonProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Factory for {@link MCDiscreteArithmeticAPHestonEngine}.
 *
 * <p>Port of {@code MakeMCDiscreteArithmeticAPHestonEngine}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCDiscreteArithmeticAPHestonEngine {
    private final HestonProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCDiscreteArithmeticAPHestonEngine(HestonProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDiscreteArithmeticAPHestonEngine(HestonProcess process,
                                                  RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withAbsoluteTolerance(
            double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withAntitheticVariate(
            boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withSteps(int steps) {
        QL_REQUIRE(stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps per year already set");
        steps_ = steps;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withStepsPerYear(int steps) {
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE,
                "number of steps already set");
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCDiscreteArithmeticAPHestonEngine withControlVariate(
            boolean controlVariate) {
        controlVariate_ = controlVariate;
        return this;
    }

    public MCDiscreteArithmeticAPHestonEngine build() {
        return new MCDiscreteArithmeticAPHestonEngine(process_,
                antithetic_, samples_, tolerance_, maxSamples_, seed_,
                steps_, stepsPerYear_, controlVariate_, rngType_);
    }
}
