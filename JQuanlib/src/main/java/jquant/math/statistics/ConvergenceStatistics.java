package jquant.math.statistics;


import jquant.math.Point;
import jquant.math.ReferencePkg;
import jquant.math.statistics.impl.Stat;

import java.util.ArrayList;
import java.util.List;

/**
 * ! statistics class with convergence table
 * ! This class decorates another statistics class adding a
 *   convergence table calculation. The table tracks the
 *   convergence of the mean.
 *
 *   It is possible to specify the number of samples at which the
 *   mean should be stored by mean of the second template
 *   parameter; the default is to store \f$ 2^{n-1} \f$ samples at
 *   the \f$ n \f$-th step. Any passed class must implement the
 *   following interface:
 *   \code
 *   Size initialSamples() const;
 *   Size nextSamples(Size currentSamples) const;
 *   \endcode
 *   as well as a copy constructor.
 *
 *  \test results are tested against known good values.
 */
public class ConvergenceStatistics {
    private List<Point<Integer, Double>> table_;
    private DoublingConvergenceSteps samplingRule_;
    private int nextSampleSize_;
    private Stat stat_;

    public ConvergenceStatistics(Stat stats, DoublingConvergenceSteps rule) {
        stat_ = stats;
        samplingRule_ = rule;
        table_ = new ArrayList<>();
        reset();
    }

    public ConvergenceStatistics(Stat stats) {
        this(stats, new DoublingConvergenceSteps());
    }

    public void add(ReferencePkg<Double> value, double weight) {
        stat_.add(value.getT(), weight);
        if (stat_.samples() == nextSampleSize_) {
            table_.add(new Point<>(stat_.samples(), stat_.mean()));
            nextSampleSize_ = samplingRule_.nextSamples(nextSampleSize_);
        }
    }

    public void addSequence(List<Double> values) {
        for (Double value : values) {
            add(new ReferencePkg<>(value), 1.0);
        }
    }

    public void addSequence(List<Double> values, List<Double> weights) {
        for (int i = 0; i < values.size(); i++) {
            add(new ReferencePkg<>(values.get(i)),  weights.get(i));
        }
    }

    public void reset() {
        stat_.reset();
        nextSampleSize_ = samplingRule_.initialSamples();
        table_.clear();
    }

    public final List<Point<Integer, Double>> convergenceTable() {
        return table_;
    }
}
