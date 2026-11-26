package jquant.models.marketmodels.products;

import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/products/multiproductmultistep.hpp/.cpp} port.
 */
public abstract class MultiProductMultiStep extends MarketModelMultiProduct {

    protected List<Double> rateTimes_;
    protected EvolutionDescription evolution_;

    protected MultiProductMultiStep(List<Double> rateTimes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        QL_REQUIRE(rateTimes_.size() > 1,
                "Rate times must contain at least two values");
        int n = rateTimes_.size() - 1;
        List<Double> evolutionTimes = new ArrayList<>();
        List<int[]> relevanceRates = new ArrayList<>();
        for (int i = 0; i < n; ++i) {
            evolutionTimes.add(rateTimes_.get(i));
            relevanceRates.add(new int[]{i, i + 1});
        }

        evolution_ = new EvolutionDescription(rateTimes_, evolutionTimes,
                relevanceRates);
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        int n = rateTimes_.size() - 1;
        List<Integer> numeraires = new ArrayList<>();
        // MoneyMarketPlus(1)
        for (int i = 0; i < n; ++i)
            numeraires.add(i + 1);
        return numeraires;
    }
}
