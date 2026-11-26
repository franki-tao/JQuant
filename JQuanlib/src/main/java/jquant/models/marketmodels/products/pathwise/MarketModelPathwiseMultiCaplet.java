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
 * {@code ql/models/marketmodels/products/pathwise/pathwiseproductcaplet.hpp/.cpp}
 * port (the two caplet products; the {@code MarketModelPathwiseMultiDeflatedCap}
 * from the same file is not ported).
 *
 * <p>Market-model pathwise caplet; essentially a test class since we have
 * better ways of computing Greeks of caplets. Used in
 * {@code MarketModelTest::testPathwiseGreeks}.
 */
public class MarketModelPathwiseMultiCaplet extends MarketModelPathwiseMultiProduct {

    private final List<Double> rateTimes_;
    private final List<Double> accruals_;
    private final List<Double> paymentTimes_;
    private final List<Double> strikes_;
    private final int numberRates_;

    // things that vary in a path
    private int currentIndex_;

    private final EvolutionDescription evolution_;

    public MarketModelPathwiseMultiCaplet(final List<Double> rateTimes,
                                          final List<Double> accruals,
                                          final List<Double> paymentTimes,
                                          final List<Double> strikes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        accruals_ = new ArrayList<>(accruals);
        paymentTimes_ = new ArrayList<>(paymentTimes);
        strikes_ = new ArrayList<>(strikes);
        numberRates_ = accruals_.size();

        Utilities.checkIncreasingTimes(rateTimes);
        Utilities.checkIncreasingTimes(paymentTimes);
        List<Double> evolTimes = new ArrayList<>(rateTimes_);
        evolTimes.remove(evolTimes.size() - 1);

        QL_REQUIRE(evolTimes.size() == numberRates_,
                "rateTimes.size()<> numberOfRates+1");

        QL_REQUIRE(paymentTimes.size() == numberRates_,
                "paymentTimes.size()<> numberOfRates");

        QL_REQUIRE(accruals.size() == numberRates_,
                "accruals.size()<> numberOfRates");

        QL_REQUIRE(strikes.size() == numberRates_,
                "strikes.size()<> numberOfRates");

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
        double liborRate = currentState.forwardRate(currentIndex_);
        cashFlowsGenerated.get(currentIndex_).get(0).timeIndex = currentIndex_;
        cashFlowsGenerated.get(currentIndex_).get(0).amount.set(0,
                (liborRate - strikes_.get(currentIndex_)) * accruals_.get(currentIndex_));

        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);

        if (cashFlowsGenerated.get(currentIndex_).get(0).amount.get(0) > 0) {
            numberCashFlowsThisStep.set(currentIndex_, 1);
            for (int i = 1; i <= numberRates_; ++i)
                cashFlowsGenerated.get(currentIndex_).get(0).amount.set(i, 0.0);

            cashFlowsGenerated.get(currentIndex_).get(0).amount.set(currentIndex_ + 1,
                    accruals_.get(currentIndex_));
        }
        ++currentIndex_;
        return (currentIndex_ == strikes_.size());
    }

    @Override
    public MarketModelPathwiseMultiCaplet copy() {
        MarketModelPathwiseMultiCaplet result = new MarketModelPathwiseMultiCaplet(
                rateTimes_, accruals_, paymentTimes_, strikes_);
        result.currentIndex_ = currentIndex_;
        return result;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        List<Integer> numeraires = new ArrayList<>(numberRates_);
        for (int i = 0; i < numberRates_; ++i)
            numeraires.add(i + 1);

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
