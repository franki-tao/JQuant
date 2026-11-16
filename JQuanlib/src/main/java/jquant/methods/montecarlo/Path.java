package jquant.methods.montecarlo;

import jquant.TimeGrid;
import jquant.math.CommonUtil;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Single-factor random walk.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/path.hpp}.
 *
 * <p>The path includes the initial asset value as its first point.
 */
public class Path {
    private final TimeGrid timeGrid_;
    private final List<Double> values_;

    public Path(TimeGrid timeGrid) {
        this(timeGrid, CommonUtil.ArrayInit(timeGrid.size(), 0.0));
    }

    public Path(TimeGrid timeGrid, List<Double> values) {
        timeGrid_ = timeGrid;
        values_ = values;
        QL_REQUIRE(values_.size() == timeGrid_.size(),
                "different number of times and asset values");
    }

    public boolean empty() {
        return timeGrid_.empty();
    }

    public int length() {
        return timeGrid_.size();
    }

    //! asset value at the i-th point
    public double get(int i) {
        return values_.get(i);
    }

    public double at(int i) {
        return values_.get(i);
    }

    public void set(int i, double value) {
        values_.set(i, value);
    }

    public double value(int i) {
        return values_.get(i);
    }

    public void setValue(int i, double value) {
        values_.set(i, value);
    }

    //! time at the i-th point
    public double time(int i) {
        return timeGrid_.get(i);
    }

    //! initial asset value
    public double front() {
        return values_.get(0);
    }

    public void setFront(double value) {
        values_.set(0, value);
    }

    //! final asset value
    public double back() {
        return values_.get(values_.size() - 1);
    }

    public void setBack(double value) {
        values_.set(values_.size() - 1, value);
    }

    //! time grid
    public TimeGrid timeGrid() {
        return timeGrid_;
    }

    public List<Double> values() {
        return values_;
    }
}
