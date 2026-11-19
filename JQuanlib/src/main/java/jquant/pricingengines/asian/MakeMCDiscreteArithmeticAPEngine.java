package jquant.pricingengines.asian;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo discrete arithmetic average-price Asian engine factory.
 *
 * <p>Port of {@code MakeMCDiscreteArithmeticAPEngine} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_price.hpp}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCDiscreteArithmeticAPEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private boolean brownianBridge_ = true;
    private long seed_ = 0;

    public MakeMCDiscreteArithmeticAPEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDiscreteArithmeticAPEngine(
            GeneralizedBlackScholesProcess process, RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDiscreteArithmeticAPEngine withBrownianBridge(
            boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDiscreteArithmeticAPEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withAbsoluteTolerance(
            double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withAntitheticVariate(
            boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCDiscreteArithmeticAPEngine withControlVariate(
            boolean controlVariate) {
        controlVariate_ = controlVariate;
        return this;
    }

    public MakeMCDiscreteArithmeticAPEngine withControlVariate() {
        return withControlVariate(true);
    }

    public MCDiscreteArithmeticAPEngine build() {
        return new MCDiscreteArithmeticAPEngine(process_, brownianBridge_,
                antithetic_, controlVariate_, samples_, tolerance_,
                maxSamples_, seed_, rngType_);
    }
}
