package jquant.math.statistics;

import jquant.math.statistics.impl.DownsideAccumulatorSet;
import jquant.math.statistics.impl.WeightedAccumulatorSet;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! Statistics tool based on incremental accumulation
 * ! It can accumulate a set of data and return statistics (e.g: mean,
 * variance, skewness, kurtosis, error estimation, etc.).
 * This class is a wrapper to the boost accumulator library.
 */
public class IncrementalStatistics {
    private WeightedAccumulatorSet acc_;
    private DownsideAccumulatorSet downsideAcc_;

    public IncrementalStatistics() {
        reset();
    }

    //! \name Inspectors
    //@{
    //! number of samples collected
    public int samples() {
        return acc_.getCount();
    }

    //! sum of data weights
    public double weightSum() {
        return acc_.getSumOfWeights();
    }

    /**
     * ! returns the mean, defined as
     * \f[ \langle x \rangle = \frac{\sum w_i x_i}{\sum w_i}. \f]
     */
    public double mean() {
        QL_REQUIRE(weightSum() > 0.0, "sampleWeight_= 0, unsufficient");
        return acc_.getWeightedMean();
    }

    /**
     * ! returns the variance, defined as
     * \f[ \frac{N}{N-1} \left\langle \left(
     * x-\langle x \rangle \right)^2 \right\rangle. \f]
     */
    public double variance() {
        QL_REQUIRE(weightSum() > 0.0, "sampleWeight_= 0, unsufficient");
        QL_REQUIRE(samples() > 1, "sample number <= 1, unsufficient");
        double n = samples();
        return n / (n - 1.0) * acc_.getWeightedVariance();
    }

    /**
     * ! returns the standard deviation \f$ \sigma \f$, defined as the
     * square root of the variance.
     */
    public double standardDeviation() {
        return Math.sqrt(variance());
    }

    /**
     * ! returns the error estimate \f$ \epsilon \f$, defined as the
     * square root of the ratio of the variance to the number of
     * samples.
     */
    public double errorEstimate() {
        return Math.sqrt(variance() / samples());
    }

    /**
     * ! returns the skewness, defined as
     * \f[ \frac{N^2}{(N-1)(N-2)} \frac{\left\langle \left(
     * x-\langle x \rangle \right)^3 \right\rangle}{\sigma^3}. \f]
     * The above evaluates to 0 for a Gaussian distribution.
     */
    public double skewness() {
        QL_REQUIRE(samples() > 2, "sample number <= 2, unsufficient");
        double n = samples();
        double r1 = n / (n - 2.0);
        double r2 = (n - 1.0) / (n - 2.0);
        return Math.sqrt(r1 * r2) * acc_.getWeightedSkewness();
    }

    /**
     * ! returns the excess kurtosis, defined as
     * \f[ \frac{N^2(N+1)}{(N-1)(N-2)(N-3)}
     * \frac{\left\langle \left(x-\langle x \rangle \right)^4
     * \right\rangle}{\sigma^4} - \frac{3(N-1)^2}{(N-2)(N-3)}. \f]
     * The above evaluates to 0 for a Gaussian distribution.
     */
    public double kurtosis() {
        QL_REQUIRE(samples() > 3,
                "sample number <= 3, unsufficient");
        acc_.getWeightedKurtosis();
        double n = samples();
        double r1 = (n - 1.0) / (n - 2.0);
        double r2 = (n + 1.0) / (n - 3.0);
        double r3 = (n - 1.0) / (n - 3.0);
        return ((3.0 + acc_.getWeightedKurtosis()) * r2 - 3.0 * r3) * r1;
    }

    /**
     * ! returns the minimum sample value
     */
    public double min() {
        QL_REQUIRE(samples() > 0, "empty sample set");
        return acc_.getMin();
    }

    /**
     * ! returns the maximum sample value
     */
    public double max() {
        QL_REQUIRE(samples() > 0, "empty sample set");
        return acc_.getMax();
    }

    //! number of negative samples collected
    public int downsideSamples() {
        return downsideAcc_.getCount();
    }

    //! sum of data weights for negative samples
    public double downsideWeightSum() {
        return downsideAcc_.getSumOfWeights();
    }

    /**
     * ! returns the downside variance, defined as
     * \f[ \frac{N}{N-1} \times \frac{ \sum_{i=1}^{N}
     * \theta \times x_i^{2}}{ \sum_{i=1}^{N} w_i} \f],
     * where \f$ \theta \f$ = 0 if x > 0 and
     * \f$ \theta \f$ =1 if x <0
     */
    public double downsideVariance() {
        QL_REQUIRE(downsideWeightSum() > 0.0, "sampleWeight_= 0, unsufficient");
        QL_REQUIRE(downsideSamples() > 1, "sample number <= 1, unsufficient");
        double n = downsideSamples();
        double r1 = n / (n - 1.0);
        return r1 * downsideAcc_.getWeightedSecondCentralMoment();
    }

    /**
     * ! returns the downside deviation, defined as the
     * square root of the downside variance.
     */
    public double downsideDeviation() {
        return Math.sqrt(downsideVariance());
    }

    //@}

    //! \name Modifiers
    //@{
    //! adds a datum to the set, possibly with a weight
    /*! \pre weight must be positive or null */
    public void add(double value, double valueWeight) {
        QL_REQUIRE(valueWeight >= 0.0, "negative weight (" + valueWeight
                + ") not allowed");
        acc_.addValue(value, valueWeight);
        if (value < 0.0) {
            downsideAcc_.addValue(value, valueWeight);
        }
    }
    //! adds a sequence of data to the set, with default weight
    public void addSequence(List<Double> arr, int begin, int end) {
        for (int i = begin; i < end; i++) {
            add(arr.get(i), 1.0);
        }
    }

    //! adds a sequence of data to the set, each with its weight
    /*! \pre weights must be positive or null */
    public void addSequence(List<Double> values, List<Double> weights) {
        for (int i = 0; i < values.size(); i++) {
            add(values.get(i), weights.get(i));
        }
    }

    //! resets the data to a null set
    public void reset() {
        acc_ = new WeightedAccumulatorSet();
        downsideAcc_ = new DownsideAccumulatorSet();
    }
}
