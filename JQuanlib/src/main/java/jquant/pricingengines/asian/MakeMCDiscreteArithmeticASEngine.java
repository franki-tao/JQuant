package jquant.pricingengines.asian;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo discrete arithmetic average-strike Asian engine factory.
 *
 * <p>Port of {@code MakeMCDiscreteArithmeticASEngine} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_strike.hpp}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCDiscreteArithmeticASEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private boolean brownianBridge_ = true;
    private long seed_ = 0;

    public MakeMCDiscreteArithmeticASEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDiscreteArithmeticASEngine(
            GeneralizedBlackScholesProcess process, RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDiscreteArithmeticASEngine withBrownianBridge(
            boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDiscreteArithmeticASEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withAbsoluteTolerance(
            double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withAntitheticVariate(
            boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDiscreteArithmeticASEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MCDiscreteArithmeticASEngine build() {
        return new MCDiscreteArithmeticASEngine(process_, brownianBridge_,
                antithetic_, samples_, tolerance_, maxSamples_, seed_,
                rngType_);
    }
}
