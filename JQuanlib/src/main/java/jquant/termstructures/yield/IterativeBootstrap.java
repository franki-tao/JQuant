package jquant.termstructures.yield;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Universal piecewise-term-structure bootstrapper.
 *
 * <p>This is the {@code IterativeBootstrap} of
 * {@code ql/termstructures/iterativebootstrap.hpp}. In JQuant the pillar loop
 * itself lives in {@link PiecewiseYieldCurve#bootstrapPillars()}; an instance
 * of this class selects that path and carries the official constructor
 * parameters. A default-constructed {@code IterativeBootstrap} is equivalent
 * to the historical default construction of {@link PiecewiseYieldCurve}.
 *
 * <p>The optional parameters mirror QuantLib's defaults:
 * {@code accuracy = Null}, {@code minValue = Null}, {@code maxValue = Null},
 * {@code maxAttempts = 1}, {@code maxFactor = 2.0}, {@code minFactor = 2.0},
 * {@code dontThrow = false}, {@code dontThrowSteps = 10} and
 * {@code maxEvaluations = MAX_FUNCTION_EVALUATIONS}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/iterativebootstrap.hpp
 */
public class IterativeBootstrap {
    private final Double accuracy_;
    private final Double minValue_;
    private final Double maxValue_;
    private final int maxAttempts_;
    private final double maxFactor_;
    private final double minFactor_;
    private final boolean dontThrow_;
    private final int dontThrowSteps_;
    private final int maxEvaluations_;

    public IterativeBootstrap() {
        this(null, null, null, 1, 2.0, 2.0, false, 10,
                jquant.math.MathUtils.MAX_FUNCTION_EVALUATIONS);
    }

    public IterativeBootstrap(Double accuracy,
                              Double minValue,
                              Double maxValue,
                              int maxAttempts,
                              double maxFactor,
                              double minFactor,
                              boolean dontThrow,
                              int dontThrowSteps,
                              int maxEvaluations) {
        this.accuracy_ = accuracy;
        this.minValue_ = minValue;
        this.maxValue_ = maxValue;
        this.maxAttempts_ = maxAttempts;
        this.maxFactor_ = maxFactor;
        this.minFactor_ = minFactor;
        this.dontThrow_ = dontThrow;
        this.dontThrowSteps_ = dontThrowSteps;
        this.maxEvaluations_ = maxEvaluations;
        QL_REQUIRE(maxFactor_ >= 1.0,
                "Expected that maxFactor would be at least 1.0 but got " + maxFactor_);
        QL_REQUIRE(minFactor_ >= 1.0,
                "Expected that minFactor would be at least 1.0 but got " + minFactor_);
    }

    /**
     * Mirrors {@code IterativeBootstrap::setup}. Quote registration happens in
     * the {@link PiecewiseYieldCurve} constructor already, so this only checks
     * that there is at least one helper.
     */
    public void setup(PiecewiseYieldCurve ts) {
        QL_REQUIRE(ts.instruments_.size() > 0, "no bootstrap helpers given");
    }

    Double accuracy() {
        return accuracy_;
    }

    Double minValue() {
        return minValue_;
    }

    Double maxValue() {
        return maxValue_;
    }

    int maxAttempts() {
        return maxAttempts_;
    }

    double maxFactor() {
        return maxFactor_;
    }

    double minFactor() {
        return minFactor_;
    }

    boolean dontThrow() {
        return dontThrow_;
    }

    int dontThrowSteps() {
        return dontThrowSteps_;
    }

    int maxEvaluations() {
        return maxEvaluations_;
    }
}
