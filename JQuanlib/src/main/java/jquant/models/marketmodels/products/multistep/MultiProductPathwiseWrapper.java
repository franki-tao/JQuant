package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.MarketModelPathwiseMultiProduct;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/multisteppathwisewrapper.hpp/.cpp} port.
 *
 * <p>Pathwise products do everything that ordinary products do and more.
 * This lets you treat a pathwise product as an ordinary product.
 * Tested in MarketModels::testInverseFloater().
 */
public class MultiProductPathwiseWrapper extends MarketModelMultiProduct {

    private final MarketModelPathwiseMultiProduct innerProduct_;
    private final List<List<MarketModelPathwiseMultiProduct.CashFlow>> cashFlowsGenerated_;
    private final int numberOfProducts_;

    public MultiProductPathwiseWrapper(final MarketModelPathwiseMultiProduct innerProduct) {
        innerProduct_ = innerProduct.copy();
        numberOfProducts_ = innerProduct.numberOfProducts();
        cashFlowsGenerated_ = new ArrayList<>();
        for (int i = 0; i < numberOfProducts_; ++i) {
            List<MarketModelPathwiseMultiProduct.CashFlow> cashFlows = new ArrayList<>();
            for (int j = 0; j < innerProduct.maxNumberOfCashFlowsPerProductPerStep(); ++j) {
                MarketModelPathwiseMultiProduct.CashFlow cashFlow =
                        new MarketModelPathwiseMultiProduct.CashFlow();
                cashFlow.amount = new ArrayList<>();
                for (int k = 0; k < 1 + innerProduct.evolution().numberOfRates(); ++k)
                    cashFlow.amount.add(0.0);
                cashFlows.add(cashFlow);
            }
            cashFlowsGenerated_.add(cashFlows);
        }
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return innerProduct_.possibleCashFlowTimes();
    }

    @Override
    public int numberOfProducts() {
        return innerProduct_.numberOfProducts();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return innerProduct_.maxNumberOfCashFlowsPerProductPerStep();
    }

    @Override
    public void reset() {
        innerProduct_.reset();
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated) {
        boolean done = innerProduct_.nextTimeStep(currentState, numberCashFlowsThisStep,
                cashFlowsGenerated_);

        // transform the data
        for (int i = 0; i < numberOfProducts_; ++i)
            for (int j = 0; j < numberCashFlowsThisStep.get(i); ++j) {
                cashFlowsGenerated.get(i).get(j).timeIndex =
                        cashFlowsGenerated_.get(i).get(j).timeIndex;
                cashFlowsGenerated.get(i).get(j).amount =
                        cashFlowsGenerated_.get(i).get(j).amount.get(0);
            }

        return done;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        return innerProduct_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return innerProduct_.evolution();
    }

    @Override
    public MultiProductPathwiseWrapper copy() {
        return new MultiProductPathwiseWrapper(innerProduct_.copy());
    }
}
