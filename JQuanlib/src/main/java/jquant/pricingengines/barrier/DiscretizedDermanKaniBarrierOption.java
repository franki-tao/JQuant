package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.DiscretizedAsset;
import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.Array;

import java.util.List;

/**
 * Discretized barrier option with the Derman-Kani adjustment.
 *
 * <p>Port of QuantLib {@code DiscretizedDermanKaniBarrierOption} from
 * {@code ql/pricingengines/barrier/discretizedbarrieroption.{hpp,cpp}}.
 */
public class DiscretizedDermanKaniBarrierOption extends DiscretizedAsset {
    private final DiscretizedBarrierOption unenhanced_;

    public DiscretizedDermanKaniBarrierOption(BarrierOption.arguments args,
                                              StochasticProcess process,
                                              TimeGrid grid) {
        unenhanced_ = new DiscretizedBarrierOption(args, process, grid);
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
        adjustBarrier(values_, grid);
        unenhanced_.checkBarrier(values_, grid); // compute payoffs
    }

    private void adjustBarrier(Array optvalues, Array grid) {
        double barrier = unenhanced_.arguments().barrier;
        double rebate = unenhanced_.arguments().rebate;
        switch (unenhanced_.arguments().barrierType) {
            case DownIn:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) <= barrier && grid.get(j + 1) > barrier) {
                        // grid[j+1] above barrier, grid[j] under (in),
                        // interpolate optvalues[j+1]
                        double ltob = (barrier - grid.get(j));
                        double htob = (grid.get(j + 1) - barrier);
                        double htol = (grid.get(j + 1) - grid.get(j));
                        double u1 = unenhanced_.values().get(j + 1);
                        double t1 = unenhanced_.vanilla().get(j + 1);
                        optvalues.set(j + 1, Math.max(0.0, (ltob * t1 + htob * u1) / htol));
                    }
                }
                break;
            case DownOut:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) <= barrier && grid.get(j + 1) > barrier) {
                        // grid[j+1] above barrier, grid[j] under (out),
                        // interpolate optvalues[j+1]
                        double a = (barrier - grid.get(j)) * rebate;
                        double b = (grid.get(j + 1) - barrier)
                                * unenhanced_.values().get(j + 1);
                        double c = (grid.get(j + 1) - grid.get(j));
                        optvalues.set(j + 1, Math.max(0.0, (a + b) / c));
                    }
                }
                break;
            case UpIn:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) < barrier && grid.get(j + 1) >= barrier) {
                        // grid[j+1] above barrier (in), grid[j] under,
                        // interpolate optvalues[j]
                        double ltob = (barrier - grid.get(j));
                        double htob = (grid.get(j + 1) - barrier);
                        double htol = (grid.get(j + 1) - grid.get(j));
                        double u = unenhanced_.values().get(j);
                        double t = unenhanced_.vanilla().get(j);
                        optvalues.set(j, Math.max(0.0, (ltob * u + htob * t) / htol)); // derman std
                    }
                }
                break;
            case UpOut:
                for (int j = 0; j < optvalues.size() - 1; ++j) {
                    if (grid.get(j) < barrier && grid.get(j + 1) >= barrier) {
                        // grid[j+1] above barrier (out), grid[j] under,
                        // interpolate optvalues[j]
                        double a = (barrier - grid.get(j))
                                * unenhanced_.values().get(j);
                        double b = (grid.get(j + 1) - barrier) * rebate;
                        double c = (grid.get(j + 1) - grid.get(j));
                        optvalues.set(j, Math.max(0.0, (a + b) / c));
                    }
                }
                break;
        }
    }
}
