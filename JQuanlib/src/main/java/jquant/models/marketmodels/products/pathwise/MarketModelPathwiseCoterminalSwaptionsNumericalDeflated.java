package jquant.models.marketmodels.products.pathwise;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/pathwise/pathwiseproductswaption.hpp/.cpp}
 * port of {@code MarketModelPathwiseCoterminalSwaptionsNumericalDeflated}.
 *
 * <p>Easiest way to test
 * {@code MarketModelPathwiseCoterminalSwaptionsDeflated} is by doing a
 * numerical differentiation version.
 */
public class MarketModelPathwiseCoterminalSwaptionsNumericalDeflated
        extends MarketModelPathwiseMultiProduct {

    private final List<Double> rateTimes_;
    private final List<Double> strikes_;
    private final int numberRates_;
    // things that vary in a path
    private int currentIndex_;

    private final EvolutionDescription evolution_;

    private final double bumpSize_;
    private LMMCurveState up_;
    private LMMCurveState down_;
    private List<Double> forwards_;

    public MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(
            final List<Double> rateTimes,
            final List<Double> strikes,
            double bumpSize) {
        rateTimes_ = new ArrayList<>(rateTimes);
        strikes_ = new ArrayList<>(strikes);
        numberRates_ = rateTimes_.size() - 1;
        bumpSize_ = bumpSize;
        up_ = new LMMCurveState(rateTimes_);
        down_ = new LMMCurveState(rateTimes_);
        forwards_ = new ArrayList<>();
        for (int i = 0; i < numberRates_; ++i)
            forwards_.add(0.0);

        Utilities.checkIncreasingTimes(rateTimes_);
        List<Double> evolTimes = new ArrayList<>(rateTimes_);
        evolTimes.remove(evolTimes.size() - 1);

        QL_REQUIRE(evolTimes.size() == numberRates_,
                "rateTimes.size()<> numberOfRates+1");

        QL_REQUIRE(strikes_.size() == numberRates_,
                "strikes.size()<> numberOfRates");

        evolution_ = new EvolutionDescription(rateTimes_, evolTimes);
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public boolean nextTimeStep(
            CurveState currentState,
            List<Integer> numberCashFlowsThisStep,
            List<List<MarketModelPathwiseMultiProduct.CashFlow>> cashFlowsGenerated) {
        double swapRate = currentState.coterminalSwapRate(currentIndex_);
        cashFlowsGenerated.get(currentIndex_).get(0).timeIndex = currentIndex_;

        double annuity =
                currentState.coterminalSwapAnnuity(currentIndex_, currentIndex_);
        cashFlowsGenerated.get(currentIndex_).get(0).amount.set(0,
                (swapRate - strikes_.get(currentIndex_)) * annuity);

        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);

        if (cashFlowsGenerated.get(currentIndex_).get(0).amount.get(0) > 0) {
            numberCashFlowsThisStep.set(currentIndex_, 1);
            for (int i = 1; i <= numberRates_; ++i)
                cashFlowsGenerated.get(currentIndex_).get(0).amount.set(i, 0.0);

            for (int k = currentIndex_; k < numberRates_; ++k) {
                // official assigns the curve-state vector by value; copy so the
                // bump does not corrupt the evolver's curve state.
                forwards_ = new ArrayList<>(currentState.forwardRates());
                forwards_.set(k, forwards_.get(k) + bumpSize_);
                up_.setOnForwardRates(forwards_);

                forwards_.set(k, forwards_.get(k) - bumpSize_);
                forwards_.set(k, forwards_.get(k) - bumpSize_);
                down_.setOnForwardRates(forwards_);

                double upSR = up_.coterminalSwapRate(currentIndex_);
                double upAnnuity =
                        up_.coterminalSwapAnnuity(currentIndex_, currentIndex_);
                double upValue = (upSR - strikes_.get(currentIndex_)) * upAnnuity;

                double downSR = down_.coterminalSwapRate(currentIndex_);
                double downAnnuity =
                        down_.coterminalSwapAnnuity(currentIndex_, currentIndex_);
                double downValue =
                        (downSR - strikes_.get(currentIndex_)) * downAnnuity;

                double deriv = (upValue - downValue) / (2.0 * bumpSize_);

                cashFlowsGenerated.get(currentIndex_).get(0).amount.set(k + 1,
                        deriv);
            }
        }
        ++currentIndex_;
        return (currentIndex_ == strikes_.size());
    }

    @Override
    public MarketModelPathwiseCoterminalSwaptionsNumericalDeflated copy() {
        MarketModelPathwiseCoterminalSwaptionsNumericalDeflated result =
                new MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(
                        rateTimes_, strikes_, bumpSize_);
        result.currentIndex_ = currentIndex_;
        result.up_ = up_.copy();
        result.down_ = down_.copy();
        result.forwards_ = new ArrayList<>(forwards_);
        return result;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        List<Integer> numeraires = new ArrayList<>(numberRates_);
        for (int i = 0; i < numberRates_; ++i)
            numeraires.add(i);

        return numeraires;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return rateTimes_;
    }

    @Override
    public int numberOfProducts() {
        return numberRates_;
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
