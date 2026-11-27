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
 * {@code ql/models/marketmodels/products/pathwise/pathwiseproductswaption.hpp/.cpp}
 * port of {@code MarketModelPathwiseCoterminalSwaptionsDeflated}.
 *
 * <p>Main use is to test market pathwise vegas. The swaptions are payers and
 * co-terminal.
 */
public class MarketModelPathwiseCoterminalSwaptionsDeflated
        extends MarketModelPathwiseMultiProduct {

    private final List<Double> rateTimes_;
    private final List<Double> strikes_;
    private final int numberRates_;
    // things that vary in a path
    private int currentIndex_;

    private final EvolutionDescription evolution_;

    public MarketModelPathwiseCoterminalSwaptionsDeflated(
            final List<Double> rateTimes,
            final List<Double> strikes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        strikes_ = new ArrayList<>(strikes);
        numberRates_ = rateTimes_.size() - 1;

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
                cashFlowsGenerated.get(currentIndex_).get(0).amount.set(k + 1,
                        (rateTimes_.get(k + 1) - rateTimes_.get(k))
                                * currentState.discountRatio(k + 1, currentIndex_));

                double multiplier = -(rateTimes_.get(k + 1) - rateTimes_.get(k))
                        * currentState.discountRatio(k + 1, k);

                for (int l = k; l < numberRates_; ++l)
                    cashFlowsGenerated.get(currentIndex_).get(0).amount.set(k + 1,
                            cashFlowsGenerated.get(currentIndex_).get(0).amount.get(k + 1)
                                    + (currentState.forwardRate(l)
                                    - strikes_.get(currentIndex_))
                                    * (rateTimes_.get(l + 1) - rateTimes_.get(l))
                                    * multiplier
                                    * currentState.discountRatio(l + 1,
                                    currentIndex_));
            }
        }
        ++currentIndex_;
        return (currentIndex_ == strikes_.size());
    }

    @Override
    public MarketModelPathwiseCoterminalSwaptionsDeflated copy() {
        MarketModelPathwiseCoterminalSwaptionsDeflated result =
                new MarketModelPathwiseCoterminalSwaptionsDeflated(
                        rateTimes_, strikes_);
        result.currentIndex_ = currentIndex_;
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
