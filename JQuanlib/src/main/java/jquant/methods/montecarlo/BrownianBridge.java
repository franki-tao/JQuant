package jquant.methods.montecarlo;

//! Builds Wiener process paths using Gaussian variates

import jquant.TimeGrid;
import jquant.math.CommonUtil;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * !This class generates normalized (i.e., unit-variance) paths as
 * sequences of variations. In order to obtain the actual path of
 * the underlying, the returned variations must be multiplied by
 * the integrated variance (including time) over the
 * corresponding time step.
 * <p>
 * \ingroup mcarlo
 */
public class BrownianBridge {
    private int size_;
    private List<Double> t_;
    private List<Double> sqrtdt_;
    private List<Integer> bridgeIndex_, leftIndex_, rightIndex_;
    private List<Double> leftWeight_, rightWeight_, stdDev_;

    /**
     * !The constructor generates the time grid so that each step
     * is of unit-time length.
     * <p>
     * \param steps The number of steps in the path
     */
    public BrownianBridge(int steps) {
        size_ = steps;
        t_ = CommonUtil.ArrayInit(size_);
        sqrtdt_ = CommonUtil.ArrayInit(size_);
        bridgeIndex_ = CommonUtil.ArrayInit(size_);
        leftIndex_ = CommonUtil.ArrayInit(size_);
        rightIndex_ = CommonUtil.ArrayInit(size_);
        leftWeight_ = CommonUtil.ArrayInit(size_);
        rightWeight_ = CommonUtil.ArrayInit(size_);
        stdDev_ = CommonUtil.ArrayInit(size_);
        for (int i = 0; i < size_; ++i)
            t_.set(i, i + 1d);
        initialize();
    }

    /**
     * !The step times are copied from the supplied vector
     * <p>
     * \param times A vector containing the times at which the
     * steps occur. This also defines the number of
     * steps that will be generated.
     * <p>
     * \note the starting time of the path is assumed to be 0 and
     * must not be included
     */
    public BrownianBridge(final List<Double> times) {
        size_ = times.size();
        t_ = times;
        sqrtdt_ = CommonUtil.ArrayInit(size_);
        bridgeIndex_ = CommonUtil.ArrayInit(size_);
        leftIndex_ = CommonUtil.ArrayInit(size_);
        rightIndex_ = CommonUtil.ArrayInit(size_);
        leftWeight_ = CommonUtil.ArrayInit(size_);
        rightWeight_ = CommonUtil.ArrayInit(size_);
        stdDev_ = CommonUtil.ArrayInit(size_);
        initialize();
    }

    /**
     * ! The step times are copied from the TimeGrid object
     * <p>
     * \param timeGrid a time grid containing the times at which
     * the steps will occur
     */
    public BrownianBridge(final TimeGrid timeGrid) {
        size_ = timeGrid.size() - 1;
        t_ = CommonUtil.ArrayInit(size_);
        sqrtdt_ = CommonUtil.ArrayInit(size_);
        bridgeIndex_ = CommonUtil.ArrayInit(size_);
        leftIndex_ = CommonUtil.ArrayInit(size_);
        rightIndex_ = CommonUtil.ArrayInit(size_);
        leftWeight_ = CommonUtil.ArrayInit(size_);
        rightWeight_ = CommonUtil.ArrayInit(size_);
        stdDev_ = CommonUtil.ArrayInit(size_);
        for (int i = 0; i < size_; ++i)
            t_.set(i, timeGrid.get(i + 1));
        initialize();
    }

    //! \name inspectors
    //@{
    public int size() {
        return size_;
    }

    public final List<Double> times() {
        return t_;
    }

    public final List<Integer> bridgeIndex() {
        return bridgeIndex_;
    }

    public final List<Integer> leftIndex() {
        return leftIndex_;
    }

    public final List<Integer> rightIndex() {
        return rightIndex_;
    }

    public final List<Double> leftWeight() {
        return leftWeight_;
    }

    public final List<Double> rightWeight() {
        return rightWeight_;
    }

    public final List<Double> stdDeviation() {
        return stdDev_;
    }
    //@}

    //! Brownian-bridge generator function

    /**
     * ! Transforms an input sequence of random variates into a
     * sequence of variations in a Brownian bridge path.
     * <p>
     * \param begin  The start iterator of the input sequence. 此处使用List代替 input.size()
     * \param end    The end iterator of the input sequence.
     * \param output The start iterator of the output sequence.
     * <p>
     * \note To get the canonical Brownian bridge which starts
     * and finishes at the same value, the first element of
     * the input sequence must be zero. Conversely, to get
     * a sloped bridge set the first element to a non-zero
     * value. In this case, the final value in the bridge
     * will be sqrt(last time point)*(first element of
     * input sequence).
     */
    public void transform(List<Double> input, List<Double> output, int start) {
        QL_REQUIRE(input.size() == size_,
                "incompatible sequence size");
        // We use output to store the path...
        output.set(start + size_ - 1, stdDev_.get(0) * input.get(0));
        for (int i = 1; i < size_; ++i) {
            int j = leftIndex_.get(i);
            int k = rightIndex_.get(i);
            int l = bridgeIndex_.get(i);
            if (j != 0) {
                output.set(l + start,
                        leftWeight_.get(i) * output.get(start + j - 1) +
                                rightWeight_.get(i) * output.get(start + k) +
                                stdDev_.get(i) * input.get(i));
            } else {
                output.set(l+start,
                        rightWeight_.get(i) * output.get(start + k) +
                                stdDev_.get(i) * input.get(i));
            }
        }
        // ...after which, we calculate the variations and
        // normalize to unit times
        for (int i = size_ - 1; i >= 1; --i) {
            output.set(start + i, output.get(start + i) - output.get(start + i - 1));
            output.set(start + i, output.get(start + i) / sqrtdt_.get(i));
        }
        output.set(start, output.get(start) / sqrtdt_.get(0));
    }

    private void initialize() {

        sqrtdt_.set(0, Math.sqrt(t_.get(0)));
        for (int i = 1; i < size_; ++i)
            sqrtdt_.set(i, Math.sqrt(t_.get(i) - t_.get(i - 1)));

        // map is used to indicate which points are already constructed.
        // If map[i] is zero, path point i is yet unconstructed.
        // map[i]-1 is the index of the variate that constructs
        // the path point # i.
        List<Integer> map = CommonUtil.ArrayInit(size_, 0);

        //  The first point in the construction is the global step.
        map.set(size_ - 1, 1);
        //  The global step is constructed from the first variate.
        bridgeIndex_.set(0, size_ - 1);
        //  The variance of the global step
        stdDev_.set(0, Math.sqrt(t_.get(size_ - 1)));
        //  The global step to the last point in time is special.
        leftWeight_.set(0, 0d);
        rightWeight_.set(0, 0.0);
        for (int j = 0, i = 1; i < size_; ++i) {
            // Find the next unpopulated entry in the map.
            while (map.get(j) != 0)
                ++j;
            int k = j;
            // Find the next populated entry in the map from there.
            while (map.get(k) == 0)
                ++k;
            // l-1 is now the index of the point to be constructed next.
            int l = j + ((k - 1 - j) >>> 1);
            map.set(l, i);
            // The i-th Gaussian variate will be used to set point l-1.
            bridgeIndex_.set(i, l);
            leftIndex_.set(i, j);
            rightIndex_.set(i, k);
            if (j != 0) {
                leftWeight_.set(i, (t_.get(k) - t_.get(l)) / (t_.get(k) - t_.get(j - 1)));
                rightWeight_.set(i, (t_.get(l) - t_.get(j - 1)) / (t_.get(k) - t_.get(j - 1)));
                stdDev_.set(i,
                        Math.sqrt(((t_.get(l) - t_.get(j - 1)) * (t_.get(k) - t_.get(l)))
                                / (t_.get(k) - t_.get(j - 1))));
            } else {
                leftWeight_.set(i, (t_.get(k) - t_.get(l)) / t_.get(k));
                rightWeight_.set(i, t_.get(l) / t_.get(k));
                stdDev_.set(i, Math.sqrt(t_.get(l) * (t_.get(k) - t_.get(l)) / t_.get(k)));
            }
            j = k + 1;
            if (j >= size_)
                j = 0;    //  wrap around
        }
    }
}
