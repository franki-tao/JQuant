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
 * Arithmetic average-price path pricer for Heston multi paths.
 *
 * <p>Port of QuantLib {@code ArithmeticAPOHestonPathPricer} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_price_heston.hpp}.
 */
public class ArithmeticAPOHestonPathPricer implements MultiPathPricer {
    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final List<Integer> fixingIndices_;
    private final double runningSum_;
    private final int pastFixings_;

    public ArithmeticAPOHestonPathPricer(
            Option.Type type,
            double strike,
            double discount,
            List<Integer> fixingIndices,
            double runningSum,
            int pastFixings) {
        payoff_ = new PlainVanillaPayoff(type, strike);
        discount_ = discount;
        fixingIndices_ = new ArrayList<>(fixingIndices);
        runningSum_ = runningSum;
        pastFixings_ = pastFixings;
        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
    }

    @Override
    public double price(MultiPath multiPath) {
        Path path = multiPath.get(0);
        int n = multiPath.pathSize();
        QL_REQUIRE(n > 0, "the path cannot be empty");

        double sum = runningSum_;
        int fixings = pastFixings_ + fixingIndices_.size();

        for (int fixingIndice : fixingIndices_) {
            sum += path.get(fixingIndice);
        }

        double averagePrice = sum / fixings;
        return discount_ * payoff_.value(averagePrice);
    }
}
