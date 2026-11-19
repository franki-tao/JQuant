package jquant.pricingengines.asian;

import jquant.Option;
import jquant.methods.montecarlo.MultiPath;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.methods.montecarlo.Path;
import jquant.pricingengines.PlainVanillaPayoff;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Geometric average-price path pricer for Heston multi paths.
 *
 * <p>Port of QuantLib {@code GeometricAPOHestonPathPricer} from
 * {@code ql/pricingengines/asian/mc_discr_geom_av_price_heston.hpp}.
 */
public class GeometricAPOHestonPathPricer implements MultiPathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final List<Integer> fixingIndices_;
    private final double runningProduct_;
    private final int pastFixings_;

    public GeometricAPOHestonPathPricer(
            Option.Type type,
            double strike,
            double discount,
            List<Integer> fixingIndices,
            double runningProduct,
            int pastFixings) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        fixingIndices_ = new ArrayList<>(fixingIndices);
        runningProduct_ = runningProduct;
        pastFixings_ = pastFixings;
        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
    }

    public GeometricAPOHestonPathPricer(
            Option.Type type,
            double strike,
            double discount,
            List<Integer> fixingIndices) {
        this(type, strike, discount, fixingIndices, 1.0, 0);
    }

    @Override
    public double price(MultiPath multiPath) {
        Path path = multiPath.get(0);
        int n = multiPath.pathSize();
        QL_REQUIRE(n > 0, "the path cannot be empty");

        double averagePrice = 1.0;
        double product = runningProduct_;
        int fixings = pastFixings_ + fixingIndices_.size();

        final double maxValue = Double.MAX_VALUE;
        for (int fixingIndice : fixingIndices_) {
            double price = path.get(fixingIndice);
            if (product < maxValue / price) {
                product *= price;
            } else {
                averagePrice *= Math.pow(product, 1.0 / fixings);
                product = price;
            }
        }

        averagePrice *= Math.pow(product, 1.0 / fixings);
        return discount_ * payoff_.value(averagePrice);
    }
}
