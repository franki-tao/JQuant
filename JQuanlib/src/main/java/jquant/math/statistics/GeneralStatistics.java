package jquant.math.statistics;

import jquant.math.*;
import jquant.math.statistics.impl.Stat;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ! Statistics tool
 * ! This class accumulates a set of data and returns their
 * statistics (e.g: mean, variance, skewness, kurtosis,
 * error estimation, percentile, etc.) based on the empirical
 * distribution (no gaussian assumption)
 * <p>
 * It doesn't suffer the numerical instability problem of
 * IncrementalStatistics. The downside is that it stores all
 * samples, thus increasing the memory requirements.
 */
public class GeneralStatistics extends Stat {
    private List<Point<Double, Double>> samples_;
    private boolean sorted_;

    public GeneralStatistics() {
        reset();
    }

    //! \name Inspectors
    //@{
    //! number of samples collected
    public int samples() {
        return samples_.size();
    }

    //! collected data
    public final List<Point<Double, Double>> data() {
        return samples_;
    }

    //! sum of data weights
    public double weightSum() {
        double result = 0.0;
        for (Point<Double, Double> sample : samples_) {
            result += sample.getSecond();
        }
        return result;
    }

    /*! returns the mean, defined as
        \f[ \langle x \rangle = \frac{\sum w_i x_i}{\sum w_i}. \f]
    */
    public double mean() {
        int N = samples();
        QL_REQUIRE(N != 0, "empty sample set");
        return expectationValue(x -> x).getFirst();
    }

    /*! returns the variance, defined as
        \f[ \sigma^2 = \frac{N}{N-1} \left\langle \left(
            x-\langle x \rangle \right)^2 \right\rangle. \f]
    */
    public double variance() {
        int N = samples();
        QL_REQUIRE(N > 1,
                "sample number <=1, unsufficient");
        // Subtract the mean and square. Repeat on the whole range.
        // Hopefully, the whole thing will be inlined in a single loop.
        double m = mean();
        double s2 = expectationValue(x -> {
            double d = x - m;
            return d * d;
        }).getFirst();
        return s2 * N / (N - 1.0);
    }

    /*! returns the standard deviation \f$ \sigma \f$, defined as the
        square root of the variance.
    */
    public double standardDeviation() {
        return FastMath.sqrt(variance());
    }

    /*! returns the error estimate on the mean value, defined as
        \f$ \epsilon = \sigma/\sqrt{N}. \f$
    */
    public double errorEstimate() {
        return FastMath.sqrt(variance() / samples());
    }

    /*! returns the skewness, defined as
        \f[ \frac{N^2}{(N-1)(N-2)} \frac{\left\langle \left(
            x-\langle x \rangle \right)^3 \right\rangle}{\sigma^3}. \f]
        The above evaluates to 0 for a Gaussian distribution.
    */
    public double skewness() {
        int N = samples();
        QL_REQUIRE(N > 2,
                "sample number <=2, unsufficient");
        double m = mean();
        double X = expectationValue(x -> {
            double d = x - m;
            return d * d * d;
        }).getFirst();
        double sigma = standardDeviation();

        return (X / (sigma * sigma * sigma)) * (N / (N - 1.0)) * (N / (N - 2.0));
    }

    /*! returns the excess kurtosis, defined as
        \f[ \frac{N^2(N+1)}{(N-1)(N-2)(N-3)}
            \frac{\left\langle \left(x-\langle x \rangle \right)^4
            \right\rangle}{\sigma^4} - \frac{3(N-1)^2}{(N-2)(N-3)}. \f]
        The above evaluates to 0 for a Gaussian distribution.
    */
    public double kurtosis() {
        int N = samples();
        QL_REQUIRE(N > 3,
                "sample number <=3, unsufficient");

        double m = mean();
        double X = expectationValue(x -> {
            double d = x - m;
            double d2 = d * d;
            return d2 * d2;
        }).getFirst();
        double sigma2 = variance();

        double c1 = (N / (N - 1.0)) * (N / (N - 2.0)) * ((N + 1.0) / (N - 3.0));
        double c2 = 3.0 * ((N - 1.0) / (N - 2.0)) * ((N - 1.0) / (N - 3.0));

        return c1 * (X / (sigma2 * sigma2)) - c2;
    }

    /*! returns the minimum sample value */
    public double min() {
        QL_REQUIRE(samples() > 0, "empty sample set");
        List<Double> fis = new ArrayList<>();
        for (Point<Double, Double> sample : samples_) {
            fis.add(sample.getFirst());
        }
        return Collections.min(fis);
    }

    /*! returns the maximum sample value */
    public double max() {
        QL_REQUIRE(samples() > 0, "empty sample set");
        List<Double> fis = new ArrayList<>();
        for (Point<Double, Double> sample : samples_) {
            fis.add(sample.getFirst());
        }
        return Collections.max(fis);
    }

    /*! Expectation value of a function \f$ f \f$ on a given
        range \f$ \mathcal{R} \f$, i.e.,
        \f[ \mathrm{E}\left[f \;|\; \mathcal{R}\right] =
            \frac{\sum_{x_i \in \mathcal{R}} f(x_i) w_i}{
                  \sum_{x_i \in \mathcal{R}} w_i}. \f]
        The range is passed as a boolean function returning
        <tt>true</tt> if the argument belongs to the range
        or <tt>false</tt> otherwise.

        The function returns a pair made of the result and
        the number of observations in the given range.
    */
    public Point<Double, Integer> expectationValue(final Function f, final Predicate inRange) {
        double num = 0.0, den = 0.0;
        int N = 0;
        for (Point<Double, Double> sample : samples_) {
            double x = sample.getFirst();
            double w = sample.getSecond();
            if (inRange.value(x)) {
                num += f.value(x) * w;
                den += w;
                N += 1;
            }
        }
        if (N == 0)
            return new Point<>(Double.NaN, 0);
        else
            return new Point<>(num / den, N);
    }

    /*! Expectation value of a function \f$ f \f$ over the whole
        set of samples; equivalent to passing the other overload
        a range function always returning <tt>true</tt>.
    */
    public Point<Double, Integer> expectationValue(final Function f) {
        return expectationValue(f, x -> true);
    }

    /*! \f$ y \f$-th percentile, defined as the value \f$ \bar{x} \f$
        such that
        \f[ y = \frac{\sum_{x_i < \bar{x}} w_i}{
                      \sum_i w_i} \f]

        \pre \f$ y \f$ must be in the range \f$ (0-1]. \f$
    */
    public double percentile(double percent) {
        QL_REQUIRE(percent > 0.0 && percent <= 1.0,
                "percentile (" + percent + ") must be in (0.0, 1.0]");

        double sampleWeight = weightSum();
        QL_REQUIRE(sampleWeight > 0.0,
                "empty sample set");

        sort();

        int k, l;
        k = 0;
        l = samples_.size() - 1;
        /* the sum of weight is non null, therefore there's
           at least one sample */
        double integral = samples_.get(k).getSecond(), target = percent * sampleWeight;
        while (integral < target && k != l) {
            ++k;
            integral += samples_.get(k).getSecond();
        }
        return samples_.get(k).getFirst();
    }

    /*! \f$ y \f$-th top percentile, defined as the value
        \f$ \bar{x} \f$ such that
        \f[ y = \frac{\sum_{x_i > \bar{x}} w_i}{
                      \sum_i w_i} \f]

        \pre \f$ y \f$ must be in the range \f$ (0-1]. \f$
    */
    public double topPercentile(double percent) {
        QL_REQUIRE(percent > 0.0 && percent <= 1.0,
                "percentile (" + percent + ") must be in (0.0, 1.0]");

        double sampleWeight = weightSum();
        QL_REQUIRE(sampleWeight > 0.0,
                "empty sample set");

        sort();

        int k, l;
        k = samples_.size() - 1;
        l = 0;
        /* the sum of weight is non null, therefore there's
           at least one sample */
        double integral = samples_.get(k).getSecond(), target = percent * sampleWeight;
        while (integral < target && k != l) {
            --k;
            integral += samples_.get(k).getSecond();
        }
        return samples_.get(k).getFirst();
    }
    //@}

    //! \name Modifiers
    //@{
    //! adds a datum to the set, possibly with a weight
    public void add(double value, double weight) {
        QL_REQUIRE(weight >= 0.0, "negative weight not allowed");
        samples_.add(new Point<>(value, weight));
        sorted_ = false;
    }

    //! adds a sequence of data to the set, with default weight
    public void addSequence(List<Double> values) {
        for (Double d : values) {
            add(d, 1);
        }
    }

    //! adds a sequence of data to the set, each with its weight
    public void addSequence(List<Double> values, List<Double> weights) {
        for (int i = 0; i < values.size(); i++) {
            add(values.get(i), weights.get(i));
        }
    }

    //! resets the data to a null set
    public void reset() {
        samples_ = new ArrayList<>();
        sorted_ = true;
    }

    //! informs the internal storage of a planned increase in size
    public void reserve(int n) {
        samples_ = CommonUtil.ArrayInit(n);
    }

    //! sort the data set in increasing order
    public void sort() {
        if (!sorted_) {
            this.samples_.sort(new PointComparator());
            sorted_ = true;
        }
    }
}
