package jquant.methods.montecarlo;

import jquant.math.statistics.IncrementalStatistics;

/**
 * General-purpose Monte Carlo model for single-variate path samples.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/montecarlomodel.hpp}.
 * Both control-variate and antithetic-variate handling are preserved.
 */
public class MonteCarloModel {
    private final PathGenerator pathGenerator_;
    private final PathPricer pathPricer_;
    private final IncrementalStatistics sampleAccumulator_;
    private final boolean isAntitheticVariate_;
    private final PathPricer cvPathPricer_;
    private final double cvOptionValue_;
    private final boolean isControlVariate_;
    private final PathGenerator cvPathGenerator_;

    public MonteCarloModel(PathGenerator pathGenerator,
                           PathPricer pathPricer,
                           IncrementalStatistics sampleAccumulator,
                           boolean antitheticVariate) {
        this(pathGenerator, pathPricer, sampleAccumulator, antitheticVariate,
                null, 0.0, null);
    }

    public MonteCarloModel(PathGenerator pathGenerator,
                           PathPricer pathPricer,
                           IncrementalStatistics sampleAccumulator,
                           boolean antitheticVariate,
                           PathPricer cvPathPricer,
                           double cvOptionValue,
                           PathGenerator cvPathGenerator) {
        pathGenerator_ = pathGenerator;
        pathPricer_ = pathPricer;
        sampleAccumulator_ = sampleAccumulator;
        isAntitheticVariate_ = antitheticVariate;
        cvPathPricer_ = cvPathPricer;
        cvOptionValue_ = cvOptionValue;
        isControlVariate_ = cvPathPricer != null;
        cvPathGenerator_ = cvPathGenerator;
    }

    public void addSamples(int samples) {
        for (int j = 1; j <= samples; j++) {
            SamplePath path = pathGenerator_.next();
            double price = pathPricer_.price(path.value);

            if (isControlVariate_) {
                if (cvPathGenerator_ == null) {
                    price += cvOptionValue_ - cvPathPricer_.price(path.value);
                } else {
                    SamplePath cvPath = cvPathGenerator_.next();
                    price += cvOptionValue_ - cvPathPricer_.price(cvPath.value);
                }
            }

            if (isAntitheticVariate_) {
                SamplePath atPath = pathGenerator_.antithetic();
                double price2 = pathPricer_.price(atPath.value);
                if (isControlVariate_) {
                    if (cvPathGenerator_ == null) {
                        price2 += cvOptionValue_
                                - cvPathPricer_.price(atPath.value);
                    } else {
                        SamplePath cvPath = cvPathGenerator_.antithetic();
                        price2 += cvOptionValue_
                                - cvPathPricer_.price(cvPath.value);
                    }
                }
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
