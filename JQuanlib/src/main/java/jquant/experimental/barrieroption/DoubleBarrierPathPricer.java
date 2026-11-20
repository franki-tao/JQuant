package jquant.experimental.barrieroption;

import jquant.DoubleBarrier;
import jquant.Option;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Path pricer for double-barrier options.
 *
 * <p>Port of QuantLib {@code DoubleBarrierPathPricer} from
 * {@code ql/experimental/barrieroption/mcdoublebarrierengine.{hpp,cpp}}.
 */
public class DoubleBarrierPathPricer implements PathPricer {
    private final DoubleBarrier.Type barrierType_;
    private final double barrierLow_;
    private final double barrierHigh_;
    private final double rebate_;
    private final PlainVanillaPayoff payoff_;
    private final List<Double> discounts_;

    public DoubleBarrierPathPricer(DoubleBarrier.Type barrierType,
                                   double barrierLow,
                                   double barrierHigh,
                                   double rebate,
                                   Option.Type type,
                                   double strike,
                                   List<Double> discounts) {
        barrierType_ = barrierType;
        barrierLow_ = barrierLow;
        barrierHigh_ = barrierHigh;
        rebate_ = rebate;
        payoff_ = new PlainVanillaPayoff(type, strike);
        discounts_ = discounts;

        QL_REQUIRE(strike >= 0.0,
                "strike less than zero not allowed");
        QL_REQUIRE(barrierLow > 0.0,
                "low barrier less/equal zero not allowed");
        QL_REQUIRE(barrierHigh > 0.0,
                "high barrier less/equal zero not allowed");
    }

    @Override
    public double price(Path path) {
        final int nullNode = -1;
        int n = path.length();
        QL_REQUIRE(n > 1, "the path cannot be empty");

        boolean isOptionActive = false;
        int knockNode = nullNode;
        double terminal_price = path.back();
        double new_asset_price;
        int i;

        switch (barrierType_) {
            case KnockOut:
                isOptionActive = true;
                for (i = 0; i < n - 1; i++) {
                    new_asset_price = path.get(i + 1);

                    if (new_asset_price >= barrierHigh_
                            || new_asset_price <= barrierLow_) {
                        isOptionActive = false;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                        break;
                    }
                }
                break;
            case KnockIn:
                isOptionActive = false;
                for (i = 0; i < n - 1; i++) {
                    new_asset_price = path.get(i + 1);

                    if (new_asset_price >= barrierHigh_
                            || new_asset_price <= barrierLow_) {
                        isOptionActive = true;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                        break;
                    }
                }
                break;
            default:
                QL_FAIL("unknown barrier type");
        }

        if (isOptionActive) {
            return payoff_.value(terminal_price)
                    * discounts_.get(discounts_.size() - 1);
        } else {
            switch (barrierType_) {
                case KnockOut:
                    return rebate_ * discounts_.get(knockNode);
                case KnockIn:
                    return rebate_ * discounts_.get(discounts_.size() - 1);
                default:
                    QL_FAIL("unknown barrier type");
                    return 0.0;
            }
        }
    }
}
