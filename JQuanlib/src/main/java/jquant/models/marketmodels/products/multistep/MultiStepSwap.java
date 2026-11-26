package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/multistepswap.hpp/.cpp} port.
 */
public class MultiStepSwap extends MultiProductMultiStep {

    private final List<Double> fixedAccruals_;
    private final List<Double> floatingAccruals_;
    private final List<Double> paymentTimes_;
    private final double fixedRate_;
    private final double multiplier_;
    private final int lastIndex_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepSwap(final List<Double> rateTimes,
                         List<Double> fixedAccruals,
                         List<Double> floatingAccruals,
                         final List<Double> paymentTimes,
                         double fixedRate,
                         boolean payer) {
        super(rateTimes);
        fixedAccruals_ = new ArrayList<>(fixedAccruals);
        floatingAccruals_ = new ArrayList<>(floatingAccruals);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        fixedRate_ = fixedRate;
        multiplier_ = payer ? 1.0 : -1.0;
        lastIndex_ = rateTimes.size() - 1;
        Utilities.checkIncreasingTimes(paymentTimes);
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return 1;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 2;
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
        genCashFlows.get(0).get(0).timeIndex = currentIndex_;
        genCashFlows.get(0).get(0).amount =
                -multiplier_ * fixedRate_ * fixedAccruals_.get(currentIndex_);
        genCashFlows.get(0).get(1).timeIndex = currentIndex_;
        genCashFlows.get(0).get(1).amount =
                multiplier_ * liborRate * floatingAccruals_.get(currentIndex_);
        numberCashFlowsThisStep.set(0, 2);
        ++currentIndex_;
        return (currentIndex_ == lastIndex_);
    }

    @Override
    public MultiStepSwap copy() {
        MultiStepSwap result = new MultiStepSwap(rateTimes_, fixedAccruals_,
                floatingAccruals_, paymentTimes_, fixedRate_, multiplier_ > 0.0);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
