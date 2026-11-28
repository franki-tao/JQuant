package jquant.pricingengines;

import jquant.Instrument;
import jquant.Lattice;
import jquant.PricingEngine;
import jquant.TimeGrid;
import jquant.models.ShortRateModel;

import java.util.HashMap;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Engine for a short-rate model specialized on a lattice.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/latticeshortratemodelengine.hpp}. The official
 * class is a template over the arguments/results pair; Java has no way to
 * instantiate those types generically, so they are passed to the constructor
 * by the concrete engine.
 */
public abstract class LatticeShortRateModelEngine<
        A extends PricingEngine.arguments,
        R extends PricingEngine.results> extends GenericEngine<A, R> {

    protected final ShortRateModel model_;
    protected TimeGrid timeGrid_ = new TimeGrid();
    protected int timeSteps_;
    protected Lattice lattice_;

    protected LatticeShortRateModelEngine(ShortRateModel model,
                                          int timeSteps,
                                          A arguments,
                                          R results) {
        super(arguments, results);
        initResults(results);
        model_ = model;
        timeSteps_ = timeSteps;
        QL_REQUIRE(timeSteps > 0, "timeSteps must be positive, "
                + timeSteps + " not allowed");
        registerWith(model_);
    }

    protected LatticeShortRateModelEngine(ShortRateModel model,
                                          TimeGrid timeGrid,
                                          A arguments,
                                          R results) {
        super(arguments, results);
        initResults(results);
        model_ = model;
        timeGrid_ = timeGrid;
        timeSteps_ = 0;
        registerWith(model_);
        lattice_ = model_.tree(timeGrid_);
    }

    private static void initResults(PricingEngine.results results) {
        if (results instanceof Instrument.results) {
            ((Instrument.results) results).additionalResults = new HashMap<>();
        }
    }

    @Override
    public void update() {
        if (timeGrid_ != null && !timeGrid_.empty())
            lattice_ = model_.tree(timeGrid_);
        super.update();
    }
}
