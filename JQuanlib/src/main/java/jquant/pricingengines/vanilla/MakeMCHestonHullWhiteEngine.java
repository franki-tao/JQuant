package jquant.pricingengines.vanilla;

import jquant.PricingEngine;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.HybridHestonHullWhiteProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo Heston/Hull-White engine factory.
 *
 * <p>Port of {@code MakeMCHestonHullWhiteEngine} from
 * {@code ql/pricingengines/vanilla/mchestonhullwhiteengine.hpp}. The official
 * conversion operator maps to {@link #build()}. The official {@code RNG}
 * template argument defaults to {@code PseudoRandom} and is represented by
 * {@link RngType}.
 */
public class MakeMCHestonHullWhiteEngine {
    private final HybridHestonHullWhiteProcess process_;
    private final RngType rngType_;
    private int steps_ = MathUtils.NULL_SIZE;
    private int stepsPerYear_ = MathUtils.NULL_SIZE;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private boolean antithetic_ = false, controlVariate_ = false;
    private double tolerance_ = MathUtils.NULL_REAL;
    private long seed_ = 0;

    public MakeMCHestonHullWhiteEngine(
            HybridHestonHullWhiteProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCHestonHullWhiteEngine(
            HybridHestonHullWhiteProcess process, RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCHestonHullWhiteEngine withSteps(int steps) {
        steps_ = steps;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withStepsPerYear(int steps) {
        stepsPerYear_ = steps;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withAntitheticVariate(boolean b) {
        antithetic_ = b;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCHestonHullWhiteEngine withControlVariate(boolean b) {
        controlVariate_ = b;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withControlVariate() {
        return withControlVariate(true);
    }

    public MakeMCHestonHullWhiteEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withAbsoluteTolerance(double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy "
                        + "does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCHestonHullWhiteEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public PricingEngine build() {
        QL_REQUIRE(steps_ != MathUtils.NULL_SIZE
                        || stepsPerYear_ != MathUtils.NULL_SIZE,
                "number of steps not given");
        QL_REQUIRE(steps_ == MathUtils.NULL_SIZE
                        || stepsPerYear_ == MathUtils.NULL_SIZE,
                "number of steps overspecified");
        return new MCHestonHullWhiteEngine(process_,
                steps_,
                stepsPerYear_,
                antithetic_,
                controlVariate_,
                samples_,
                tolerance_,
                maxSamples_,
                seed_,
                rngType_);
    }
}
