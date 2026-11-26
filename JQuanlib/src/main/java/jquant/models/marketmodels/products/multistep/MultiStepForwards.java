package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multistep/multistepforwards.hpp/.cpp} port.
 */
public class MultiStepForwards extends MultiProductMultiStep {

    private final List<Double> accruals_;
    private final List<Double> paymentTimes_;
    private final List<Double> strikes_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepForwards(final List<Double> rateTimes,
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
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        double liborRate = currentState.forwardRate(currentIndex_);
        genCashFlows.get(currentIndex_).get(0).timeIndex = currentIndex_;
        genCashFlows.get(currentIndex_).get(0).amount =
                (liborRate - strikes_.get(currentIndex_)) * accruals_.get(currentIndex_);
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        numberCashFlowsThisStep.set(currentIndex_, 1);
        ++currentIndex_;
        return (currentIndex_ == strikes_.size());
    }

    @Override
    public MultiStepForwards copy() {
        MultiStepForwards result = new MultiStepForwards(rateTimes_, accruals_,
                paymentTimes_, strikes_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
