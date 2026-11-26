package jquant.models.marketmodels.products.onestep;

import jquant.Payoff;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductOneStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/onestep/onestepoptionlets.hpp/.cpp} port.
 */
public class OneStepOptionlets extends MultiProductOneStep {

    private final List<Double> accruals_;
    private final List<Double> paymentTimes_;
    private final List<Payoff> payoffs_;

    public OneStepOptionlets(final List<Double> rateTimes,
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
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        for (int i = 0; i < payoffs_.size(); ++i) {
            double liborRate = currentState.forwardRate(i);
            double payoff = payoffs_.get(i).value(liborRate);
            if (payoff > 0.0) {
                numberCashFlowsThisStep.set(i, 1);
                genCashFlows.get(i).get(0).timeIndex = i;
                genCashFlows.get(i).get(0).amount = payoff * accruals_.get(i);
            }
        }

        return true;
    }

    @Override
    public OneStepOptionlets copy() {
        return new OneStepOptionlets(rateTimes_, accruals_, paymentTimes_, payoffs_);
    }
}
