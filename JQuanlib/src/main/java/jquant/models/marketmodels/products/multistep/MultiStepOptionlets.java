package jquant.models.marketmodels.products.multistep;

import jquant.Payoff;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multistep/multistepoptionlets.hpp/.cpp} port.
 */
public class MultiStepOptionlets extends MultiProductMultiStep {

    private final List<Double> accruals_;
    private final List<Double> paymentTimes_;
    private final List<Payoff> payoffs_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepOptionlets(final List<Double> rateTimes,
                               List<Double> accruals,
                               final List<Double> paymentTimes,
                               List<? extends Payoff> payoffs) {
        super(rateTimes);
        accruals_ = new ArrayList<>(accruals);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        payoffs_ = new ArrayList<>(payoffs);
        Utilities.checkIncreasingTimes(paymentTimes);
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return payoffs_.size();
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
                payoffs_.get(currentIndex_).value(liborRate) * accruals_.get(currentIndex_);
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        numberCashFlowsThisStep.set(currentIndex_, 1);
        ++currentIndex_;
        return (currentIndex_ == payoffs_.size());
    }

    @Override
    public MultiStepOptionlets copy() {
        MultiStepOptionlets result = new MultiStepOptionlets(rateTimes_, accruals_,
                paymentTimes_, payoffs_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
