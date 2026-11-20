package jquant.experimental.barrieroption;

import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for double-barrier options using the analytical formulae of
 * Wulin Suo and Yong Wang ("Barrier Option Pricing").
 *
 * <p>Port of QuantLib
 * {@code ql/experimental/barrieroption/suowangdoublebarrierengine.{hpp,cpp}}.
 */
public class SuoWangDoubleBarrierEngine extends DoubleBarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final int series_;
    private final CumulativeNormalDistribution f_ =
            new CumulativeNormalDistribution();

    public SuoWangDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process, int series) {
        process_ = process;
        series_ = series;

        registerWith(process_);
    }

    public SuoWangDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, 5);
    }

    @Override
    public void calculate() {

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;
        QL_REQUIRE(payoff.strike() > 0.0,
                "strike must be positive");

        double K = payoff.strike();
        double S = process_.x0();
        QL_REQUIRE(S > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(S), "barrier touched");

        DoubleBarrier.Type barrierType = arguments_.barrierType;
        QL_REQUIRE(barrierType == DoubleBarrier.Type.KnockOut ||
                        barrierType == DoubleBarrier.Type.KnockIn,
                "only KnockIn and KnockOut options supported");

        double L = arguments_.barrier_lo;
        double H = arguments_.barrier_hi;
        double K_up = Math.min(H, K);
        double K_down = Math.max(L, K);
        double T = residualTime();
        double rd = riskFreeRate();
        double dd = riskFreeDiscount();
        double rf = dividendYield();
        double df = dividendDiscount();
        double vol = volatility();
        double mu = rd - rf - vol * vol / 2.0;
        double sgn = mu > 0 ? 1.0 : (mu < 0 ? -1.0 : 0.0);
        // rebate
        double R_L = arguments_.rebate;
        double R_H = arguments_.rebate;

        // european option
        EuropeanOption europeanOption = new EuropeanOption(
                payoff, arguments_.exercise);
        PricingEngine analyticEuropeanEngine =
                new AnalyticEuropeanEngine(process_);
        europeanOption.setPricingEngine(analyticEuropeanEngine);
        double european = europeanOption.NPV();

        double barrierOut = 0;
        double rebateIn = 0;
        for (int n = -series_; n < series_; n++) {
            double d1 = D(S / H * Math.pow(L / H, 2.0 * n),
                    vol * vol + mu, vol, T);
            double d2 = d1 - vol * Math.sqrt(T);
            double g1 = D(H / S * Math.pow(L / H, 2.0 * n - 1.0),
                    vol * vol + mu, vol, T);
            double g2 = g1 - vol * Math.sqrt(T);
            double h1 = D(S / H * Math.pow(L / H, 2.0 * n - 1.0),
                    vol * vol + mu, vol, T);
            double h2 = h1 - vol * Math.sqrt(T);
            double k1 = D(L / S * Math.pow(L / H, 2.0 * n - 1.0),
                    vol * vol + mu, vol, T);
            double k2 = k1 - vol * Math.sqrt(T);
            double d1_down = D(S / K_down * Math.pow(L / H, 2.0 * n),
                    vol * vol + mu, vol, T);
            double d2_down = d1_down - vol * Math.sqrt(T);
            double d1_up = D(S / K_up * Math.pow(L / H, 2.0 * n),
                    vol * vol + mu, vol, T);
            double d2_up = d1_up - vol * Math.sqrt(T);
            double k1_down = D((H * H) / (K_down * S)
                    * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            double k2_down = k1_down - vol * Math.sqrt(T);
            double k1_up = D((H * H) / (K_up * S)
                    * Math.pow(L / H, 2.0 * n), vol * vol + mu, vol, T);
            double k2_up = k1_up - vol * Math.sqrt(T);

            if (payoff.optionType() == Option.Type.Call) {
                barrierOut += Math.pow(L / H, 2.0 * n * mu / (vol * vol)) *
                        (df * S * Math.pow(L / H, 2.0 * n)
                                * (f_.value(d1_down) - f_.value(d1))
                                - dd * K * (f_.value(d2_down) - f_.value(d2))
                                - df * Math.pow(L / H, 2.0 * n) * H * H / S
                                * Math.pow(H / S, 2.0 * mu / (vol * vol))
                                * (f_.value(k1_down) - f_.value(k1))
                                + dd * K
                                * Math.pow(H / S, 2.0 * mu / (vol * vol))
                                * (f_.value(k2_down) - f_.value(k2)));
            } else if (payoff.optionType() == Option.Type.Put) {
                barrierOut += Math.pow(L / H, 2.0 * n * mu / (vol * vol)) *
                        (dd * K * (f_.value(h2) - f_.value(d2_up))
                                - df * S * Math.pow(L / H, 2.0 * n)
                                * (f_.value(h1) - f_.value(d1_up))
                                - dd * K
                                * Math.pow(H / S, 2.0 * mu / (vol * vol))
                                * (f_.value(g2) - f_.value(k2_up))
                                + df * Math.pow(L / H, 2.0 * n) * H * H / S
                                * Math.pow(H / S, 2.0 * mu / (vol * vol))
                                * (f_.value(g1) - f_.value(k1_up)));
            } else {
                QL_FAIL("option type not recognized");
            }

            double v1 = D(H / S * Math.pow(H / L, 2.0 * n), -mu, vol, T);
            double v2 = D(H / S * Math.pow(H / L, 2.0 * n), mu, vol, T);
            double v3 = D(S / L * Math.pow(H / L, 2.0 * n), -mu, vol, T);
            double v4 = D(S / L * Math.pow(H / L, 2.0 * n), mu, vol, T);
            rebateIn += dd * R_H * sgn
                    * (Math.pow(L / H, 2.0 * n * mu / (vol * vol))
                    * f_.value(sgn * v1)
                    - Math.pow(H / S, 2.0 * mu / (vol * vol))
                    * f_.value(-sgn * v2))
                    + dd * R_L * sgn
                    * (Math.pow(L / S, 2.0 * mu / (vol * vol))
                    * f_.value(-sgn * v3)
                    - Math.pow(H / L, 2.0 * n * mu / (vol * vol))
                    * f_.value(sgn * v4));
        }

        // rebate paid at maturity
        if (barrierType == DoubleBarrier.Type.KnockOut)
            results_.value = barrierOut;
        else
            results_.value = european - barrierOut;
        results_.additionalResults.put("vanilla", european);
        results_.additionalResults.put("barrierOut", barrierOut);
        results_.additionalResults.put("barrierIn", european - barrierOut);
        results_.additionalResults.put("rebateIn", rebateIn);
    }

    private double residualTime() {
        return process_.time(arguments_.exercise.lastDate());
    }

    private double volatility() {
        return process_.blackVolatility().getValue().blackVol(
                residualTime(), ((PlainVanillaPayoff) arguments_.payoff).strike());
    }

    private double riskFreeRate() {
        return process_.riskFreeRate().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().getValue().discount(residualTime());
    }

    private double dividendYield() {
        return process_.dividendYield().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().getValue().discount(residualTime());
    }

    private double D(double X, double lambda, double sigma, double T) {
        return (Math.log(X) + lambda * T) / (sigma * Math.sqrt(T));
    }
}
