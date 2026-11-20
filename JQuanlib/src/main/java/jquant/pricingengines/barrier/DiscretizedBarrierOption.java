package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.DiscretizedAsset;
import jquant.Exercise;
import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.pricingengines.vanilla.DiscretizedVanillaOption;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Discretized barrier option used by tree lattices.
 *
 * <p>Port of QuantLib
 * {@code ql/pricingengines/barrier/discretizedbarrieroption.{hpp,cpp}}.
 */
public class DiscretizedBarrierOption extends DiscretizedAsset {
    private final BarrierOption.arguments arguments_;
    private final List<Double> stoppingTimes_ = new ArrayList<>();
    private final DiscretizedVanillaOption vanilla_;

    public DiscretizedBarrierOption(BarrierOption.arguments args,
                                    StochasticProcess process,
                                    TimeGrid grid) {
        arguments_ = args;
        vanilla_ = new DiscretizedVanillaOption(args, process, grid);

        QL_REQUIRE(!args.exercise.dates().isEmpty(),
                "specify at least one stopping date");

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
        vanilla_.initialize(method(), time());
        values_ = new Array(size, 0.0);
        adjustValues();
    }

    public Array vanilla() {
        return vanilla_.values();
    }

    public BarrierOption.arguments arguments() {
        return arguments_;
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
        if (arguments_.barrierType == Barrier.Type.DownIn
                || arguments_.barrierType == Barrier.Type.UpIn) {
            vanilla_.rollback(time());
        }
        Array grid = method().grid(time());
        checkBarrier(values_, grid);
    }

    public void checkBarrier(Array optvalues, Array grid) {
        double now = time();
        boolean endTime = isOnTime(stoppingTimes_.get(stoppingTimes_.size() - 1));
        boolean stoppingTime = false;
        switch (arguments_.exercise.type()) {
            case American:
                if ((now <= stoppingTimes_.get(1) || isOnTime(stoppingTimes_.get(1))) &&
                        (now >= stoppingTimes_.get(0) || isOnTime(stoppingTimes_.get(0))))
                    stoppingTime = true;
                break;
            case European:
                if (isOnTime(stoppingTimes_.get(0)))
                    stoppingTime = true;
                break;
            case Bermudan:
                for (double stoppingTimeValue : stoppingTimes_) {
                    if (isOnTime(stoppingTimeValue)) {
                        stoppingTime = true;
                        break;
                    }
                }
                break;
            default:
                QL_FAIL("invalid option type");
        }
        for (int j = 0; j < optvalues.size(); j++) {
            switch (arguments_.barrierType) {
                case DownIn:
                    if (grid.get(j) <= arguments_.barrier) {
                        // knocked in
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla_.values().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla_.values().get(j));
                        }
                    } else if (endTime) {
                        optvalues.set(j, arguments_.rebate);
                    }
                    break;
                case DownOut:
                    if (grid.get(j) <= arguments_.barrier) {
                        optvalues.set(j, arguments_.rebate); // knocked out
                    } else if (stoppingTime) {
                        optvalues.set(j, Math.max(optvalues.get(j),
                                arguments_.payoff.value(grid.get(j))));
                    }
                    break;
                case UpIn:
                    if (grid.get(j) >= arguments_.barrier) {
                        // knocked in
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla_.values().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla_.values().get(j));
                        }
                    } else if (endTime) {
                        optvalues.set(j, arguments_.rebate);
                    }
                    break;
                case UpOut:
                    if (grid.get(j) >= arguments_.barrier) {
                        optvalues.set(j, arguments_.rebate); // knocked out
                    } else if (stoppingTime) {
                        optvalues.set(j, Math.max(optvalues.get(j),
                                arguments_.payoff.value(grid.get(j))));
                    }
                    break;
                default:
                    QL_FAIL("invalid barrier type");
            }
        }
    }
}
