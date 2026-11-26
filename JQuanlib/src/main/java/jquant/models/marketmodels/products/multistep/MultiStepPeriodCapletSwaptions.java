package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductMultiStep;
import jquant.pricingengines.StrikedTypePayoff;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/multistepperiodcapletswaptions.hpp/.cpp} port.
 */
public class MultiStepPeriodCapletSwaptions extends MultiProductMultiStep {

    private final List<Double> paymentTimes_;
    private final List<Double> forwardOptionPaymentTimes_;
    private final List<Double> swaptionPaymentTimes_;
    private final List<StrikedTypePayoff> forwardPayOffs_;
    private final List<StrikedTypePayoff> swapPayOffs_;
    private final int lastIndex_;
    private final int period_;
    private final int offset_;
    private final int numberFRAs_;
    private final int numberBigFRAs_;

    // things that vary in a path
    private int currentIndex_;
    private int productIndex_;

    public MultiStepPeriodCapletSwaptions(
            final List<Double> rateTimes,
            final List<Double> forwardOptionPaymentTimes,
            final List<Double> swaptionPaymentTimes,
            List<StrikedTypePayoff> forwardPayOffs,
            List<StrikedTypePayoff> swapPayOffs,
            int period,
            int offset) {
        super(rateTimes);
        paymentTimes_ = new ArrayList<>(forwardOptionPaymentTimes);
        forwardOptionPaymentTimes_ = new ArrayList<>(forwardOptionPaymentTimes);
        swaptionPaymentTimes_ = new ArrayList<>(swaptionPaymentTimes);
        forwardPayOffs_ = new ArrayList<>(forwardPayOffs);
        swapPayOffs_ = new ArrayList<>(swapPayOffs);
        period_ = period;
        offset_ = offset;
        QL_REQUIRE(rateTimes.size() >= 2,
                "we need at least two rate times in MultiStepPeriodCapletSwaptions ");

        Utilities.checkIncreasingTimes(forwardOptionPaymentTimes);
        Utilities.checkIncreasingTimes(swaptionPaymentTimes);
        for (double swaptionPaymentTime : swaptionPaymentTimes_)
            paymentTimes_.add(swaptionPaymentTime);
        lastIndex_ = rateTimes.size() - 1;
        numberFRAs_ = rateTimes.size() - 1;
        numberBigFRAs_ = (numberFRAs_ - offset_) / period_;

        QL_REQUIRE(offset_ < period_,
                "the offset must be less then the period in MultiStepPeriodCapletSwaptions ");
        QL_REQUIRE(numberBigFRAs_ > 0,
                "we must have at least one FRA after the periodizing in  MultiStepPeriodCapletSwaptions ");

        QL_REQUIRE(forwardOptionPaymentTimes_.size() == numberBigFRAs_,
                "we must have precisely one payment time for each forward option  MultiStepPeriodCapletSwaptions ");

        QL_REQUIRE(forwardPayOffs_.size() == numberBigFRAs_,
                "we must have precisely one payoff  for each forward option  MultiStepPeriodCapletSwaptions ");

        QL_REQUIRE(swaptionPaymentTimes_.size() == numberBigFRAs_,
                "we must have precisely one payment time for each swaption in MultiStepPeriodCapletSwaptions ");

        QL_REQUIRE(swapPayOffs_.size() == numberBigFRAs_,
                "we must have precisely one payoff  for each swaption in  MultiStepPeriodCapletSwaptions ");
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return numberBigFRAs_ * 2;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
        productIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> genCashFlows) {
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);

        if (currentIndex_ >= offset_ && (currentIndex_ - offset_) % period_ == 0) {
            // caplet first
            double df = currentState.discountRatio(currentIndex_ + period_, currentIndex_);
            double tau = rateTimes_.get(currentIndex_ + period_) - rateTimes_.get(currentIndex_);
            double forward = (1.0 / df - 1.0) / tau;
            double value = forwardPayOffs_.get(productIndex_).value(forward);
            value *= tau * currentState.discountRatio(currentIndex_ + period_, currentIndex_);

            if (value > 0) {
                numberCashFlowsThisStep.set(productIndex_, 1);
                genCashFlows.get(productIndex_).get(0).amount = value;
                genCashFlows.get(productIndex_).get(0).timeIndex = productIndex_;
            }

            // now swaption
            int numberPeriods = numberBigFRAs_ - productIndex_;
            double B = 0.0;
            double P0 = 1.0; // i.e currentState.discountRatio(currentIndex_,currentIndex_);
            double Pn = currentState.discountRatio(
                    currentIndex_ + numberPeriods * period_, currentIndex_);
            for (int i = 0; i < numberPeriods; ++i) {
                double tauSwaption = rateTimes_.get(currentIndex_ + (i + 1) * period_)
                        - rateTimes_.get(currentIndex_ + i * period_);
                B += tauSwaption * currentState.discountRatio(
                        currentIndex_ + (i + 1) * period_, currentIndex_);
            }

            double swapRate = (P0 - Pn) / B;

            double swaptionValue = swapPayOffs_.get(productIndex_).value(swapRate);
            swaptionValue *= B;

            if (swaptionValue > 0) {
                numberCashFlowsThisStep.set(productIndex_ + numberBigFRAs_, 1);
                genCashFlows.get(productIndex_ + numberBigFRAs_).get(0).amount = swaptionValue;
                genCashFlows.get(productIndex_ + numberBigFRAs_).get(0).timeIndex =
                        productIndex_ + numberBigFRAs_;
            }

            ++productIndex_;
        }

        ++currentIndex_;

        return (productIndex_ >= numberBigFRAs_);
    }

    @Override
    public MultiStepPeriodCapletSwaptions copy() {
        MultiStepPeriodCapletSwaptions result = new MultiStepPeriodCapletSwaptions(
                rateTimes_, forwardOptionPaymentTimes_, swaptionPaymentTimes_,
                forwardPayOffs_, swapPayOffs_, period_, offset_);
        result.currentIndex_ = currentIndex_;
        result.productIndex_ = productIndex_;
        return result;
    }
}
