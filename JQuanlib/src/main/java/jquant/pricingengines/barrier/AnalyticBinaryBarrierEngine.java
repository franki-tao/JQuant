package jquant.pricingengines.barrier;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.AssetOrNothingPayoff;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_TWOPI;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Analytic pricing engine for American binary barrier options.
 *
 * <p>The formulas are taken from "The complete guide to option pricing
 * formulas 2nd Ed", E.G. Haug, McGraw-Hill, p.176 and following.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/barrier/analyticbinarybarrierengine.{hpp,cpp}}.
 */
public class AnalyticBinaryBarrierEngine extends BarrierOption.engine {

    private final GeneralizedBlackScholesProcess process_;

    public AnalyticBinaryBarrierEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    /** {@code log N(x)}, with the asymptotic tail where N(x) underflows. */
    private static double logCumNormal(double x) {
        if (x > -30.0) {
            return Math.log(new CumulativeNormalDistribution().value(x));
        }
        double z2 = 1.0 / (x * x);
        return -0.5 * x * x - Math.log(-x) - 0.5 * Math.log(M_TWOPI)
                + Math.log1p(-z2 * (1.0 - z2 * (3.0 - 15.0 * z2)));
    }

    @Override
    public void calculate() {
        AmericanExercise ex =
                (arguments_.exercise instanceof AmericanExercise a) ? a : null;
        QL_REQUIRE(ex != null, "non-American exercise given");
        QL_REQUIRE(ex.payoffAtExpiry(), "payoff must be at expiry");
        QL_REQUIRE(TimeUtils.leq(ex.dates().get(0),
                        process_.blackVolatility().getValue().referenceDate()),
                "American option with window exercise not handled yet");

        StrikedTypePayoff payoff =
                (arguments_.payoff instanceof StrikedTypePayoff p) ? p : null;
        QL_REQUIRE(payoff != null, "non-striked payoff given");

        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        double variance = process_.blackVolatility().getValue()
                .blackVariance(ex.lastDate(), payoff.strike());
        double barrier = arguments_.barrier;
        QL_REQUIRE(barrier > 0.0, "positive barrier value required");
        Barrier.Type barrierType = arguments_.barrierType;

        // KO degenerate cases
        if ((barrierType == Barrier.Type.DownOut && spot <= barrier)
                || (barrierType == Barrier.Type.UpOut && spot >= barrier)) {
            // knocked out, no value
            results_.value = 0;
            results_.delta = 0;
            results_.gamma = 0;
            results_.vega = 0;
            results_.theta = 0;
            results_.rho = 0;
            results_.dividendRho = 0;
            return;
        }

        // KI degenerate cases
        if ((barrierType == Barrier.Type.DownIn && spot <= barrier)
                || (barrierType == Barrier.Type.UpIn && spot >= barrier)) {
            // knocked in - is a digital european
            EuropeanExercise exercise = new EuropeanExercise(
                    arguments_.exercise.lastDate());
            EuropeanOption opt = new EuropeanOption(payoff, exercise);
            opt.setPricingEngine(new AnalyticEuropeanEngine(process_));
            results_.value = opt.NPV();
            results_.delta = opt.delta();
            results_.gamma = opt.gamma();
            results_.vega = opt.vega();
            results_.theta = opt.theta();
            results_.rho = opt.rho();
            results_.dividendRho = opt.dividendRho();
            return;
        }

        double riskFreeDiscount =
                process_.riskFreeRate().getValue().discount(ex.lastDate());

        results_.value = payoffAtExpiry(payoff, ex, spot, variance,
                riskFreeDiscount);
    }

    private double payoffAtExpiry(StrikedTypePayoff payoff,
                                  AmericanExercise exercise,
                                  double spot, double variance,
                                  double discount) {
        double dividendDiscount =
                process_.dividendYield().getValue().discount(exercise.lastDate());

        QL_REQUIRE(spot > 0.0, "positive spot value required");
        QL_REQUIRE(discount > 0.0, "positive discount required");
        QL_REQUIRE(dividendDiscount > 0.0,
                "positive dividend discount required");
        QL_REQUIRE(variance >= 0.0, "negative variance not allowed");

        Option.Type type = payoff.optionType();
        double strike = payoff.strike();
        double barrier = arguments_.barrier;
        QL_REQUIRE(barrier > 0.0, "positive barrier value required");
        Barrier.Type barrierType = arguments_.barrierType;

        double stdDev = Math.sqrt(variance);
        double mu = Math.log(dividendDiscount / discount) / variance - 0.5;
        double K = 0;

        // binary cash-or-nothing payoff?
        if (payoff instanceof CashOrNothingPayoff coo) {
            K = coo.cashPayoff();
        }

        // binary asset-or-nothing payoff?
        if (payoff instanceof AssetOrNothingPayoff) {
            mu += 1.0;
            K = spot * dividendDiscount / discount; // forward
        }

        double log_S_X = Math.log(spot / strike);
        double log_S_H = Math.log(spot / barrier);
        double log_H_S = Math.log(barrier / spot);
        double log_H2_SX = Math.log(barrier * barrier / (spot * strike));
        double H_S_2mu = Math.pow(barrier / spot, 2 * mu);

        double eta = (barrierType == Barrier.Type.DownIn
                || barrierType == Barrier.Type.DownOut ? 1.0 : -1.0);
        double phi = (type == Option.Type.Call ? 1.0 : -1.0);

        double cum_x1;
        double cum_x2;
        double cum_y1;
        double cum_y2;
        if (variance >= QL_EPSILON) {
            // we calculate using mu*stddev instead of (mu+1)*stddev
            // because cash-or-nothing don't need it. asset-or-nothing
            // mu is really mu+1
            double x1 = phi * (log_S_X / stdDev + mu * stdDev);
            double x2 = phi * (log_S_H / stdDev + mu * stdDev);
            double y1 = eta * (log_H2_SX / stdDev + mu * stdDev);
            double y2 = eta * (log_H_S / stdDev + mu * stdDev);

            CumulativeNormalDistribution f = new CumulativeNormalDistribution();
            cum_x1 = f.value(x1);
            cum_x2 = f.value(x2);
            cum_y1 = f.value(y1);
            cum_y2 = f.value(y2);

            // at small variance (H/S)^(2 mu) can overflow where N(y)
            // underflows; the product is finite, so it is taken in logs
            if (!Double.isFinite(H_S_2mu)) {
                cum_y1 = Math.exp(2.0 * mu * log_H_S + logCumNormal(y1));
                cum_y2 = Math.exp(2.0 * mu * log_H_S + logCumNormal(y2));
                H_S_2mu = 1.0;
            }
        } else {
            cum_x1 = log_S_X > 0 ? 1.0 : 0.0;
            cum_x2 = log_S_H > 0 ? 1.0 : 0.0;
            cum_y1 = log_H2_SX > 0 ? 1.0 : 0.0;
            cum_y2 = log_H_S > 0 ? 1.0 : 0.0;
        }

        double alpha = 0;

        switch (barrierType) {
            case DownIn:
                if (type == Option.Type.Call) {
                    // down-in and call
                    if (strike >= barrier) {
                        // B3 (eta=1, phi=1)
                        alpha = H_S_2mu * cum_y1;
                    } else {
                        // B1-B2+B4 (eta=1, phi=1)
                        alpha = cum_x1 - cum_x2 + H_S_2mu * cum_y2;
                    }
                } else {
                    // down-in and put
                    if (strike >= barrier) {
                        // B2-B3+B4 (eta=1, phi=-1)
                        alpha = cum_x2 + H_S_2mu * (-cum_y1 + cum_y2);
                    } else {
                        // B1 (eta=1, phi=-1)
                        alpha = cum_x1;
                    }
                }
                break;

            case UpIn:
                if (type == Option.Type.Call) {
                    // up-in and call
                    if (strike >= barrier) {
                        // B1 (eta=-1, phi=1)
                        alpha = cum_x1;
                    } else {
                        // B2-B3+B4 (eta=-1, phi=1)
                        alpha = cum_x2 + H_S_2mu * (-cum_y1 + cum_y2);
                    }
                } else {
                    // up-in and put
                    if (strike >= barrier) {
                        // B1-B2+B4 (eta=-1, phi=-1)
                        alpha = cum_x1 - cum_x2 + H_S_2mu * cum_y2;
                    } else {
                        // B3 (eta=-1, phi=-1)
                        alpha = H_S_2mu * cum_y1;
                    }
                }
                break;

            case DownOut:
                if (type == Option.Type.Call) {
                    // down-out and call
                    if (strike >= barrier) {
                        // B1-B3 (eta=1, phi=1)
                        alpha = cum_x1 - H_S_2mu * cum_y1;
                    } else {
                        // B2-B4 (eta=1, phi=1)
                        alpha = cum_x2 - H_S_2mu * cum_y2;
                    }
                } else {
                    // down-out and put
                    if (strike >= barrier) {
                        // B1-B2+B3-B4 (eta=1, phi=-1)
                        alpha = cum_x1 - cum_x2
                                + H_S_2mu * (cum_y1 - cum_y2);
                    } else {
                        // always 0
                        alpha = 0;
                    }
                }
                break;
            case UpOut:
                if (type == Option.Type.Call) {
                    // up-out and call
                    if (strike >= barrier) {
                        // always 0
                        alpha = 0;
                    } else {
                        // B1-B2+B3-B4 (eta=-1, phi=1)
                        alpha = cum_x1 - cum_x2
                                + H_S_2mu * (cum_y1 - cum_y2);
                    }
                } else {
                    // up-out and put
                    if (strike >= barrier) {
                        // B2-B4 (eta=-1, phi=-1)
                        alpha = cum_x2 - H_S_2mu * cum_y2;
                    } else {
                        // B1-B3 (eta=-1, phi=-1)
                        alpha = cum_x1 - H_S_2mu * cum_y1;
                    }
                }
                break;
            default:
                QL_FAIL("invalid barrier type");
        }

        return discount * K * alpha;
    }
}
