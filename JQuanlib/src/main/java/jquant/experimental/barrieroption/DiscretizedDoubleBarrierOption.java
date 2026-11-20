package jquant.experimental.barrieroption;

import jquant.DiscretizedAsset;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
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
 * Standard discretized double-barrier option helper class.
 *
 * <p>Used with {@code BinomialDoubleBarrierEngine} to implement a standard
 * binomial algorithm for double barrier options.
 *
 * <p>Port of QuantLib
 * {@code ql/experimental/barrieroption/discretizeddoublebarrieroption.{hpp,cpp}}.
 */
public class DiscretizedDoubleBarrierOption extends DiscretizedAsset {
    private final DoubleBarrierOption.arguments arguments_;
    private final List<Double> stoppingTimes_ = new ArrayList<>();
    private final DiscretizedVanillaOption vanilla_;

    public DiscretizedDoubleBarrierOption(
            DoubleBarrierOption.arguments args,
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

    public DoubleBarrierOption.arguments arguments() {
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
        if (arguments_.barrierType != DoubleBarrier.Type.KnockOut) {
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
                case KnockIn:
                    if (grid.get(j) <= arguments_.barrier_lo) {
                        // knocked in dn
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla().get(j));
                        }
                    } else if (grid.get(j) >= arguments_.barrier_hi) {
                        // knocked in up
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla().get(j));
                        }
                    } else if (endTime) {
                        optvalues.set(j, arguments_.rebate);
                    }
                    break;
                case KnockOut:
                    if (grid.get(j) <= arguments_.barrier_lo) {
                        optvalues.set(j, arguments_.rebate); // knocked out lo
                    } else if (grid.get(j) >= arguments_.barrier_hi) {
                        optvalues.set(j, arguments_.rebate); // knocked out hi
                    } else if (stoppingTime) {
                        optvalues.set(j, Math.max(optvalues.get(j),
                                arguments_.payoff.value(grid.get(j))));
                    }
                    break;
                case KIKO:
                    // low barrier is KI, high is KO
                    if (grid.get(j) <= arguments_.barrier_lo) {
                        // knocked in dn
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla().get(j));
                        }
                    } else if (grid.get(j) >= arguments_.barrier_hi) {
                        optvalues.set(j, arguments_.rebate); // knocked out hi
                    } else if (endTime) {
                        optvalues.set(j, arguments_.rebate);
                    }
                    break;
                case KOKI:
                    // low barrier is KO, high is KI
                    if (grid.get(j) <= arguments_.barrier_lo) {
                        optvalues.set(j, arguments_.rebate); // knocked out lo
                    } else if (grid.get(j) >= arguments_.barrier_hi) {
                        // knocked in up
                        if (stoppingTime) {
                            optvalues.set(j, Math.max(vanilla().get(j),
                                    arguments_.payoff.value(grid.get(j))));
                        } else {
                            optvalues.set(j, vanilla().get(j));
                        }
                    } else if (endTime) {
                        optvalues.set(j, arguments_.rebate);
                    }
                    break;
                default:
                    QL_FAIL("invalid barrier type");
            }
        }
    }
}
