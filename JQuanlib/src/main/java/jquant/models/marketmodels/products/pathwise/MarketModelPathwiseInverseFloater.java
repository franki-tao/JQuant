package jquant.models.marketmodels.products.pathwise;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/pathwise/pathwiseproductinversefloater.hpp/.cpp} port.
 *
 * <p>Pathwise product inverse floater for doing Greeks.
 * Tested in MarketModels::testInverseFloater().
 */
public class MarketModelPathwiseInverseFloater extends MarketModelPathwiseMultiProduct {

    private final List<Double> rateTimes_;
    private final List<Double> fixedAccruals_;
    private final List<Double> floatingAccruals_;
    private final List<Double> fixedStrikes_;
    private final List<Double> fixedMultipliers_;
    private final List<Double> floatingSpreads_;
    private final List<Double> paymentTimes_;
    private final double multiplier_;
    private final int lastIndex_;

    private final EvolutionDescription evolution_;

    // things that vary in a path
    private int currentIndex_;

    public MarketModelPathwiseInverseFloater(
            final List<Double> rateTimes,
            List<Double> fixedAccruals,
            final List<Double> floatingAccruals,
            final List<Double> fixedStrikes,
            final List<Double> fixedMultipliers,
            final List<Double> floatingSpreads,
            final List<Double> paymentTimes,
            boolean payer) {
        rateTimes_ = new ArrayList<>(rateTimes);
        fixedAccruals_ = new ArrayList<>(fixedAccruals);
        floatingAccruals_ = new ArrayList<>(floatingAccruals);
        fixedStrikes_ = new ArrayList<>(fixedStrikes);
        fixedMultipliers_ = new ArrayList<>(fixedMultipliers);
        floatingSpreads_ = new ArrayList<>(floatingSpreads);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        multiplier_ = payer ? -1.0 : 1.0;
        lastIndex_ = rateTimes.size() - 1;
        Utilities.checkIncreasingTimes(paymentTimes);
        QL_REQUIRE(fixedAccruals_.size() == lastIndex_,
                " Incorrect number of fixedAccruals given, should be " + lastIndex_
                        + " not " + fixedAccruals_.size());
        QL_REQUIRE(floatingAccruals.size() == lastIndex_,
                " Incorrect number of floatingAccruals given, should be " + lastIndex_
                        + " not " + floatingAccruals.size());
        QL_REQUIRE(fixedStrikes.size() == lastIndex_,
                " Incorrect number of fixedStrikes given, should be " + lastIndex_
                        + " not " + fixedStrikes.size());
        QL_REQUIRE(fixedMultipliers.size() == lastIndex_,
                " Incorrect number of fixedMultipliers given, should be " + lastIndex_
                        + " not " + fixedMultipliers.size());
        QL_REQUIRE(floatingSpreads.size() == lastIndex_,
                " Incorrect number of floatingSpreads given, should be " + lastIndex_
                        + " not " + floatingSpreads.size());
        QL_REQUIRE(paymentTimes.size() == lastIndex_,
                " Incorrect number of paymentTimes given, should be " + lastIndex_
                        + " not " + paymentTimes.size());

        List<Double> evolTimes = new ArrayList<>(rateTimes);
        evolTimes.remove(evolTimes.size() - 1);

        evolution_ = new EvolutionDescription(rateTimes_, evolTimes);
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelPathwiseMultiProduct.CashFlow>> cashFlowsGenerated) {
        numberCashFlowsThisStep.set(0, 1);
        for (int i = 1; i <= lastIndex_; ++i)
            cashFlowsGenerated.get(0).get(0).amount.set(i, 0.0);

        double liborRate = currentState.forwardRate(currentIndex_);
        double inverseFloatingCoupon =
                Math.max((fixedStrikes_.get(currentIndex_)
                        - fixedMultipliers_.get(currentIndex_) * liborRate), 0.0)
                        * fixedAccruals_.get(currentIndex_);
        double floatingCoupon = (liborRate + floatingSpreads_.get(currentIndex_))
                * floatingAccruals_.get(currentIndex_);
        cashFlowsGenerated.get(0).get(0).timeIndex = currentIndex_;
        cashFlowsGenerated.get(0).get(0).amount.set(0,
                multiplier_ * (inverseFloatingCoupon - floatingCoupon));

        if (inverseFloatingCoupon > 0.0) {
            cashFlowsGenerated.get(0).get(0).amount.set(currentIndex_ + 1,
                    multiplier_ * (-fixedMultipliers_.get(currentIndex_)
                            * fixedAccruals_.get(currentIndex_)
                            - floatingAccruals_.get(currentIndex_)));
        } else {
            cashFlowsGenerated.get(0).get(0).amount.set(currentIndex_ + 1,
                    -multiplier_ * floatingAccruals_.get(currentIndex_));
        }

        ++currentIndex_;

        return (currentIndex_ == lastIndex_);
    }

    @Override
    public MarketModelPathwiseInverseFloater copy() {
        MarketModelPathwiseInverseFloater result = new MarketModelPathwiseInverseFloater(
                rateTimes_, fixedAccruals_, floatingAccruals_, fixedStrikes_,
                fixedMultipliers_, floatingSpreads_, paymentTimes_, multiplier_ == -1.0);
        result.currentIndex_ = currentIndex_;
        return result;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        List<Integer> numeraires = new ArrayList<>();
        for (int i = 0; i < lastIndex_; ++i)
            numeraires.add(i);
        return numeraires;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
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
        return 1;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }
}
