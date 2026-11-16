package jquant.pricingengines.vanilla;

import jquant.DiscretizedAsset;
import jquant.Exercise;
import jquant.Option;
import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.Array;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Discretized vanilla option used by tree lattices.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/vanilla/discretizedvanillaoption.cpp}.
 */
public class DiscretizedVanillaOption extends DiscretizedAsset {
    private final Option.arguments arguments_;
    private final List<Double> stoppingTimes_ = new ArrayList<>();

    public DiscretizedVanillaOption(Option.arguments args,
                                    StochasticProcess process,
                                    TimeGrid grid) {
        arguments_ = args;
        for (int i = 0; i < args.exercise.dates().size(); ++i) {
            double stoppingTime = process.time(args.exercise.date(i));
            if (grid != null && !grid.empty()) {
                // adjust to the given grid
                stoppingTime = grid.closestTime(stoppingTime);
            }
            stoppingTimes_.add(stoppingTime);
        }
    }

    @Override
    public void reset(int size) {
        values_ = new Array(size, 0.0);
        adjustValues();
    }

    @Override
    public List<Double> mandatoryTimes() {
        return stoppingTimes_;
    }

    @Override
    protected void preAdjustValuesImpl() {
        // nothing to do
    }

    @Override
    protected void postAdjustValuesImpl() {
        double now = time();
        switch (arguments_.exercise.type()) {
            case American:
                if ((now <= stoppingTimes_.get(1) || isOnTime(stoppingTimes_.get(1))) &&
                        (now >= stoppingTimes_.get(0) || isOnTime(stoppingTimes_.get(0))))
                    applySpecificCondition();
                break;
            case European:
                if (isOnTime(stoppingTimes_.get(0)))
                    applySpecificCondition();
                break;
            case Bermudan:
                for (double stoppingTime : stoppingTimes_) {
                    if (isOnTime(stoppingTime))
                        applySpecificCondition();
                }
                break;
            default:
                QL_FAIL("invalid option type");
        }
    }

    private void applySpecificCondition() {
        Array grid = method().grid(time());
        for (int j = 0; j < values_.size(); j++) {
            values_.set(j, Math.max(values_.get(j), arguments_.payoff.value(grid.get(j))));
        }
    }
}
