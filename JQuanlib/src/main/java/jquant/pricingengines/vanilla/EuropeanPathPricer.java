package jquant.pricingengines.vanilla;

import jquant.Option;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for European options.
 *
 * <p>Port of QuantLib {@code EuropeanPathPricer} from
 * {@code ql/pricingengines/vanilla/mceuropeanengine.hpp}.
 */
public class EuropeanPathPricer implements PathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;

    public EuropeanPathPricer(Option.Type type, double strike, double discount) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
    }

    @Override
    public double price(Path path) {
        QL_REQUIRE(!path.empty(), "the path cannot be empty");
        return payoff_.value(path.back()) * discount_;
    }
}
