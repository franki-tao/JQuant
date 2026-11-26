package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/multistepnothing.hpp/.cpp} port.
 */
public class MultiStepNothing extends MultiProductMultiStep {

    private final int numberOfProducts_;
    private final int doneIndex_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepNothing(final EvolutionDescription evolution) {
        this(evolution, 1, 0);
    }

    public MultiStepNothing(final EvolutionDescription evolution,
                            int numberOfProducts,
                            int doneIndex) {
        super(evolution.rateTimes());
        numberOfProducts_ = numberOfProducts;
        doneIndex_ = doneIndex;
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return new ArrayList<>();
    }

    @Override
    public int numberOfProducts() {
        return numberOfProducts_;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 0;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        ++currentIndex_;
        return (currentIndex_ >= doneIndex_);
    }

    @Override
    public MultiStepNothing copy() {
        MultiStepNothing result = new MultiStepNothing(evolution(),
                numberOfProducts_, doneIndex_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
