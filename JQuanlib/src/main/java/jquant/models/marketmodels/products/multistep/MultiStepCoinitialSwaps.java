package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multistep/multistepcoinitialswaps.hpp/.cpp} port.
 */
public class MultiStepCoinitialSwaps extends MultiProductMultiStep {

    private final List<Double> fixedAccruals_;
    private final List<Double> floatingAccruals_;
    private final List<Double> paymentTimes_;
    private final double fixedRate_;
    private final int lastIndex_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepCoinitialSwaps(final List<Double> rateTimes,
                                   List<Double> fixedAccruals,
                                   List<Double> floatingAccruals,
                                   final List<Double> paymentTimes,
                                   double fixedRate) {
        super(rateTimes);
        fixedAccruals_ = new ArrayList<>(fixedAccruals);
        floatingAccruals_ = new ArrayList<>(floatingAccruals);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        fixedRate_ = fixedRate;
        Utilities.checkIncreasingTimes(paymentTimes);

        lastIndex_ = rateTimes.size() - 1;
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return lastIndex_;
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
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);

        for (int i = currentIndex_; i < lastIndex_; i++) {
            genCashFlows.get(i).get(0).timeIndex = currentIndex_;
            genCashFlows.get(i).get(0).amount =
                    -fixedRate_ * fixedAccruals_.get(currentIndex_);

            genCashFlows.get(i).get(1).timeIndex = currentIndex_;
            genCashFlows.get(i).get(1).amount =
                    liborRate * floatingAccruals_.get(currentIndex_);

            numberCashFlowsThisStep.set(i, 2);
        }
        ++currentIndex_;

        return (currentIndex_ == lastIndex_);
    }

    @Override
    public MultiStepCoinitialSwaps copy() {
        MultiStepCoinitialSwaps result = new MultiStepCoinitialSwaps(rateTimes_,
                fixedAccruals_, floatingAccruals_, paymentTimes_, fixedRate_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
