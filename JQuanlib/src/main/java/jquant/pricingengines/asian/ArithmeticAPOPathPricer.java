package jquant.pricingengines.asian;

import jquant.Option;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for discrete arithmetic average-price Asian options.
 *
 * <p>Port of {@code ArithmeticAPOPathPricer} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_price.hpp}.
 */
public class ArithmeticAPOPathPricer implements PathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final double runningSum_;
    private final int pastFixings_;

    public ArithmeticAPOPathPricer(Option.Type type,
                                   double strike,
                                   double discount,
                                   double runningSum,
                                   int pastFixings) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        runningSum_ = runningSum;
        pastFixings_ = pastFixings;
        QL_REQUIRE(strike >= 0.0, "strike less than zero not allowed");
    }

    public ArithmeticAPOPathPricer(Option.Type type,
                                   double strike,
                                   double discount) {
        this(type, strike, discount, 0.0, 0);
    }

    @Override
    public double price(Path path) {
        final int n = path.length();
        QL_REQUIRE(n > 1, "the path cannot be empty");

        double sum;
        int fixings;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            // include initial fixing
            sum = runningSum_;
            for (int i = 0; i < n; i++) {
                sum += path.get(i);
            }
            fixings = pastFixings_ + n;
        } else {
            sum = runningSum_;
            for (int i = 1; i < n; i++) {
                sum += path.get(i);
            }
            fixings = pastFixings_ + n - 1;
        }
        final double averagePrice = sum / fixings;
        return discount_ * payoff_.value(averagePrice);
    }
}
