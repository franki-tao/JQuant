package jquant.methods.montecarlo;

import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.CommonUtil;
import jquant.math.randomnumbers.InverseCumulativeRsg;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Generates random paths using a Gaussian sequence generator.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/pathgenerator.hpp}
 * for the single-variate case.
 */
public class PathGenerator {
    private final boolean brownianBridge_;
    private final InverseCumulativeRsg generator_;
    private final int dimension_;
    private final TimeGrid timeGrid_;
    private final StochasticProcess1D process_;
    private final SamplePath next_;
    private final List<Double> temp_;
    private final BrownianBridge bb_;

    public PathGenerator(StochasticProcess process,
                         double length,
                         int timeSteps,
                         InverseCumulativeRsg generator,
                         boolean brownianBridge) {
        this(process, new TimeGrid(length, timeSteps), generator, brownianBridge);
        QL_REQUIRE(dimension_ == timeSteps,
                "sequence generator dimensionality (" + dimension_
                        + ") != timeSteps (" + timeSteps + ")");
    }

    public PathGenerator(StochasticProcess process,
                         TimeGrid timeGrid,
                         InverseCumulativeRsg generator,
                         boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        generator_ = generator;
        dimension_ = generator_.dimension();
        timeGrid_ = timeGrid;
        QL_REQUIRE(process instanceof StochasticProcess1D, "1-D process required");
        process_ = (StochasticProcess1D) process;
        next_ = new SamplePath(new Path(timeGrid_), 1.0);
        temp_ = CommonUtil.ArrayInit(dimension_);
        bb_ = new BrownianBridge(timeGrid_);
        QL_REQUIRE(dimension_ == timeGrid_.size() - 1,
                "sequence generator dimensionality (" + dimension_
                        + ") != timeSteps (" + (timeGrid_.size() - 1) + ")");
    }

    public SamplePath next() {
        return next(false);
    }

    public SamplePath antithetic() {
        return next(true);
    }

    private SamplePath next(boolean antithetic) {
        SampleVector sequence = antithetic ? generator_.lastSequence()
                : generator_.nextSequence();

        List<Double> increments;
        if (brownianBridge_) {
            bb_.transform(sequence.value, temp_, 0);
            increments = temp_;
        } else {
            increments = sequence.value;
        }

        next_.weight = sequence.weight;

        Path path = next_.value;
        path.setFront(process_.x0());
        for (int i = 1; i < path.length(); i++) {
            double t = timeGrid_.get(i - 1);
            double dt = timeGrid_.dt(i - 1);
            path.set(i, process_.evolve(t, path.get(i - 1), dt,
                    antithetic ? -increments.get(i - 1) : increments.get(i - 1)));
        }

        return next_;
    }

    public int size() {
        return dimension_;
    }

    public TimeGrid timeGrid() {
        return timeGrid_;
    }
}
