package jquant.models.marketmodels.products;

import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/compositeproduct.hpp/.cpp} port.
 */
public abstract class MarketModelComposite extends MarketModelMultiProduct {

    protected static class SubProduct {
        MarketModelMultiProduct product;
        double multiplier;
        List<Integer> numberOfCashflows;
        List<List<CashFlow>> cashflows;
        List<Integer> timeIndices;
        boolean done;
    }

    protected final List<SubProduct> components_ = new ArrayList<>();
    // common evolution data
    protected List<Double> rateTimes_ = new ArrayList<>();
    // Official default-constructed vector; Java must not leave this null
    // because mergeTimes appends into it.
    protected List<Double> evolutionTimes_ = new ArrayList<>();
    protected EvolutionDescription evolution_;
    // working variables
    protected boolean finalized_ = false;
    protected int currentIndex_;
    protected List<Double> cashflowTimes_ = new ArrayList<>();
    protected List<List<Double>> allEvolutionTimes_ = new ArrayList<>();
    protected List<boolean[]> isInSubset_ = new ArrayList<>();

    @Override
    public EvolutionDescription evolution() {
        QL_REQUIRE(finalized_, "composite not finalized");
        return evolution_;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        QL_REQUIRE(finalized_, "composite not finalized");
        return EvolutionDescription.terminalMeasure(evolution_);
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        QL_REQUIRE(finalized_, "composite not finalized");
        return cashflowTimes_;
    }

    @Override
    public void reset() {
        for (SubProduct component : components_) {
            component.product.reset();
            component.done = false;
        }
        currentIndex_ = 0;
    }

    public void add(MarketModelMultiProduct product, double multiplier) {
        QL_REQUIRE(!finalized_, "product already finalized");
        EvolutionDescription d = product.evolution();
        if (!components_.isEmpty()) {
            // enforce preconditions
            EvolutionDescription d1 = components_.get(0).product.evolution();
            List<Double> rateTimes1 = d1.rateTimes();
            List<Double> rateTimes2 = d.rateTimes();
            QL_REQUIRE(rateTimes1.size() == rateTimes2.size()
                            && rateTimes1.equals(rateTimes2),
                    "incompatible rate times");
        }
        SubProduct component = new SubProduct();
        component.product = product.copy();
        component.multiplier = multiplier;
        component.done = false;
        components_.add(component);
        allEvolutionTimes_.add(d.evolutionTimes());
    }

    public void add(MarketModelMultiProduct product) {
        add(product, 1.0);
    }

    public void subtract(MarketModelMultiProduct product, double multiplier) {
        add(product, -multiplier);
    }

    public void subtract(MarketModelMultiProduct product) {
        subtract(product, 1.0);
    }

    public void finalize() {
        QL_REQUIRE(!finalized_, "product already finalized");
        QL_REQUIRE(!components_.isEmpty(), "no sub-product provided");

        // fetch the rate times from the first subproduct (we checked
        // they're all the same)
        EvolutionDescription description = components_.get(0).product.evolution();
        rateTimes_ = description.rateTimes();

        Utilities.mergeTimes(allEvolutionTimes_, evolutionTimes_, isInSubset_);

        List<Double> allCashflowTimes = new ArrayList<>();

        // now, for each subproduct...
        for (SubProduct component : components_) {
            // ...collect all possible cash-flow times...
            List<Double> cashflowTimes = component.product.possibleCashFlowTimes();
            allCashflowTimes.addAll(cashflowTimes);
            // ...allocate working vectors...
            component.numberOfCashflows = new ArrayList<>();
            for (int i = 0; i < component.product.numberOfProducts(); ++i)
                component.numberOfCashflows.add(0);
            component.cashflows = new ArrayList<>();
            for (int i = 0; i < component.product.numberOfProducts(); ++i) {
                List<CashFlow> cashFlows = new ArrayList<>();
                for (int j = 0; j < component.product.maxNumberOfCashFlowsPerProductPerStep(); ++j)
                    cashFlows.add(new CashFlow());
                component.cashflows.add(cashFlows);
            }
        }

        // all information having been collected, we can sort and
        // compact the vector of all cash-flow times...
        allCashflowTimes.sort(Double::compare);
        List<Double> uniqueCashflowTimes = new ArrayList<>();
        for (double t : allCashflowTimes) {
            if (uniqueCashflowTimes.isEmpty()
                    || t != uniqueCashflowTimes.get(uniqueCashflowTimes.size() - 1))
                uniqueCashflowTimes.add(t);
        }
        cashflowTimes_.addAll(uniqueCashflowTimes);
        // ...and map each product's cash-flow time into the total vector.
        for (SubProduct component : components_) {
            List<Double> productTimes = component.product.possibleCashFlowTimes();
            component.timeIndices = new ArrayList<>();
            for (double productTime : productTimes)
                component.timeIndices.add(indexOf(cashflowTimes_, productTime));
        }

        evolution_ = new EvolutionDescription(rateTimes_, evolutionTimes_);

        // all done.
        finalized_ = true;
    }

    public int size() {
        return components_.size();
    }

    public MarketModelMultiProduct item(int i) {
        return components_.get(i).product;
    }

    public double multiplier(int i) {
        return components_.get(i).multiplier;
    }

    private static int indexOf(List<Double> values, double value) {
        for (int i = 0; i < values.size(); ++i)
            if (values.get(i) == value)
                return i;
        return -1;
    }
}
