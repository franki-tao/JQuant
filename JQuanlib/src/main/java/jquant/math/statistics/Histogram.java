package jquant.math.statistics;

import jquant.math.CommonUtil;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.statistics.impl.StatiticsUtil.quantile;
import static jquant.math.statistics.impl.StatiticsUtil.unique;

/**
 * ! Histogram class
 * ! This class computes the histogram of a given data set.  The
 * caller can specify the number of bins, the breaks, or the
 * algorithm for determining these quantities in computing the
 * histogram.
 */
public class Histogram {
    public enum Algorithm {None, Sturges, FD, Scott}

    private List<Double> data_;
    private int bins_;
    private Algorithm algorithm_;
    private List<Double> breaks_;
    private List<Integer> counts_;
    private List<Double> frequency_;

    //! \name constructors
    //@{
    public Histogram() {
        algorithm_ = Algorithm.Scott;
    }

    public Histogram(List<Double> data, int breaks) {
        data_ = data;
        bins_ = breaks + 1;
        calculate();
    }

    public Histogram(List<Double> data, Algorithm algorithm) {
        data_ = data;
        bins_ = -1;
        algorithm_ = algorithm;
        calculate();
    }

    public Histogram(List<Double> data, List<Double> breaks) {
        data_ = data;
        breaks_ = breaks;
        bins_ = breaks.size() + 1;
        calculate();
    }

    //@}

    //! \name inspectors
    //@{
    public int bins() {
        return bins_;
    }

    public final List<Double> breaks() {
        return breaks_;
    }

    public Algorithm algorithm() {
        return algorithm_;
    }

    public boolean empty() {
        return bins_ == 0;
    }
    //@}

    //! \name results
    //@{
    public int counts(int i) {
        return counts_.get(i);
    }

    public double frequency(int i) {
        return frequency_.get(i);
    }
    //@}

    private void calculate() {
        QL_REQUIRE(!data_.isEmpty(), "no data given");

        double min = Collections.min(data_);  //*std::min_element(data_.begin(), data_.end());
        double max = Collections.max(data_);  //*std::max_element(data_.begin(), data_.end());

        // calculate number of bins if necessary
        if (bins_ == -1) {
            switch (algorithm_) {
                case Sturges: {
                    bins_ = (int) Math.ceil(Math.log(data_.size()) / Math.log(2.0) + 1);
                    break;
                }
                case FD: {
                    double r1 = quantile(data_, 0.25);
                    double r2 = quantile(data_, 0.75);
                    double h = 2.0 * (r2 - r1) * Math.pow((data_.size()), -1.0 / 3.0);
                    bins_ = (int) (Math.ceil((max - min) / h));
                    break;
                }
                case Scott: {
                    IncrementalStatistics summary = new IncrementalStatistics();
                    summary.addSequence(data_);
                    double variance = summary.variance();
                    double h = 3.5 * Math.sqrt(variance)
                            * Math.pow((data_.size()), -1.0 / 3.0);
                    bins_ = (int) (Math.ceil((max - min) / h));
                    break;
                }
                case None:
                    QL_FAIL("a bin-partition algorithm is required");
                default:
                    QL_FAIL("unknown bin-partition algorithm");
            }
            ;
            bins_ = Math.max(bins_, 1);
        }

        if (breaks_.isEmpty()) {
            // set breaks if not provided
            breaks_ = CommonUtil.ArrayInit(bins_ - 1);

            // ensure breaks_ evenly span over the range of data_
            // TODO: borrow the idea of pretty in R.
            double h = (max - min) / bins_;
            for (int i = 0; i < breaks_.size(); ++i) {
                breaks_.set(i, min + (i + 1) * h);
            }
        } else {
            // or ensure they're sorted if given
            Collections.sort(breaks_);
            unique(breaks_);
        }

        // finally, calculate counts and frequencies
        counts_ = CommonUtil.ArrayInit(bins_, 0);

        for (double p : data_) {
            boolean processed = false;
            for (int i = 0; i < breaks_.size(); ++i) {
                if (p < breaks_.get(i)) {
                    counts_.set(i, counts_.get(i) + 1);
                    processed = true;
                    break;
                }
            }
            if (!processed)
                counts_.set(bins_ - 1, counts_.get(bins_ - 1) + 1);
        }

        frequency_ = CommonUtil.ArrayInit(bins_);

        int totalCounts = data_.size();
        for (int i = 0; i < bins_; ++i)
            frequency_.set(i, (double) (counts_.get(i)) / totalCounts);
    }
}
