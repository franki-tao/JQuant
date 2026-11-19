package jquant.pricingengines.asian;

import jquant.Option;
import jquant.math.MathUtils;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for discrete arithmetic average-strike Asian options.
 *
 * <p>Port of {@code ArithmeticASOPathPricer} from
 * {@code ql/pricingengines/asian/mc_discr_arith_av_strike.hpp}.
 */
public class ArithmeticASOPathPricer implements PathPricer {
    private final Option.Type type_;
    private final double discount_;
    private final double runningSum_;
    private final int pastFixings_;
    private final int fixingCount_;

    public ArithmeticASOPathPricer(Option.Type type,
                                   double discount,
                                   double runningSum,
                                   int pastFixings,
                                   int fixingCount) {
        type_ = type;
        discount_ = discount;
        runningSum_ = runningSum;
        pastFixings_ = pastFixings;
        fixingCount_ = fixingCount;
    }

    public ArithmeticASOPathPricer(Option.Type type,
                                   double discount) {
        this(type, discount, 0.0, 0, MathUtils.NULL_SIZE);
    }

    @Override
    public double price(Path path) {
        final int n = path.length();
        QL_REQUIRE(n > 1, "the path cannot be empty");

        // When fixingCount_ is set, the path may include an extra point
        // at the exercise date beyond the last fixing date. Average only
        // over the fixing points; use path.back() for the spot at exercise.
        final int nFixings = (fixingCount_ != MathUtils.NULL_SIZE)
                ? fixingCount_ : n;
        QL_REQUIRE(nFixings <= n, "fixingCount (" + nFixings
                + ") exceeds path length (" + n + ")");

        double averageStrike;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            // include initial fixing (T=0 is a fixing date)
            double sum = runningSum_;
            for (int i = 0; i < nFixings; i++) {
                sum += path.get(i);
            }
            averageStrike = sum / (pastFixings_ + nFixings);
        } else {
            // first path point is T=0 (not a fixing), skip it
            double sum = runningSum_;
            for (int i = 1; i < nFixings; i++) {
                sum += path.get(i);
            }
            averageStrike = sum / (pastFixings_ + nFixings - 1);
        }

        return discount_ * new PlainVanillaPayoff(type_, averageStrike)
                .value(path.back());
    }
}
