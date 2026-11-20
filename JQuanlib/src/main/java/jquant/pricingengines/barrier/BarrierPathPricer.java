package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Option;
import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.randomnumbers.RandomSequenceGenerator;
import jquant.methods.montecarlo.Path;
import jquant.methods.montecarlo.PathPricer;
import jquant.pricingengines.PlainVanillaPayoff;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_SIZE;

/**
 * Path pricer for barrier options using the Brownian-bridge correction.
 *
 * <p>Port of QuantLib {@code BarrierPathPricer} from
 * {@code ql/pricingengines/barrier/mcbarrierengine.{hpp,cpp}}.
 */
public class BarrierPathPricer implements PathPricer {
    private final Barrier.Type barrierType_;
    private final double barrier_;
    private final double rebate_;
    private final StochasticProcess1D diffProcess_;
    private final RandomSequenceGenerator sequenceGen_;
    private final PlainVanillaPayoff payoff_;
    private final List<Double> discounts_;

    public BarrierPathPricer(Barrier.Type barrierType,
                             double barrier,
                             double rebate,
                             Option.Type type,
                             double strike,
                             List<Double> discounts,
                             StochasticProcess1D diffProcess,
                             RandomSequenceGenerator sequenceGen) {
        barrierType_ = barrierType;
        barrier_ = barrier;
        rebate_ = rebate;
        diffProcess_ = diffProcess;
        sequenceGen_ = sequenceGen;
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
        double newAssetPrice;
        double x, y;
        double vol;
        TimeGrid timeGrid = path.timeGrid();
        double dt;
        List<Double> u = sequenceGen_.nextSequence().value;
        int i;

        switch (barrierType_) {
            case DownIn:
                isOptionActive = false;
                for (i = 0; i < n - 1; i++) {
                    newAssetPrice = path.get(i + 1);
                    // terminal or initial vol?
                    vol = diffProcess_.diffusion(timeGrid.get(i), assetPrice);
                    dt = timeGrid.dt(i);

                    x = Math.log(newAssetPrice / assetPrice);
                    y = 0.5 * (x - Math.sqrt(x * x - 2 * vol * vol * dt
                            * Math.log(u.get(i))));
                    y = assetPrice * Math.exp(y);
                    if (y <= barrier_) {
                        isOptionActive = true;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                    }
                    assetPrice = newAssetPrice;
                }
                break;
            case UpIn:
                isOptionActive = false;
                for (i = 0; i < n - 1; i++) {
                    newAssetPrice = path.get(i + 1);
                    // terminal or initial vol?
                    vol = diffProcess_.diffusion(timeGrid.get(i), assetPrice);
                    dt = timeGrid.dt(i);

                    x = Math.log(newAssetPrice / assetPrice);
                    y = 0.5 * (x + Math.sqrt(x * x - 2 * vol * vol * dt
                            * Math.log(1 - u.get(i))));
                    y = assetPrice * Math.exp(y);
                    if (y >= barrier_) {
                        isOptionActive = true;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                    }
                    assetPrice = newAssetPrice;
                }
                break;
            case DownOut:
                isOptionActive = true;
                for (i = 0; i < n - 1; i++) {
                    newAssetPrice = path.get(i + 1);
                    // terminal or initial vol?
                    vol = diffProcess_.diffusion(timeGrid.get(i), assetPrice);
                    dt = timeGrid.dt(i);

                    x = Math.log(newAssetPrice / assetPrice);
                    y = 0.5 * (x - Math.sqrt(x * x - 2 * vol * vol * dt
                            * Math.log(u.get(i))));
                    y = assetPrice * Math.exp(y);
                    if (y <= barrier_) {
                        isOptionActive = false;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                    }
                    assetPrice = newAssetPrice;
                }
                break;
            case UpOut:
                isOptionActive = true;
                for (i = 0; i < n - 1; i++) {
                    newAssetPrice = path.get(i + 1);
                    // terminal or initial vol?
                    vol = diffProcess_.diffusion(timeGrid.get(i), assetPrice);
                    dt = timeGrid.dt(i);

                    x = Math.log(newAssetPrice / assetPrice);
                    y = 0.5 * (x + Math.sqrt(x * x - 2 * vol * vol * dt
                            * Math.log(1 - u.get(i))));
                    y = assetPrice * Math.exp(y);
                    if (y >= barrier_) {
                        isOptionActive = false;
                        if (knockNode == nullNode)
                            knockNode = i + 1;
                    }
                    assetPrice = newAssetPrice;
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
