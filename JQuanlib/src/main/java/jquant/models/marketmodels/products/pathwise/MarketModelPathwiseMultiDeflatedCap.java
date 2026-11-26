package jquant.models.marketmodels.products.pathwise;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/pathwise/pathwiseproductcaplet.hpp/.cpp}
 * port of {@code MarketModelPathwiseMultiDeflatedCap}.
 *
 * <p>MarketModelPathwiseMultiDeflatedCap to price several caps and get their
 * derivatives simultaneously. Mainly useful for testing pathwise market vegas
 * code.
 */
public class MarketModelPathwiseMultiDeflatedCap extends MarketModelPathwiseMultiProduct {

    private final MarketModelPathwiseMultiDeflatedCaplet underlyingCaplets_;

    private final int numberRates_;

    private final List<int[]> startsAndEnds_; // (start, end) pairs

    // things that vary in a path
    private int currentIndex_;
    private final List<Integer> innerCashFlowSizes_;
    private final List<List<MarketModelPathwiseMultiProduct.CashFlow>> innerCashFlowsGenerated_;

    public MarketModelPathwiseMultiDeflatedCap(final List<Double> rateTimes,
                                               final List<Double> accruals,
                                               final List<Double> paymentTimes,
                                               double strike,
                                               List<int[]> startsAndEnds) {
        underlyingCaplets_ = new MarketModelPathwiseMultiDeflatedCaplet(
                rateTimes, accruals, paymentTimes, strike);
        numberRates_ = accruals.size();
        startsAndEnds_ = new ArrayList<>();
        for (int[] pair : startsAndEnds)
            startsAndEnds_.add(new int[]{pair[0], pair[1]});

        for (int j = 0; j < startsAndEnds_.size(); ++j) {
            QL_REQUIRE(startsAndEnds_.get(j)[0] < startsAndEnds_.get(j)[1],
                    "a cap must start before it ends: " + j
                            + startsAndEnds_.get(j)[0] + startsAndEnds_.get(j)[1]);
            QL_REQUIRE(startsAndEnds_.get(j)[1] <= accruals.size(),
                    "a cap must end when the underlying caplets: " + j
                            + startsAndEnds_.get(j)[0] + startsAndEnds_.get(j)[1]);
        }

        innerCashFlowSizes_ = new ArrayList<>();
        for (int i = 0; i < accruals.size(); ++i)
            innerCashFlowSizes_.add(0);

        innerCashFlowsGenerated_ = new ArrayList<>();
        for (int i = 0; i < accruals.size(); ++i) {
            List<MarketModelPathwiseMultiProduct.CashFlow> cashFlows = new ArrayList<>();
            for (int j = 0;
                 j < underlyingCaplets_.maxNumberOfCashFlowsPerProductPerStep(); ++j) {
                MarketModelPathwiseMultiProduct.CashFlow cashFlow =
                        new MarketModelPathwiseMultiProduct.CashFlow();
                cashFlow.timeIndex = 0;
                cashFlow.amount = new ArrayList<>();
                for (int k = 0; k <= accruals.size(); ++k)
                    cashFlow.amount.add(0.0);
                cashFlows.add(cashFlow);
            }
            innerCashFlowsGenerated_.add(cashFlows);
        }
    }

    private MarketModelPathwiseMultiDeflatedCap(
            final MarketModelPathwiseMultiDeflatedCap source) {
        underlyingCaplets_ = source.underlyingCaplets_.copy();
        numberRates_ = source.numberRates_;
        startsAndEnds_ = new ArrayList<>();
        for (int[] pair : source.startsAndEnds_)
            startsAndEnds_.add(new int[]{pair[0], pair[1]});

        currentIndex_ = source.currentIndex_;
        innerCashFlowSizes_ = new ArrayList<>(source.innerCashFlowSizes_);
        innerCashFlowsGenerated_ = new ArrayList<>();
        for (List<MarketModelPathwiseMultiProduct.CashFlow> stepFlows
                : source.innerCashFlowsGenerated_) {
            List<MarketModelPathwiseMultiProduct.CashFlow> stepCopy = new ArrayList<>();
            for (MarketModelPathwiseMultiProduct.CashFlow cashFlow : stepFlows)
                stepCopy.add(copyCashFlow(cashFlow));
            innerCashFlowsGenerated_.add(stepCopy);
        }
    }

    private static MarketModelPathwiseMultiProduct.CashFlow copyCashFlow(
            final MarketModelPathwiseMultiProduct.CashFlow source) {
        MarketModelPathwiseMultiProduct.CashFlow result =
                new MarketModelPathwiseMultiProduct.CashFlow();
        result.timeIndex = source.timeIndex;
        result.amount = new ArrayList<>(source.amount);
        return result;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        return underlyingCaplets_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return underlyingCaplets_.evolution();
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return underlyingCaplets_.possibleCashFlowTimes();
    }

    @Override
    public int numberOfProducts() {
        return startsAndEnds_.size();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return underlyingCaplets_.maxNumberOfCashFlowsPerProductPerStep();
    }

    // has division by the numeraire already been done?
    @Override
    public boolean alreadyDeflated() {
        return underlyingCaplets_.alreadyDeflated();
    }

    @Override
    public void reset() {
        underlyingCaplets_.reset();
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(
            CurveState currentState,
            List<Integer> numberCashFlowsThisStep,
            List<List<MarketModelPathwiseMultiProduct.CashFlow>> cashFlowsGenerated) {

        boolean done = underlyingCaplets_.nextTimeStep(
                currentState, innerCashFlowSizes_, innerCashFlowsGenerated_);

        for (int k = 0; k < startsAndEnds_.size(); ++k)
            numberCashFlowsThisStep.set(k, 0);

        for (int j = 0; j < numberRates_; ++j) {
            if (innerCashFlowSizes_.get(j) > 0) {
                for (int k = 0; k < startsAndEnds_.size(); ++k) {
                    if (startsAndEnds_.get(k)[0] <= j
                            && j < startsAndEnds_.get(k)[1]) {
                        for (int l = 0; l < innerCashFlowSizes_.get(j); ++l) {
                            int count = numberCashFlowsThisStep.get(k);
                            cashFlowsGenerated.get(k).set(count,
                                    copyCashFlow(innerCashFlowsGenerated_.get(j).get(l)));
                            numberCashFlowsThisStep.set(k, count + 1);
                        }
                    }
                }
            }
        }

        return done;
    }

    @Override
    public MarketModelPathwiseMultiDeflatedCap copy() {
        return new MarketModelPathwiseMultiDeflatedCap(this);
    }
}
