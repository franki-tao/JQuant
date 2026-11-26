package jquant.models.marketmodels.products.onestep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductOneStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/onestep/onestepforwards.hpp/.cpp} port.
 */
public class OneStepForwards extends MultiProductOneStep {

    private final List<Double> accruals_;
    private final List<Double> paymentTimes_;
    private final List<Double> strikes_;

    public OneStepForwards(final List<Double> rateTimes,
                           List<Double> accruals,
                           final List<Double> paymentTimes,
                           List<Double> strikes) {
        super(rateTimes);
        accruals_ = new ArrayList<>(accruals);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        strikes_ = new ArrayList<>(strikes);
        Utilities.checkIncreasingTimes(paymentTimes);
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return strikes_.size();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        for (int i = 0; i < strikes_.size(); ++i) {
            double liborRate = currentState.forwardRate(i);
            genCashFlows.get(i).get(0).timeIndex = i;
            genCashFlows.get(i).get(0).amount =
                    (liborRate - strikes_.get(i)) * accruals_.get(i);
        }
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 1);
        return true;
    }

    @Override
    public OneStepForwards copy() {
        return new OneStepForwards(rateTimes_, accruals_, paymentTimes_, strikes_);
    }
}
