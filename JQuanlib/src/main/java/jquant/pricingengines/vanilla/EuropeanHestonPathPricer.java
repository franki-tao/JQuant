package jquant.pricingengines.vanilla;

import jquant.Option;
import jquant.methods.montecarlo.MultiPath;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.methods.montecarlo.Path;
import jquant.pricingengines.PlainVanillaPayoff;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for European options under the Heston model.
 *
 * <p>Port of QuantLib {@code EuropeanHestonPathPricer} from
 * {@code ql/pricingengines/vanilla/mceuropeanhestonengine.hpp}. The
 * equity price is the first component of the multi path.
 */
public class EuropeanHestonPathPricer implements MultiPathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;

    public EuropeanHestonPathPricer(Option.Type type, double strike,
                                    double discount) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
    }

    @Override
    public double price(MultiPath multiPath) {
        Path path = multiPath.get(0);
        int n = multiPath.pathSize();
        QL_REQUIRE(n > 0, "the path cannot be empty");

        return payoff_.value(path.back()) * discount_;
    }
}
