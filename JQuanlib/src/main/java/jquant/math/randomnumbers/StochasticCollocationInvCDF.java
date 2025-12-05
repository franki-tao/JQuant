package jquant.math.randomnumbers;

import jquant.math.Array;
import jquant.math.Function;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.integrals.GaussHermiteIntegration;
import jquant.math.interpolations.LagrangeInterpolation;
import jquant.math.randomnumbers.impl.RandomUtil;

import static jquant.math.MathUtils.M_SQRT2;

/**
 * ! Stochastic collocation inverse cumulative distribution function
 * ! References:
 * L.A. Grzelak, J.A.S. Witteveen, M.Suárez-Taboada, C.W. Oosterlee,
 * The Stochastic Collocation Monte Carlo Sampler: Highly efficient
 * sampling from “expensive” distributions
 * http://papers.ssrn.com/sol3/papers.cfm?abstract_id=2529691
 */
public class StochasticCollocationInvCDF {
    private final Array x_;
    private final double sigma_;
    private final Array y_;
    private final LagrangeInterpolation interpl_;

    public StochasticCollocationInvCDF(final Function invCDF,
                                       int lagrangeOrder,
                                       double pMax,
                                       double pMin) {
        x_ = new Array(new GaussHermiteIntegration(lagrangeOrder).x().mutiply(M_SQRT2));
        sigma_ = (!Double.isNaN(pMax))
                ? x_.back() / new InverseCumulativeNormal().value(pMax)
                : (!Double.isNaN(pMin))
                ? (x_.front() / new InverseCumulativeNormal().value(pMin))
                : 1.0;
        y_ = RandomUtil.g(sigma_,x_,invCDF);
        interpl_ = new LagrangeInterpolation(x_.toArray(), y_.toArray());
    }

    public double value_(double x) {
        return interpl_.value(x*sigma_, true);
    }

    public double value(double u) {
        return value_(new InverseCumulativeNormal().value(u));
    }
}
