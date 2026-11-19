package jquant.methods.montecarlo;

import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.randomnumbers.InverseCumulativeRsg;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Generates a multi path from a random-array generator.
 *
 * <p>Port of QuantLib {@code ql/methods/montecarlo/multipathgenerator.hpp}.
 */
public class MultiPathGenerator {
    private final boolean brownianBridge_;
    private final StochasticProcess process_;
    private final InverseCumulativeRsg generator_;
    private final SampleMultiPath next_;

    public MultiPathGenerator(StochasticProcess process,
                              TimeGrid times,
                              InverseCumulativeRsg generator,
                              boolean brownianBridge) {
        brownianBridge_ = brownianBridge;
        process_ = process;
        generator_ = generator;
        next_ = new SampleMultiPath(
                new MultiPath(process.size(), times), 1.0);

        QL_REQUIRE(generator_.dimension()
                        == process_.factors() * (times.size() - 1),
                "dimension (" + generator_.dimension()
                        + ") is not equal to ("
                        + process_.factors() + " * " + (times.size() - 1)
                        + ") the number of factors "
                        + "times the number of time steps");
        QL_REQUIRE(times.size() > 1, "no times given");
    }

    public SampleMultiPath next() {
        return next(false);
    }

    public SampleMultiPath antithetic() {
        return next(true);
    }

    private SampleMultiPath next(boolean antithetic) {
        if (brownianBridge_) {
            QL_FAIL("Brownian bridge not supported");
        }

        SampleVector sequence = antithetic
                ? generator_.lastSequence()
                : generator_.nextSequence();

        final int m = process_.size();
        final int n = process_.factors();

        MultiPath path = next_.value;

        Array asset = process_.initialValues();
        for (int j = 0; j < m; j++) {
            path.get(j).setFront(asset.get(j));
        }

        Array temp = new Array(n);
        next_.weight = sequence.weight;

        final TimeGrid timeGrid = path.get(0).timeGrid();
        for (int i = 1; i < path.pathSize(); i++) {
            final int offset = (i - 1) * n;
            final double t = timeGrid.get(i - 1);
            final double dt = timeGrid.dt(i - 1);
            for (int j = 0; j < n; j++) {
                temp.set(j, antithetic
                        ? -sequence.value.get(offset + j)
                        : sequence.value.get(offset + j));
            }

            asset = process_.evolve(t, asset, dt, temp);
            for (int j = 0; j < m; j++) {
                path.get(j).set(i, asset.get(j));
            }
        }
        return next_;
    }
}
