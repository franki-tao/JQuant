package jquant.pricingengines;

import jquant.PricingEngine;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.statistics.IncrementalStatistics;
import jquant.methods.montecarlo.MonteCarloModel;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.methods.montecarlo.PathGenerator;
import jquant.methods.montecarlo.PathPricer;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Framework for single-variate Monte Carlo pricing engines.
 *
 * <p>Port of QuantLib {@code ql/pricingengines/mcsimulation.hpp}.
 */
public abstract class McSimulation {
    protected McSimulation(boolean antitheticVariate, boolean controlVariate) {
        antitheticVariate_ = antitheticVariate;
        controlVariate_ = controlVariate;
    }

    protected PathPricer pathPricer() {
        return null;
    }

    protected PathGenerator pathGenerator() {
        return null;
    }

    protected MultiPathPricer multiPathPricer() {
        return null;
    }

    protected MultiPathGenerator multiPathGenerator() {
        return null;
    }

    protected abstract TimeGrid timeGrid();

    protected PathPricer controlPathPricer() {
        return null;
    }

    protected MultiPathPricer controlMultiPathPricer() {
        return null;
    }

    protected PathGenerator controlPathGenerator() {
        return null;
    }

    protected MultiPathGenerator controlMultiPathGenerator() {
        return null;
    }

    protected PricingEngine controlPricingEngine() {
        return null;
    }

    protected double controlVariateValue() {
        return MathUtils.NULL_REAL;
    }

    protected boolean antitheticVariate_;
    protected boolean controlVariate_;
    protected MonteCarloModel mcModel_;

    /**
     * Add samples until the required absolute tolerance is reached.
     */
    public double value(double tolerance, int maxSamples, int minSamples) {
        int sampleNumber = mcModel_.sampleAccumulator().samples();
        if (sampleNumber < minSamples) {
            mcModel_.addSamples(minSamples - sampleNumber);
            sampleNumber = mcModel_.sampleAccumulator().samples();
        }

        int nextBatch;
        double order;
        double error = mcModel_.sampleAccumulator().errorEstimate();
        while (maxError(error) > tolerance) {
            QL_REQUIRE(sampleNumber < maxSamples,
                    "max number of samples (" + maxSamples
                            + ") reached, while error (" + error
                            + ") is still above tolerance (" + tolerance + ")");

            // conservative estimate of how many samples are needed
            order = maxError(error * error) / tolerance / tolerance;
            nextBatch = (int) Math.max((double) sampleNumber * order * 0.8
                            - (double) sampleNumber,
                    (double) minSamples);

            // do not exceed maxSamples
            nextBatch = Math.min(nextBatch, maxSamples - sampleNumber);
            sampleNumber += nextBatch;
            mcModel_.addSamples(nextBatch);
            error = mcModel_.sampleAccumulator().errorEstimate();
        }

        return mcModel_.sampleAccumulator().mean();
    }

    public double value(double tolerance) {
        return value(tolerance, Integer.MAX_VALUE, 1023);
    }

    /**
     * Simulate a fixed number of samples.
     */
    public double valueWithSamples(int samples) {
        int sampleNumber = mcModel_.sampleAccumulator().samples();

        QL_REQUIRE(samples >= sampleNumber,
                "number of already simulated samples (" + sampleNumber
                        + ") greater than requested samples (" + samples + ")");

        mcModel_.addSamples(samples - sampleNumber);

        return mcModel_.sampleAccumulator().mean();
    }

    /**
     * Error estimated using the samples simulated so far.
     */
    public double errorEstimate() {
        return mcModel_.sampleAccumulator().errorEstimate();
    }

    /**
     * Access to the sample accumulator for richer statistics.
     */
    public IncrementalStatistics sampleAccumulator() {
        return mcModel_.sampleAccumulator();
    }

    /**
     * Basic calculate method provided to inherited pricing engines.
     */
    public void calculate(double requiredTolerance, int requiredSamples, int maxSamples) {

        QL_REQUIRE(requiredTolerance != MathUtils.NULL_REAL
                        || requiredSamples != MathUtils.NULL_SIZE,
                "neither tolerance nor number of samples set");

        final MultiPathPricer multiPP = multiPathPricer();

        if (multiPP != null) {
            if (controlVariate_) {
                double controlVariateValue = controlVariateValue();
                QL_REQUIRE(controlVariateValue != MathUtils.NULL_REAL,
                        "engine does not provide control-variation price");

                MultiPathPricer controlPP = controlMultiPathPricer();
                QL_REQUIRE(controlPP != null,
                        "engine does not provide control-variation path pricer");

                mcModel_ = new MonteCarloModel(multiPathGenerator(),
                        multiPP, new IncrementalStatistics(),
                        antitheticVariate_, controlPP, controlVariateValue,
                        controlMultiPathGenerator());
            } else {
                mcModel_ = new MonteCarloModel(multiPathGenerator(),
                        multiPP, new IncrementalStatistics(),
                        antitheticVariate_);
            }
        } else if (controlVariate_) {
            double controlVariateValue = controlVariateValue();
            QL_REQUIRE(controlVariateValue != MathUtils.NULL_REAL,
                    "engine does not provide control-variation price");

            PathPricer controlPP = controlPathPricer();
            QL_REQUIRE(controlPP != null,
                    "engine does not provide control-variation path pricer");

            PathGenerator controlPG = controlPathGenerator();

            mcModel_ = new MonteCarloModel(pathGenerator(), pathPricer(),
                    new IncrementalStatistics(), antitheticVariate_,
                    controlPP, controlVariateValue, controlPG);
        } else {
            mcModel_ = new MonteCarloModel(pathGenerator(), pathPricer(),
                    new IncrementalStatistics(), antitheticVariate_);
        }

        if (requiredTolerance != jquant.math.MathUtils.NULL_REAL) {
            if (maxSamples != jquant.math.MathUtils.NULL_SIZE)
                value(requiredTolerance, maxSamples, 1023);
            else
                value(requiredTolerance);
        } else {
            valueWithSamples(requiredSamples);
        }
    }

    private static double maxError(double error) {
        return error;
    }
}
