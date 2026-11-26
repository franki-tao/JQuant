package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multistep/multistepinversefloater.hpp/.cpp} port.
 *
 * <p>Tested in MarketModels::testInverseFloater().
 */
public class MultiStepInverseFloater extends MultiProductMultiStep {

    private final List<Double> fixedAccruals_;
    private final List<Double> floatingAccruals_;
    private final List<Double> fixedStrikes_;
    private final List<Double> fixedMultipliers_;
    private final List<Double> floatingSpreads_;
    private final List<Double> paymentTimes_;
    private final double multiplier_;
    private final int lastIndex_;
    // things that vary in a path
    private int currentIndex_;

    public MultiStepInverseFloater(final List<Double> rateTimes,
                                   List<Double> fixedAccruals,
                                   final List<Double> floatingAccruals,
                                   final List<Double> fixedStrikes,
                                   final List<Double> fixedMultipliers,
                                   final List<Double> floatingSpreads,
                                   final List<Double> paymentTimes,
                                   boolean payer) {
        super(rateTimes);
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

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        double liborRate = currentState.forwardRate(currentIndex_);
        double inverseFloatingCoupon =
                Math.max((fixedStrikes_.get(currentIndex_)
                        - fixedMultipliers_.get(currentIndex_) * liborRate), 0.0)
                        * fixedAccruals_.get(currentIndex_);
        double floatingCoupon = (liborRate + floatingSpreads_.get(currentIndex_))
                * floatingAccruals_.get(currentIndex_);

        genCashFlows.get(0).get(0).timeIndex = currentIndex_;
        genCashFlows.get(0).get(0).amount =
                multiplier_ * (inverseFloatingCoupon - floatingCoupon);

        numberCashFlowsThisStep.set(0, 1);
        ++currentIndex_;

        return (currentIndex_ == lastIndex_);
    }

    @Override
    public MultiStepInverseFloater copy() {
        MultiStepInverseFloater result = new MultiStepInverseFloater(rateTimes_,
                fixedAccruals_, floatingAccruals_, fixedStrikes_, fixedMultipliers_,
                floatingSpreads_, paymentTimes_, multiplier_ == -1.0);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
