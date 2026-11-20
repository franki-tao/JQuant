package jquant.pricingengines.barrier;

import jquant.Compounding;
import jquant.DoubleBarrier;
import jquant.DoubleBarrierOption;
import jquant.Exercise;
import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for double-barrier European options using analytical
 * formulae.
 *
 * <p>The formulas are taken from "The complete guide to option pricing
 * formulas 2nd Ed", E.G. Haug, McGraw-Hill, p.156 and following. Implements
 * the Ikeda and Kunitomo series; the series is truncated after {@code series}
 * terms on each side (the default is 5).
 *
 * <p>Port of QuantLib
 * {@code ql/pricingengines/barrier/analyticdoublebarrierengine.{hpp,cpp}}.
 */
public class AnalyticDoubleBarrierEngine extends DoubleBarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final CumulativeNormalDistribution f_ = new CumulativeNormalDistribution();
    private final int series_;

    public AnalyticDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process, int series) {
        process_ = process;
        series_ = series;

        registerWith(process_);
    }

    public AnalyticDoubleBarrierEngine(
            GeneralizedBlackScholesProcess process) {
        this(process, 5);
    }

    @Override
    public void calculate() {

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "this engine handles only european options");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        double strike = payoff.strike();
        QL_REQUIRE(strike > 0.0,
                "strike must be positive");

        double spot = underlying();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(spot), "barrier(s) already touched");

        DoubleBarrier.Type barrierType = arguments_.barrierType;

        if (triggered(spot)) {
            if (barrierType == DoubleBarrier.Type.KnockIn)
                results_.value = vanillaEquivalent();  // knocked in
            else
                results_.value = 0.0;  // knocked out
        } else {
            switch (payoff.optionType()) {
                case Call:
                    switch (barrierType) {
                        case KnockIn:
                            results_.value = callKI();
                            break;
                        case KnockOut:
                            results_.value = callKO();
                            break;
                        case KIKO:
                        case KOKI:
                            QL_FAIL("unsupported double-barrier type: "
                                    + DoubleBarrier.toString(barrierType));
                            break;
                        default:
                            QL_FAIL("unknown double-barrier type: "
                                    + DoubleBarrier.toString(barrierType));
                    }
                    break;
                case Put:
                    switch (barrierType) {
                        case KnockIn:
                            results_.value = putKI();
                            break;
                        case KnockOut:
                            results_.value = putKO();
                            break;
                        case KIKO:
                        case KOKI:
                            QL_FAIL("unsupported double-barrier type: "
                                    + DoubleBarrier.toString(barrierType));
                            break;
                        default:
                            QL_FAIL("unknown double-barrier type: "
                                    + DoubleBarrier.toString(barrierType));
                    }
                    break;
                default:
                    QL_FAIL("unknown type");
            }
        }
    }

    private double underlying() {
        return process_.x0();
    }

    private double strike() {
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        return ((PlainVanillaPayoff) arguments_.payoff).strike();
    }

    private double residualTime() {
        return process_.time(arguments_.exercise.lastDate());
    }

    private double volatility() {
        return process_.blackVolatility().getValue().blackVol(
                residualTime(), strike());
    }

    private double volatilitySquared() {
        return volatility() * volatility();
    }

    private double stdDeviation() {
        return volatility() * Math.sqrt(residualTime());
    }

    private double barrierLo() {
        return arguments_.barrier_lo;
    }

    private double barrierHi() {
        return arguments_.barrier_hi;
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

    private double costOfCarry() {
        return riskFreeRate() - dividendYield();
    }

    private double vanillaEquivalent() {
        // Call KI equates to vanilla - callKO
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        double forwardPrice = underlying() * dividendDiscount()
                / riskFreeDiscount();
        BlackCalculator black = new BlackCalculator(
                payoff, forwardPrice, stdDeviation(), riskFreeDiscount());
        double vanilla = black.value();
        if (vanilla < 0.0)
            vanilla = 0.0;
        return vanilla;
    }

    private double callKO() {
        if (strike() >= barrierHi())
            return 0.0;
        // Integrate only over terminal spots inside the barriers; keep the
        // payoff strike.
        final double bound = Math.max(strike(), barrierLo());
        // N.B. for flat barriers mu3=mu1 and mu2=0
        double mu1 = 2 * costOfCarry() / volatilitySquared() + 1;
        double bsigma = (costOfCarry() + volatilitySquared() / 2.0)
                * residualTime() / stdDeviation();

        double acc1 = 0;
        double acc2 = 0;
        for (int n = -series_; n <= series_; ++n) {
            double L2n = Math.pow(barrierLo(), 2 * n);
            double U2n = Math.pow(barrierHi(), 2 * n);
            double d1 = Math.log(underlying() * U2n / (bound * L2n))
                    / stdDeviation() + bsigma;
            double d2 = Math.log(underlying() * U2n
                    / (barrierHi() * L2n)) / stdDeviation() + bsigma;
            double d3 = Math.log(Math.pow(barrierLo(), 2 * n + 2)
                    / (bound * underlying() * U2n)) / stdDeviation() + bsigma;
            double d4 = Math.log(Math.pow(barrierLo(), 2 * n + 2)
                    / (barrierHi() * underlying() * U2n))
                    / stdDeviation() + bsigma;

            acc1 += Math.pow(Math.pow(barrierHi(), n)
                            / Math.pow(barrierLo(), n), mu1) *
                    (f_.value(d1) - f_.value(d2)) -
                    Math.pow(Math.pow(barrierLo(), n + 1)
                                    / (Math.pow(barrierHi(), n) * underlying()),
                            mu1) *
                            (f_.value(d3) - f_.value(d4));

            acc2 += Math.pow(Math.pow(barrierHi(), n)
                            / Math.pow(barrierLo(), n), mu1 - 2) *
                    (f_.value(d1 - stdDeviation())
                            - f_.value(d2 - stdDeviation())) -
                    Math.pow(Math.pow(barrierLo(), n + 1)
                                    / (Math.pow(barrierHi(), n) * underlying()),
                            mu1 - 2) *
                            (f_.value(d3 - stdDeviation())
                                    - f_.value(d4 - stdDeviation()));
        }

        double rend = Math.exp(-dividendYield() * residualTime());
        double kov = underlying() * rend * acc1
                - strike() * riskFreeDiscount() * acc2;
        return Math.max(0.0, kov);
    }

    private double callKI() {
        // Call KI equates to vanilla - callKO
        return Math.max(0.0, vanillaEquivalent() - callKO());
    }

    private double putKO() {
        if (strike() <= barrierLo())
            return 0.0;
        final double bound = Math.min(strike(), barrierHi());
        double mu1 = 2 * costOfCarry() / volatilitySquared() + 1;
        double bsigma = (costOfCarry() + volatilitySquared() / 2.0)
                * residualTime() / stdDeviation();

        double acc1 = 0;
        double acc2 = 0;
        for (int n = -series_; n <= series_; ++n) {
            double L2n = Math.pow(barrierLo(), 2 * n);
            double U2n = Math.pow(barrierHi(), 2 * n);
            double y1 = Math.log(underlying() * U2n
                    / Math.pow(barrierLo(), 2 * n + 1))
                    / stdDeviation() + bsigma;
            double y2 = Math.log(underlying() * U2n / (bound * L2n))
                    / stdDeviation() + bsigma;
            double y3 = Math.log(Math.pow(barrierLo(), 2 * n + 2)
                    / (barrierLo() * underlying() * U2n))
                    / stdDeviation() + bsigma;
            double y4 = Math.log(Math.pow(barrierLo(), 2 * n + 2)
                    / (bound * underlying() * U2n))
                    / stdDeviation() + bsigma;

            acc1 += Math.pow(Math.pow(barrierHi(), n)
                            / Math.pow(barrierLo(), n), mu1 - 2) *
                    (f_.value(y1 - stdDeviation())
                            - f_.value(y2 - stdDeviation())) -
                    Math.pow(Math.pow(barrierLo(), n + 1)
                                    / (Math.pow(barrierHi(), n) * underlying()),
                            mu1 - 2) *
                            (f_.value(y3 - stdDeviation())
                                    - f_.value(y4 - stdDeviation()));

            acc2 += Math.pow(Math.pow(barrierHi(), n)
                            / Math.pow(barrierLo(), n), mu1) *
                    (f_.value(y1) - f_.value(y2)) -
                    Math.pow(Math.pow(barrierLo(), n + 1)
                                    / (Math.pow(barrierHi(), n) * underlying()),
                            mu1) *
                            (f_.value(y3) - f_.value(y4));
        }

        double rend = Math.exp(-dividendYield() * residualTime());
        double kov = strike() * riskFreeDiscount() * acc1
                - underlying() * rend * acc2;
        return Math.max(0.0, kov);
    }

    private double putKI() {
        // Put KI equates to vanilla - putKO
        return Math.max(0.0, vanillaEquivalent() - putKO());
    }
}
