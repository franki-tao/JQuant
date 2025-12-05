package jquant;

import jquant.math.CommonUtil;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

//! time grid class
/*! \todo what was the rationale for limiting the grid to
          positive times? Investigate and see whether we
          can use it for negative ones as well.
*/
public class TimeGrid {
    private List<Double> times_;
    private List<Double> dt_;
    private List<Double> mandatoryTimes_;

    //! \name Constructors
    public TimeGrid() {
    }

    //! Regularly spaced time-grid
    public TimeGrid(double end, int steps) {
        // We seem to assume that the grid begins at 0.
        // Let's enforce the assumption for the time being
        // (even though I'm not sure that I agree.)
        QL_REQUIRE(end > 0.0,
                "negative times not allowed");
        double dt = end / steps;
        times_ = new ArrayList<>();
        for (int i = 0; i <= steps; i++)
            times_.add(dt * i);

        mandatoryTimes_ = CommonUtil.ArrayInit(1, 0d);
        mandatoryTimes_.set(0, end);

        dt_ = CommonUtil.ArrayInit(steps, dt);
    }

    public TimeGrid(List<Double> arr, int begin, int end) {
        mandatoryTimes_ = CommonUtil.ArrayInit(end - begin);
        for (int i = begin; i < end; i++) {
            mandatoryTimes_.set(i - begin, arr.get(i));
        }
        QL_REQUIRE(begin != end, "empty time sequence");
        mandatoryTimes_.sort(null);
        // We seem to assume that the grid begins at 0.
        // Let's enforce the assumption for the time being
        // (even though I'm not sure that I agree.)
        QL_REQUIRE(mandatoryTimes_.get(0) >= 0.0,
                "negative times not allowed");
        unique(mandatoryTimes_);

        if (mandatoryTimes_.get(0) > 0.0)
            times_.add(0.0);

        times_.addAll(mandatoryTimes_);
        dt_ = adjacent_difference(times_.subList(1, times_.size()));
    }

    public TimeGrid(List<Double> arr, int begin, int end, int steps) {
        mandatoryTimes_ = CommonUtil.ArrayInit(end - begin);
        for (int i = begin; i < end; i++) {
            mandatoryTimes_.set(i - begin, arr.get(i));
        }
        QL_REQUIRE(begin != end, "empty time sequence");
        mandatoryTimes_.sort(null);
        // We seem to assume that the grid begins at 0.
        // Let's enforce the assumption for the time being
        // (even though I'm not sure that I agree.)
        QL_REQUIRE(mandatoryTimes_.get(0) >= 0.0,
                "negative times not allowed");
        unique(mandatoryTimes_);
        double last = mandatoryTimes_.get(mandatoryTimes_.size() - 1);
        double dtMax;
        // The resulting timegrid have points at times listed in the input
        // list. Between these points, there are inner-points which are
        // regularly spaced.
        if (steps == 0) {
            List<Double> diff = adjacent_difference(mandatoryTimes_);
            QL_REQUIRE(!diff.isEmpty(), "at least two distinct points required in time grid");

            if (diff.get(0) == 0.0)
                diff.remove(0);

            int i = minElementIndex(diff);
            QL_REQUIRE(i != -1, "not enough distinct points in time grid");
            dtMax = diff.get(i);
        } else {
            dtMax = last / steps;
        }

        double periodBegin = 0.0;
        times_.add(periodBegin);
        for (int t = 0;
             t < mandatoryTimes_.size();
             ++t) {
            double periodEnd = mandatoryTimes_.get(t);
            if (periodEnd != 0.0) {
                // the nearest integer, at least 1
                int nSteps = Math.max((int) (Math.round((periodEnd - periodBegin) / dtMax)), 1);
                double dt = (periodEnd - periodBegin) / nSteps;
                for (int n = 1; n <= nSteps; ++n)
                    times_.add(periodBegin + n * dt);
            }
            periodBegin = periodEnd;
        }

        dt_ = adjacent_difference(times_.subList(1, times_.size()));
    }

    public TimeGrid(List<Double> times) {
        this(times, 0, times.size());
    }

    public TimeGrid(List<Double> times, int steps) {
        this(times, 0, times.size(), steps);
    }

    //@}
    //! \name Time grid interface
    //@{
    //! returns the index i such that grid[i] = t
    public int index(double t) {
        int i = closestIndex(t);
        if (close_enough(t, times_.get(i))) {
            return i;
        } else {
            if (t < times_.get(0)) {
                QL_FAIL("using inadequate time grid: all nodes " +
                        "are later than the required time t = "
                        + t
                        + " (earliest node is t1 = "
                        + times_.get(0) + ")");
            } else if (t > times_.get(times_.size() - 1)) {
                QL_FAIL("using inadequate time grid: all nodes " +
                        "are earlier than the required time t = "
                        + t
                        + " (latest node is t1 = "
                        + times_.get(times_.size() - 1) + ")");
            } else {
                int j, k;
                if (t > times_.get(i)) {
                    j = i;
                    k = i + 1;
                } else {
                    j = i - 1;
                    k = i;
                }
                QL_FAIL("using inadequate time grid: the nodes closest " +
                        "to the required time t = "
                        + t
                        + " are t1 = "
                        + times_.get(j)
                        + " and t2 = "
                        + times_.get(k));
            }
        }
        return i;
    }

    //! returns the index i such that grid[i] is closest to t
    public int closestIndex(double t) {

        int result = lowerBound(times_, t);
        if (result == 0) {
            return 0;
        } else if (result == times_.size()) {
            return times_.size() - 1;
        } else {
            double dt1 = times_.get(result) - t;
            double dt2 = t - times_.get(result - 1);
            if (dt1 < dt2)
                return result;
            else
                return result - 1;
        }
    }

    //! returns the time on the grid closest to the given t
    public double closestTime(double t) {
        return times_.get(closestIndex(t));
    }

    public final List<Double> mandatoryTimes() {
        return mandatoryTimes_;
    }

    public double dt(int i) {
        return dt_.get(i);
    }

    public double get(int i) {
        return times_.get(i);
    }

    public double at(int i) {
        return times_.get(i);
    }

    public int size() {
        return times_.size();
    }

    public boolean empty() {
        return times_.isEmpty();
    }

    public double front() {
        return times_.get(0);
    }

    public double back() {
        return times_.get(times_.size() - 1);
    }


    private void unique(List<Double> list) {
        // 慢指针：指向“不重复区域的最后一个位置”
        int slow = 0;
        // 快指针：遍历所有元素
        for (int fast = 1; fast < list.size(); fast++) {
            // 若当前元素与不重复区域最后一个元素“不近似相等”，则保留
            if (!close_enough(list.get(slow), list.get(fast))) {
                slow++; // 慢指针后移
                list.set(slow, list.get(fast)); // 前移不重复元素
            }
            // 若近似相等：跳过（视为重复，不保留）
        }

        // 移除慢指针之后的冗余元素（等价C++的erase）
        if (slow < list.size() - 1) {
            list.subList(slow + 1, list.size()).clear();
        }
    }

    private List<Double> adjacent_difference(List<Double> arr) {
        List<Double> diff = new ArrayList<>();
        diff.add(arr.get(0));
        for (int i = 1; i < arr.size(); i++) {
            diff.add(arr.get(i) - arr.get(i - 1));
        }
        return diff;
    }

    private int minElementIndex(List<Double> list) {
        // 空列表：返回 -1（等价 C++ std::min_element 返回 end()）
        if (list.isEmpty()) {
            return -1;
        }

        // 初始化：第一个元素为初始最小值，索引为 0
        int minIndex = 0;
        Double minValue = list.get(0);

        // 遍历后续元素，严格按 C++ < 规则比较
        for (int i = 1; i < list.size(); i++) {
            Double currentValue = list.get(i);
            // 仅当当前值 < 最小值时，才更新（保证第一个最小元素不被覆盖）
            if (currentValue < minValue) {
                minValue = currentValue;
                minIndex = i;
            }
        }

        return minIndex;
    }

    private int lowerBound(List<Double> list, double t) {
        int low = 0;
        int high = list.size();
        // 二分查找：找第一个 ≥ t 的索引
        while (low < high) {
            int mid = (low + high) / 2;
            if (list.get(mid) < t) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low; // 等价 C++ 的 result 迭代器（low 是索引，list.size() 等价 end）
    }
}
