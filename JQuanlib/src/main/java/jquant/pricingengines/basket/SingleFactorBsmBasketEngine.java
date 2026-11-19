package jquant.pricingengines.basket;

import jquant.AverageBasketPayoff;
import jquant.BasketOption;
import jquant.Option;
import jquant.math.Array;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close_enough;

/**
 * Basket engine where all underlyings are driven by one stochastic factor.
 *
 * <p>Jaehyuk Choi, "Sum of all Black-Scholes-Merton Models: An efficient
 * Pricing Method for Spread, Basket and Asian Options".
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/basket/singlefactorbsmbasketengine.{hpp,cpp}}.
 */
public class SingleFactorBsmBasketEngine extends BasketOption.engine {
    private final double xTol_;
    private final int n_;
    private final List<GeneralizedBlackScholesProcess> processes_;

    public SingleFactorBsmBasketEngine(
            List<GeneralizedBlackScholesProcess> p) {
        this(p, 1e4 * QL_EPSILON);
    }

    public SingleFactorBsmBasketEngine(
            List<GeneralizedBlackScholesProcess> p, double xTol) {
        xTol_ = xTol;
        n_ = p.size();
        processes_ = new ArrayList<>(p);
        for (GeneralizedBlackScholesProcess process : processes_) {
            registerWith(process);
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.payoff instanceof AverageBasketPayoff,
                "average basket payoff expected");
        AverageBasketPayoff avgPayoff =
                (AverageBasketPayoff) arguments_.payoff;
        QL_REQUIRE(avgPayoff.basePayoff() instanceof PlainVanillaPayoff,
                "non-plain vanilla payoff given");
        PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) avgPayoff.basePayoff();
        double strike = payoff.strike();

        Array weights = avgPayoff.weights();
        QL_REQUIRE(n_ == weights.size(),
                "wrong number of weights arguments in payoff");

        QL_REQUIRE(arguments_.exercise instanceof jquant.EuropeanExercise,
                "not an European exercise");
        Date maturityDate = arguments_.exercise.lastDate();

        VectorBsmProcessExtractor pExtractor =
                new VectorBsmProcessExtractor(processes_);
        Array s = pExtractor.getSpot();
        Array dq = pExtractor.getDividendYieldDf(maturityDate);
        double dr0 = pExtractor.getInterestRateDf(maturityDate);

        Array stdDev = pExtractor.getBlackStdDev(maturityDate);
        Array v = new Array(stdDev.size());
        for (int i = 0; i < stdDev.size(); ++i) {
            v.set(i, stdDev.get(i) * stdDev.get(i));
        }

        Array fwdBasket = new Array(n_);
        for (int i = 0; i < n_; ++i) {
            fwdBasket.set(i, weights.get(i) * s.get(i) * dq.get(i) / dr0);
        }

        // first check if all vols are zero -> intrinsic case
        boolean allZero = true;
        for (int i = 0; i < stdDev.size(); ++i) {
            if (!close_enough(stdDev.get(i), 0.0)) {
                allZero = false;
                break;
            }
        }

        if (allZero) {
            double sum = 0.0;
            for (int i = 0; i < fwdBasket.size(); ++i) {
                sum += fwdBasket.get(i);
            }
            results_.value = dr0 * payoff.value(sum);
        } else {
            Array a = new Array(fwdBasket.size());
            for (int i = 0; i < fwdBasket.size(); ++i) {
                a.set(i, fwdBasket.get(i) * Math.exp(-0.5 * v.get(i)));
            }
            double d = -new SumExponentialsRootSolver(a, stdDev, strike)
                    .getRoot(xTol_, SumExponentialsRootSolver.Strategy.Brent);

            CumulativeNormalDistribution N = new CumulativeNormalDistribution();
            double cp = payoff.optionType() == Option.Type.Call ? 1.0 : -1.0;

            double sum = -strike * N.value(cp * d);
            for (int i = 0; i < fwdBasket.size(); ++i) {
                sum += fwdBasket.get(i) * N.value(cp * (d + stdDev.get(i)));
            }

            results_.value = cp * dr0 * sum;
            results_.additionalResults.put("d", d);
        }
    }
}
