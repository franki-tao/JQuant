package jquant.pricingengines.asian;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.HestonProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Factory for {@link MCDiscreteGeometricAPHestonEngine}.
 *
 * <p>Port of {@code MakeMCDiscreteGeometricAPHestonEngine}. The RNG template
 * parameter is represented by {@link RngType}.
 */
public class MakeMCDiscreteGeometricAPHestonEngine {
    private final HestonProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCDiscreteGeometricAPHestonEngine(HestonProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDiscreteGeometricAPHestonEngine(HestonProcess process,
                                                 RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withAbsoluteTolerance(
            double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withAntitheticVariate(
            boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDiscreteGeometricAPHestonEngine withSteps(int steps) {
        QL_REQUIRE(stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps per year already set");
        steps_ = steps;
        return this;
    }

    public MakeMCDiscreteGeometricAPHestonEngine withStepsPerYear(int steps) {
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE,
                "number of steps already set");
        stepsPerYear_ = steps;
        return this;
    }

    public MCDiscreteGeometricAPHestonEngine build() {
        return new MCDiscreteGeometricAPHestonEngine(process_,
                antithetic_, samples_, tolerance_, maxSamples_, seed_,
                steps_, stepsPerYear_, rngType_);
    }
}
