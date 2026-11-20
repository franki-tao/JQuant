package jquant.experimental.barrieroption;

import jquant.DiscretizedAsset;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.Array;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Derman-Kani-Ergener-Bardhan discretized double-barrier option helper class.
 *
 * <p>Port of QuantLib {@code DiscretizedDermanKaniDoubleBarrierOption} from
 * {@code ql/experimental/barrieroption/discretizeddoublebarrieroption.{hpp,cpp}}.
 */
public class DiscretizedDermanKaniDoubleBarrierOption extends DiscretizedAsset {
    private final DiscretizedDoubleBarrierOption unenhanced_;

    public DiscretizedDermanKaniDoubleBarrierOption(
            DoubleBarrierOption.arguments args,
            StochasticProcess process,
            TimeGrid grid) {
        unenhanced_ = new DiscretizedDoubleBarrierOption(args, process, grid);
    }

    @Override
    public void reset(int size) {
        unenhanced_.initialize(method(), time());
        values_ = new Array(size, 0.0);
        adjustValues();
    }

    @Override
    public List<Double> mandatoryTimes() {
        return unenhanced_.mandatoryTimes();
    }

    @Override
    protected void preAdjustValuesImpl() {
        // nothing to do
    }

    @Override
    protected void postAdjustValuesImpl() {
        unenhanced_.rollback(time());

        Array grid = method().grid(time());
        unenhanced_.checkBarrier(values_, grid); // compute payoffs
        adjustBarrier(values_, grid);
    }

    private void adjustBarrier(Array optvalues, Array grid) {
        double barrier_lo = unenhanced_.arguments().barrier_lo;
        double barrier_hi = unenhanced_.arguments().barrier_hi;
        double rebate = unenhanced_.arguments().rebate;
        switch (unenhanced_.arguments().barrierType) {
            case KnockIn:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) <= barrier_lo && grid.get(j + 1) > barrier_lo) {
                        // grid[j+1] above barrier_lo, grid[j] under (in),
                        // interpolate optvalues[j+1]
                        double ltob = (barrier_lo - grid.get(j));
                        double htob = (grid.get(j + 1) - barrier_lo);
                        double htol = (grid.get(j + 1) - grid.get(j));
                        double u1 = unenhanced_.values().get(j + 1);
                        double t1 = unenhanced_.vanilla().get(j + 1);
                        optvalues.set(j + 1, Math.max(0.0,
                                (ltob * t1 + htob * u1) / htol)); // derman std
                    } else if (grid.get(j) < barrier_hi && grid.get(j + 1) >= barrier_hi) {
                        // grid[j+1] above barrier_hi (in), grid[j] under,
                        // interpolate optvalues[j]
                        double ltob = (barrier_hi - grid.get(j));
                        double htob = (grid.get(j + 1) - barrier_hi);
                        double htol = (grid.get(j + 1) - grid.get(j));
                        double u = unenhanced_.values().get(j);
                        double t = unenhanced_.vanilla().get(j);
                        optvalues.set(j, Math.max(0.0,
                                (ltob * u + htob * t) / htol)); // derman std
                    }
                }
                break;
            case KnockOut:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) <= barrier_lo && grid.get(j + 1) > barrier_lo) {
                        // grid[j+1] above barrier_lo, grid[j] under (out),
                        // interpolate optvalues[j+1]
                        double a = (barrier_lo - grid.get(j)) * rebate;
                        double b = (grid.get(j + 1) - barrier_lo)
                                * unenhanced_.values().get(j + 1);
                        double c = (grid.get(j + 1) - grid.get(j));
                        optvalues.set(j + 1, Math.max(0.0, (a + b) / c));
                    } else if (grid.get(j) < barrier_hi && grid.get(j + 1) >= barrier_hi) {
                        // grid[j+1] above barrier_hi (out), grid[j] under,
                        // interpolate optvalues[j]
                        double a = (barrier_hi - grid.get(j))
                                * unenhanced_.values().get(j);
                        double b = (grid.get(j + 1) - barrier_hi) * rebate;
                        double c = (grid.get(j + 1) - grid.get(j));
                        optvalues.set(j, Math.max(0.0, (a + b) / c));
                    }
                }
                break;
            default:
                QL_FAIL("unsupported barrier type");
                break;
        }
    }
}
