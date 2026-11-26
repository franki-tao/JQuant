package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;
import jquant.pricingengines.StrikedTypePayoff;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/multistepcoterminalswaptions.hpp/.cpp} port.
 */
public class MultiStepCoterminalSwaptions extends MultiProductMultiStep {

    private final List<Double> paymentTimes_;
    private final List<StrikedTypePayoff> payoffs_;
    private final int lastIndex_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepCoterminalSwaptions(final List<Double> rateTimes,
                                        final List<Double> paymentTimes,
                                        List<StrikedTypePayoff> payoffs) {
        super(rateTimes);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        payoffs_ = new ArrayList<>(payoffs);
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
        genCashFlows.get(currentIndex_).get(0).timeIndex = currentIndex_;

        double swapRate = currentState.coterminalSwapRate(currentIndex_);
        double annuity = currentState.coterminalSwapAnnuity(currentIndex_, currentIndex_);
        genCashFlows.get(currentIndex_).get(0).amount =
                payoffs_.get(currentIndex_).value(swapRate) * annuity;
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        numberCashFlowsThisStep.set(currentIndex_, 1);
        ++currentIndex_;
        return (currentIndex_ == lastIndex_);
    }

    @Override
    public MultiStepCoterminalSwaptions copy() {
        MultiStepCoterminalSwaptions result = new MultiStepCoterminalSwaptions(rateTimes_,
                paymentTimes_, payoffs_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
