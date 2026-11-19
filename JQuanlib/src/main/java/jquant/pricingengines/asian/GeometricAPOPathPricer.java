package jquant.pricingengines.asian;

import jquant.Option;
import jquant.math.MathUtils;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for discrete geometric average-price Asian options.
 *
 * <p>Port of {@code GeometricAPOPathPricer} from
 * {@code ql/pricingengines/asian/mc_discr_geom_av_price.hpp}.
 */
public class GeometricAPOPathPricer implements PathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final double runningProduct_;
    private final int pastFixings_;

    public GeometricAPOPathPricer(Option.Type type,
                                  double strike,
                                  double discount,
                                  double runningProduct,
                                  int pastFixings) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        runningProduct_ = runningProduct;
        pastFixings_ = pastFixings;
        QL_REQUIRE(strike >= 0.0, "negative strike given");
    }

    public GeometricAPOPathPricer(Option.Type type,
                                  double strike,
                                  double discount) {
        this(type, strike, discount, 1.0, 0);
    }

    @Override
    public double price(Path path) {
        final int n = path.length() - 1;
        QL_REQUIRE(n > 0, "the path cannot be empty");

        double product = runningProduct_;
        int fixings = n + pastFixings_;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            fixings += 1;
            product *= path.front();
        }
        // care must be taken not to overflow product
        final double maxValue = MathUtils.QL_MAX_REAL;
        double averagePrice = 1.0;
        for (int i = 1; i < n + 1; i++) {
            final double price = path.get(i);
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
