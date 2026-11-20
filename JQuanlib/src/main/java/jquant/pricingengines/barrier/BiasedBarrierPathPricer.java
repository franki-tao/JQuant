package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Option;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

/**
 * Biased (no Brownian-bridge correction) path pricer for barrier options.
 *
 * <p>Port of QuantLib {@code BiasedBarrierPathPricer} from
 * {@code ql/pricingengines/barrier/mcbarrierengine.{hpp,cpp}}.
 */
public class BiasedBarrierPathPricer implements PathPricer {
    private final Barrier.Type barrierType_;
    private final double barrier_;
    private final double rebate_;
    private final PlainVanillaPayoff payoff_;
    private final List<Double> discounts_;

    public BiasedBarrierPathPricer(Barrier.Type barrierType,
                                   double barrier,
                                   double rebate,
                                   Option.Type type,
                                   double strike,
                                   List<Double> discounts) {
        barrierType_ = barrierType;
        barrier_ = barrier;
        rebate_ = rebate;
        payoff_ = new PlainVanillaPayoff(type, strike);
        discounts_ = discounts;

        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
        QL_REQUIRE(barrier > 0.0,
                "barrier less/equal zero not allowed");
    }

    @Override
    public double price(Path path) {
        int nullNode = NULL_SIZE;
        int n = path.length();
        QL_REQUIRE(n > 1, "the path cannot be empty");

        boolean isOptionActive = false;
        int knockNode = nullNode;
        double assetPrice = path.front();
        int i;

        switch (barrierType_) {
            case DownIn:
                isOptionActive = false;
                for (i = 1; i < n; i++) {
                    assetPrice = path.get(i);
                    if (assetPrice <= barrier_) {
                        isOptionActive = true;
                        if (knockNode == nullNode)
                            knockNode = i;
                    }
                }
                break;
            case UpIn:
                isOptionActive = false;
                for (i = 1; i < n; i++) {
                    assetPrice = path.get(i);
                    if (assetPrice >= barrier_) {
                        isOptionActive = true;
                        if (knockNode == nullNode)
                            knockNode = i;
                    }
                }
                break;
            case DownOut:
                isOptionActive = true;
                for (i = 1; i < n; i++) {
                    assetPrice = path.get(i);
                    if (assetPrice <= barrier_) {
                        isOptionActive = false;
                        if (knockNode == nullNode)
                            knockNode = i;
                    }
                }
                break;
            case UpOut:
                isOptionActive = true;
                for (i = 1; i < n; i++) {
                    assetPrice = path.get(i);
                    if (assetPrice >= barrier_) {
                        isOptionActive = false;
                        if (knockNode == nullNode)
                            knockNode = i;
                    }
                }
                break;
            default:
                QL_FAIL("unknown barrier type");
        }

        if (isOptionActive) {
            return payoff_.value(assetPrice) * discounts_.get(discounts_.size() - 1);
        } else {
            switch (barrierType_) {
                case UpIn:
                case DownIn:
                    return rebate_ * discounts_.get(discounts_.size() - 1);
                case UpOut:
                case DownOut:
                    return rebate_ * discounts_.get(knockNode);
                default:
                    QL_FAIL("unknown barrier type");
                    return 0.0;
            }
        }
    }
}
