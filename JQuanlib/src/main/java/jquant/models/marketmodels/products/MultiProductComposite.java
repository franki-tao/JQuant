package jquant.models.marketmodels.products;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelMultiProduct;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multiproductcomposite.hpp/.cpp} port.
 */
public class MultiProductComposite extends MarketModelComposite {

    @Override
    public int numberOfProducts() {
        int result = 0;
        for (SubProduct component : components_)
            result += component.product.numberOfProducts();
        return result;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        int result = 0;
        for (SubProduct component : components_)
            result = Math.max(result, component.product.maxNumberOfCashFlowsPerProductPerStep());
        return result;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated) {
        QL_REQUIRE(finalized_, "composite not finalized");
        boolean done = true;
        int n = 0;
        int offset = 0;
        // for each sub-product...
        for (SubProduct component : components_) {
            if (isInSubset_.get(n)[currentIndex_] && !component.done) {
                // ...make it evolve...
                boolean thisDone = component.product.nextTimeStep(currentState,
                        component.numberOfCashflows,
                        component.cashflows);
                // ...and copy the results. Time indices need to be remapped
                // so that they point into all cash-flow times. Amounts need
                // to be adjusted by the corresponding multiplier.
                for (int j = 0; j < component.product.numberOfProducts(); ++j) {
                    numberCashFlowsThisStep.set(j + offset,
                            component.numberOfCashflows.get(j));
                    for (int k = 0; k < component.numberOfCashflows.get(j); ++k) {
                        MarketModelMultiProduct.CashFlow from = component.cashflows.get(j).get(k);
                        MarketModelMultiProduct.CashFlow to =
                                cashFlowsGenerated.get(j + offset).get(k);
                        to.timeIndex = component.timeIndices.get(from.timeIndex);
                        to.amount = from.amount * component.multiplier;
                    }
                }
                // finally, set done to false if this product isn't done
                done = done && thisDone;
            } else {
                for (int j = 0; j < component.product.numberOfProducts(); ++j)
                    numberCashFlowsThisStep.set(j + offset, 0);
            }

            // the offset is updated whether or not the product was evolved
            offset += component.product.numberOfProducts();
            ++n;
        }
        ++currentIndex_;
        return done;
    }

    @Override
    public MarketModelMultiProduct copy() {
        MultiProductComposite result = new MultiProductComposite();
        result.finalized_ = finalized_;
        result.currentIndex_ = currentIndex_;
        result.rateTimes_ = rateTimes_ == null ? null : new ArrayList<>(rateTimes_);
        result.evolutionTimes_ = evolutionTimes_ == null ? null : new ArrayList<>(evolutionTimes_);
        result.evolution_ = evolution_;
        result.cashflowTimes_ = new ArrayList<>(cashflowTimes_);
        result.allEvolutionTimes_ = new ArrayList<>();
        for (List<Double> times : allEvolutionTimes_)
            result.allEvolutionTimes_.add(new ArrayList<>(times));
        result.isInSubset_ = new ArrayList<>();
        for (boolean[] present : isInSubset_)
            result.isInSubset_.add(present.clone());
        for (SubProduct component : components_) {
            SubProduct copy = new SubProduct();
            copy.product = component.product.copy();
            copy.multiplier = component.multiplier;
            copy.numberOfCashflows = component.numberOfCashflows == null
                    ? null : new ArrayList<>(component.numberOfCashflows);
            if (component.cashflows == null) {
                copy.cashflows = null;
            } else {
                copy.cashflows = new ArrayList<>();
                for (List<MarketModelMultiProduct.CashFlow> cashFlows : component.cashflows) {
                    List<MarketModelMultiProduct.CashFlow> cashFlowCopies = new ArrayList<>();
                    for (MarketModelMultiProduct.CashFlow cashFlow : cashFlows) {
                        MarketModelMultiProduct.CashFlow cashFlowCopy =
                                new MarketModelMultiProduct.CashFlow();
                        cashFlowCopy.timeIndex = cashFlow.timeIndex;
                        cashFlowCopy.amount = cashFlow.amount;
                        cashFlowCopies.add(cashFlowCopy);
                    }
                    copy.cashflows.add(cashFlowCopies);
                }
            }
            copy.timeIndices = component.timeIndices == null
                    ? null : new ArrayList<>(component.timeIndices);
            copy.done = component.done;
            result.components_.add(copy);
        }
        return result;
    }
}
