package jquant.pricingengines.barrier;

import jquant.AmericanExercise;
import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.EuropeanExercise;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic pricing engine for double barrier binary options.
 *
 * <p>This engine implements the C.H.Hui series ("One-Touch Double Barrier
 * Binary Option Values", Applied Financial Economics 6/1996), as described in
 * "The complete guide to option pricing formulas 2nd Ed", E.G. Haug,
 * McGraw-Hill, p.180.
 *
 * <p>The Knock In part of KI+KO and KO+KI options pays at hit, while the
 * Double Knock In pays at end. This engine thus requires European exercise
 * for Double Knock options, and American exercise for KIKO/KOKI.
 *
 * <p>Greeks are calculated by simple numeric derivation.
 *
 * <p>Port of QuantLib
 * {@code ql/pricingengines/barrier/analyticdoublebarrierbinaryengine.{hpp,cpp}}.
 */
public class AnalyticDoubleBarrierBinaryEngine extends DoubleBarrierOption.engine {

    // number of iterations ...
    private static final double PI = 3.14159265358979323846264338327950;

    private final GeneralizedBlackScholesProcess process_;

    public AnalyticDoubleBarrierBinaryEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;

        registerWith(process_);
    }

    @Override
    public void calculate() {

        if (arguments_.barrierType == DoubleBarrier.Type.KIKO ||
                arguments_.barrierType == DoubleBarrier.Type.KOKI) {
            QL_REQUIRE(arguments_.exercise instanceof AmericanExercise,
                    "KIKO/KOKI options must have American exercise");
            QL_REQUIRE(arguments_.exercise.dates().get(0).serialNumber()
                            <= process_.blackVolatility().getValue().referenceDate()
                            .serialNumber(),
                    "American option with window exercise not handled yet");
        } else {
            QL_REQUIRE(arguments_.exercise instanceof EuropeanExercise,
                    "non-European exercise given");
        }
        QL_REQUIRE(arguments_.payoff instanceof CashOrNothingPayoff,
                "a cash-or-nothing payoff must be given");
        CashOrNothingPayoff payoff = (CashOrNothingPayoff) arguments_.payoff;

        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        // Official QuantLib allows strike 0 (QL_MIN_REAL .. QL_MAX_REAL);
        // the Java term structures use Double.MIN_VALUE as lower bound.
        double variance = process_.blackVolatility().getValue().blackVariance(
                arguments_.exercise.lastDate(), payoff.strike(), true);
        double barrier_lo = arguments_.barrier_lo;
        double barrier_hi = arguments_.barrier_hi;
        DoubleBarrier.Type barrierType = arguments_.barrierType;
        QL_REQUIRE(barrier_lo > 0.0,
                "positive low barrier value required");
        QL_REQUIRE(barrier_hi > 0.0,
                "positive high barrier value required");
        QL_REQUIRE(barrier_lo < barrier_hi,
                "barrier_lo must be < barrier_hi");
        QL_REQUIRE(barrierType == DoubleBarrier.Type.KnockIn ||
                        barrierType == DoubleBarrier.Type.KnockOut ||
                        barrierType == DoubleBarrier.Type.KIKO ||
                        barrierType == DoubleBarrier.Type.KOKI,
                "Unsupported barrier type");

        // degenerate cases
        switch (barrierType) {
            case KnockOut:
                if (spot <= barrier_lo || spot >= barrier_hi) {
                    // knocked out, no value
                    results_.value = 0;
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                }
                break;

            case KnockIn:
                if (spot <= barrier_lo || spot >= barrier_hi) {
                    // knocked in - pays
                    results_.value = payoff.cashPayoff();
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                }
                break;

            case KIKO:
                if (spot >= barrier_hi) {
                    // knocked out, no value
                    results_.value = 0;
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                } else if (spot <= barrier_lo) {
                    // knocked in, pays
                    results_.value = payoff.cashPayoff();
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                }
                break;

            case KOKI:
                if (spot <= barrier_lo) {
                    // knocked out, no value
                    results_.value = 0;
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                } else if (spot >= barrier_hi) {
                    // knocked in, pays
                    results_.value = payoff.cashPayoff();
                    results_.delta = 0;
                    results_.gamma = 0;
                    results_.vega = 0;
                    results_.rho = 0;
                    return;
                }
                break;
        }

        switch (barrierType) {
            case KnockOut:
            case KnockIn:
                results_.value = payoffAtExpiry(spot, variance, barrierType);
                break;

            case KIKO:
            case KOKI:
                results_.value = payoffKIKO(spot, variance, barrierType);
                break;
            default:
                break;
        }
    }

    private double payoffAtExpiry(double spot, double variance,
                                  DoubleBarrier.Type barrierType,
                                  int maxIteration,
                                  double requiredConvergence) {
        QL_REQUIRE(spot > 0.0,
                "positive spot value required");

        QL_REQUIRE(variance >= 0.0,
                "negative variance not allowed");

        double residualTime = process_.time(arguments_.exercise.lastDate());
        QL_REQUIRE(residualTime > 0.0,
                "expiration time must be > 0");

        CashOrNothingPayoff payoff = (CashOrNothingPayoff) arguments_.payoff;
        double cash = payoff.cashPayoff();
        double barrier_lo = arguments_.barrier_lo;
        double barrier_hi = arguments_.barrier_hi;

        double sigmaq = variance / residualTime;
        double r = process_.riskFreeRate().getValue().zeroRate(
                residualTime, Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
        double q = process_.dividendYield().getValue().zeroRate(
                residualTime, Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
        double b = r - q;

        double alpha = -0.5 * (2 * b / sigmaq - 1);
        double beta = -0.25 * (2 * b / sigmaq - 1) * (2 * b / sigmaq - 1)
                - 2 * r / sigmaq;
        double Z = Math.log(barrier_hi / barrier_lo);
        double factor = ((2 * PI * cash) / (Z * Z)); // common factor
        double lo_alpha = Math.pow(spot / barrier_lo, alpha);
        double hi_alpha = Math.pow(spot / barrier_hi, alpha);

        double tot = 0, term = 0;
        for (int i = 1; i < maxIteration; ++i) {
            double term1 = (lo_alpha - Math.pow(-1.0, i) * hi_alpha)
                    / (alpha * alpha + (i * PI / Z) * (i * PI / Z));
            double term2 = Math.sin(i * PI / Z * Math.log(spot / barrier_lo));
            double term3 = Math.exp(-0.5
                    * ((i * PI / Z) * (i * PI / Z) - beta) * variance);
            term = factor * i * term1 * term2 * term3;
            tot += term;
        }

        // Check if convergence is sufficiently fast (for extreme parameters
        // with big alpha the convergence can be very poor, see for example
        // Hui "One-touch double barrier binary option value")
        QL_REQUIRE(Math.abs(term) < requiredConvergence,
                "serie did not converge sufficiently fast");

        if (barrierType == DoubleBarrier.Type.KnockOut)
            return Math.max(tot, 0.0); // KO
        else {
            double discount = process_.riskFreeRate().getValue().discount(
                    arguments_.exercise.lastDate());
            QL_REQUIRE(discount > 0.0,
                    "positive discount required");
            return Math.max(cash * discount - tot, 0.0); // KI
        }
    }

    private double payoffAtExpiry(double spot, double variance,
                                  DoubleBarrier.Type barrierType) {
        return payoffAtExpiry(spot, variance, barrierType, 100, 1e-8);
    }

    private double payoffKIKO(double spot, double variance,
                              DoubleBarrier.Type barrierType) {
        return payoffKIKO(spot, variance, barrierType, 1000, 1e-8);
    }

    private double payoffKIKO(double spot, double variance,
                              DoubleBarrier.Type barrierType,
                              int maxIteration,
                              double requiredConvergence) {
        QL_REQUIRE(spot > 0.0,
                "positive spot value required");

        QL_REQUIRE(variance >= 0.0,
                "negative variance not allowed");

        double residualTime = process_.time(arguments_.exercise.lastDate());
        QL_REQUIRE(residualTime > 0.0,
                "expiration time must be > 0");

        CashOrNothingPayoff payoff = (CashOrNothingPayoff) arguments_.payoff;
        double cash = payoff.cashPayoff();
        double barrier_lo = arguments_.barrier_lo;
        double barrier_hi = arguments_.barrier_hi;
        if (barrierType == DoubleBarrier.Type.KOKI) {
            double tmp = barrier_lo;
            barrier_lo = barrier_hi;
            barrier_hi = tmp;
        }

        double sigmaq = variance / residualTime;
        double r = process_.riskFreeRate().getValue().zeroRate(
                residualTime, Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
        double q = process_.dividendYield().getValue().zeroRate(
                residualTime, Compounding.Continuous,
                Frequency.NO_FREQUENCY, true).rate();
        double b = r - q;

        double alpha = -0.5 * (2 * b / sigmaq - 1);
        double beta = -0.25 * (2 * b / sigmaq - 1) * (2 * b / sigmaq - 1)
                - 2 * r / sigmaq;
        double Z = Math.log(barrier_hi / barrier_lo);
        double log_S_L = Math.log(spot / barrier_lo);

        double tot = 0, term = 0;
        for (int i = 1; i < maxIteration; ++i) {
            double factor = (i * PI / Z) * (i * PI / Z) - beta;
            double term1 = (beta - (i * PI / Z) * (i * PI / Z)
                    * Math.exp(-0.5 * factor * variance)) / factor;
            double term2 = Math.sin(i * PI / Z * log_S_L);
            term = (2.0 / (i * PI)) * term1 * term2;
            tot += term;
        }
        tot += 1 - log_S_L / Z;
        tot *= cash * Math.pow(spot / barrier_lo, alpha);

        // Check if convergence is sufficiently fast
        QL_REQUIRE(Math.abs(term) < requiredConvergence,
                "serie did not converge sufficiently fast");

        return Math.max(tot, 0.0);
    }
}
