package jquant.methods.montecarlo;

import jquant.math.statistics.IncrementalStatistics;

/**
 * General-purpose Monte Carlo model for single-variate path samples.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/montecarlomodel.hpp}.
 * The control-variate facilities are not needed by the European engine
 * and are omitted; the antithetic-variate handling is preserved.
 */
public class MonteCarloModel {
    private final PathGenerator pathGenerator_;
    private final PathPricer pathPricer_;
    private final IncrementalStatistics sampleAccumulator_;
    private final boolean isAntitheticVariate_;

    public MonteCarloModel(PathGenerator pathGenerator,
                           PathPricer pathPricer,
                           IncrementalStatistics sampleAccumulator,
                           boolean antitheticVariate) {
        pathGenerator_ = pathGenerator;
        pathPricer_ = pathPricer;
        sampleAccumulator_ = sampleAccumulator;
        isAntitheticVariate_ = antitheticVariate;
    }

    public void addSamples(int samples) {
        for (int j = 1; j <= samples; j++) {
            SamplePath path = pathGenerator_.next();
            double price = pathPricer_.price(path.value);

            if (isAntitheticVariate_) {
                SamplePath atPath = pathGenerator_.antithetic();
                double price2 = pathPricer_.price(atPath.value);
                sampleAccumulator_.add((price + price2) / 2.0, path.weight);
            } else {
                sampleAccumulator_.add(price, path.weight);
            }
        }
    }

    public IncrementalStatistics sampleAccumulator() {
        return sampleAccumulator_;
    }
}
