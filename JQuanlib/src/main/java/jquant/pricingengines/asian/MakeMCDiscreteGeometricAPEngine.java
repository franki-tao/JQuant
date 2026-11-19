package jquant.pricingengines.asian;

import jquant.math.MathUtils;
import jquant.math.randomnumbers.RngType;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo discrete geometric average-price Asian engine factory.
 *
 * <p>Port of {@code MakeMCDiscreteGeometricAPEngine} from
 * {@code ql/pricingengines/asian/mc_discr_geom_av_price.hpp}. The RNG
 * template parameter is represented by {@link RngType}.
 */
public class MakeMCDiscreteGeometricAPEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final RngType rngType_;
    private boolean antithetic_ = false;
    private int samples_ = MathUtils.NULL_SIZE;
    private int maxSamples_ = MathUtils.NULL_SIZE;
    private double tolerance_ = MathUtils.NULL_REAL;
    private boolean brownianBridge_ = true;
    private long seed_ = 0;

    public MakeMCDiscreteGeometricAPEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, RngType.PSEUDO_RANDOM);
    }

    public MakeMCDiscreteGeometricAPEngine(
            GeneralizedBlackScholesProcess process, RngType rngType) {
        process_ = process;
        rngType_ = rngType;
    }

    public MakeMCDiscreteGeometricAPEngine withBrownianBridge(
            boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCDiscreteGeometricAPEngine withSamples(int samples) {
        QL_REQUIRE(tolerance_ == MathUtils.NULL_REAL,
                "tolerance already set");
        samples_ = samples;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withAbsoluteTolerance(
            double tolerance) {
        QL_REQUIRE(samples_ == MathUtils.NULL_SIZE,
                "number of samples already set");
        QL_REQUIRE(rngType_.allowsErrorEstimate(),
                "chosen random generator policy does not allow an error estimate");
        tolerance_ = tolerance;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withMaxSamples(int samples) {
        maxSamples_ = samples;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withSeed(long seed) {
        seed_ = seed;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withAntitheticVariate(
            boolean antithetic) {
        antithetic_ = antithetic;
        return this;
    }

    public MakeMCDiscreteGeometricAPEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MCDiscreteGeometricAPEngine build() {
        return new MCDiscreteGeometricAPEngine(process_, brownianBridge_,
                antithetic_, samples_, tolerance_, maxSamples_, seed_,
                rngType_);
    }
}
