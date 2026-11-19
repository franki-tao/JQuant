package jquant.methods.montecarlo;

import jquant.math.statistics.IncrementalStatistics;

/**
 * General-purpose Monte Carlo model for path samples.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/montecarlomodel.hpp}.
 * Both control-variate and antithetic-variate handling are preserved, for
 * single paths and for {@link MultiPath}s.
 */
public class MonteCarloModel {
    private final PathGenerator pathGenerator_;
    private final PathPricer pathPricer_;
    private final MultiPathGenerator multiPathGenerator_;
    private final MultiPathPricer multiPathPricer_;
    private final IncrementalStatistics sampleAccumulator_;
    private final boolean isAntitheticVariate_;
    private final PathPricer cvPathPricer_;
    private final MultiPathPricer cvMultiPathPricer_;
    private final double cvOptionValue_;
    private final boolean isControlVariate_;
    private final PathGenerator cvPathGenerator_;
    private final MultiPathGenerator cvMultiPathGenerator_;

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
        multiPathGenerator_ = null;
        multiPathPricer_ = null;
        sampleAccumulator_ = sampleAccumulator;
        isAntitheticVariate_ = antitheticVariate;
        cvPathPricer_ = cvPathPricer;
        cvMultiPathPricer_ = null;
        cvOptionValue_ = cvOptionValue;
        isControlVariate_ = cvPathPricer != null;
        cvPathGenerator_ = cvPathGenerator;
        cvMultiPathGenerator_ = null;
    }

    public MonteCarloModel(MultiPathGenerator pathGenerator,
                           MultiPathPricer pathPricer,
                           IncrementalStatistics sampleAccumulator,
                           boolean antitheticVariate) {
        this(pathGenerator, pathPricer, sampleAccumulator, antitheticVariate,
                null, 0.0, null);
    }

    public MonteCarloModel(MultiPathGenerator pathGenerator,
                           MultiPathPricer pathPricer,
                           IncrementalStatistics sampleAccumulator,
                           boolean antitheticVariate,
                           MultiPathPricer cvPathPricer,
                           double cvOptionValue,
                           MultiPathGenerator cvPathGenerator) {
        pathGenerator_ = null;
        pathPricer_ = null;
        multiPathGenerator_ = pathGenerator;
        multiPathPricer_ = pathPricer;
        sampleAccumulator_ = sampleAccumulator;
        isAntitheticVariate_ = antitheticVariate;
        cvPathPricer_ = null;
        cvMultiPathPricer_ = cvPathPricer;
        cvOptionValue_ = cvOptionValue;
        isControlVariate_ = cvPathPricer != null;
        cvPathGenerator_ = null;
        cvMultiPathGenerator_ = cvPathGenerator;
    }

    public void addSamples(int samples) {
        if (multiPathGenerator_ != null) {
            addMultiSamples(samples);
            return;
        }

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

    private void addMultiSamples(int samples) {
        for (int j = 1; j <= samples; j++) {
            SampleMultiPath path = multiPathGenerator_.next();
            double price = multiPathPricer_.price(path.value);

            if (isControlVariate_) {
                if (cvMultiPathGenerator_ == null) {
                    price += cvOptionValue_
                            - cvMultiPathPricer_.price(path.value);
                } else {
                    SampleMultiPath cvPath = cvMultiPathGenerator_.next();
                    price += cvOptionValue_
                            - cvMultiPathPricer_.price(cvPath.value);
                }
            }

            if (isAntitheticVariate_) {
                SampleMultiPath atPath = multiPathGenerator_.antithetic();
                double price2 = multiPathPricer_.price(atPath.value);
                if (isControlVariate_) {
                    if (cvMultiPathGenerator_ == null) {
                        price2 += cvOptionValue_
                                - cvMultiPathPricer_.price(atPath.value);
                    } else {
                        SampleMultiPath cvPath =
                                cvMultiPathGenerator_.antithetic();
                        price2 += cvOptionValue_
                                - cvMultiPathPricer_.price(cvPath.value);
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
