package jquant.models.marketmodels.products;

import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multiproductonestep.hpp/.cpp} port.
 *
 * <p>Single-step market-model product: the abstract base class for products
 * which can be evaluated in one step (aka Rebonato's very long jump).
 */
public abstract class MultiProductOneStep extends MarketModelMultiProduct {

    protected List<Double> rateTimes_;
    protected EvolutionDescription evolution_;

    protected MultiProductOneStep(List<Double> rateTimes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        QL_REQUIRE(rateTimes_.size() > 1,
                "Rate times must contain at least two values");
        List<Double> evolutionTimes = new ArrayList<>();
        evolutionTimes.add(rateTimes_.get(rateTimes_.size() - 2));
        List<int[]> relevanceRates = new ArrayList<>();
        relevanceRates.add(new int[]{0, rateTimes_.size() - 1});

        evolution_ = new EvolutionDescription(rateTimes_, evolutionTimes,
                relevanceRates);
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        // Terminal measure
        List<Integer> numeraires = new ArrayList<>();
        numeraires.add(rateTimes_.size() - 1);
        return numeraires;
    }
}
