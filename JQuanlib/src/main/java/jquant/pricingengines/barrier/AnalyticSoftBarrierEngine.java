package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.SoftBarrierOption;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Frequency;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for soft barrier European options using an analytical
 * formula.
 *
 * <p>Formulas are taken from "The complete guide to option pricing formulas
 * 2nd Ed", E.G. Haug, p.165. Implements a closed form solution for soft
 * barrier options originally introduced by Hart and Ross (1994).
 *
 * <p>The formula covers down-and-in and down-and-out calls and up-and-in and
 * up-and-out puts. Other combinations of barrier and option type raise an
 * error.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/barrier/analyticsoftbarrierengine.{hpp,cpp}}.
 */
public class AnalyticSoftBarrierEngine extends SoftBarrierOption.engine {

    private final GeneralizedBlackScholesProcess process_;
    private final CumulativeNormalDistribution f_ =
            new CumulativeNormalDistribution();

    public AnalyticSoftBarrierEngine(GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        // Market data
        double S = underlying();
        double X = strike();
        double r = riskFreeRate();
        double q = dividendYield();
        double sigma = volatility();

        // Barrier parameters
        double U = barrierHi();
        double L = barrierLo();
        Barrier.Type barrierType = arguments_.barrierType;

        // Stability tweak for r and q
        final double epsilon = 1e-6;
        if (Math.abs(r - q) < 1e-10) {
            r = q + epsilon; // Avoids mu = 0.5 singularity
        }

        // Option parameters
        double T = residualTime();
        PlainVanillaPayoff payoff =
                (arguments_.payoff instanceof PlainVanillaPayoff p) ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        Option.Type optionType = payoff.optionType();
        int eta = (optionType == Option.Type.Call ? 1 : -1);
        double b = r - q; // cost of carry

        validateInputs(S, X, r, q, T, U, L, optionType, barrierType, sigma);

        boolean isKnockedIn =
                (barrierType == Barrier.Type.DownIn && S <= L)
                        || (barrierType == Barrier.Type.UpIn && S >= U);
        boolean isKnockedOut =
                (barrierType == Barrier.Type.DownOut && S <= L)
                        || (barrierType == Barrier.Type.UpOut && S >= U);

        boolean isSingleBarrier = Math.abs(U - L) < 1e-4;

        // edge case 1: fully knocked in options should be priced as vanilla
        // (there are no more barrier features to consider)
        if (isKnockedIn) {
            results_.value = vanillaEquivalent();
            return;
        }
        // edge case 2: knocked out options are worthless
        else if (isKnockedOut) {
            results_.value = 0.0;
            return;
        }

        // edge case 3: Haug formula breaks when U=L, use single barrier
        // option formula instead
        if (isSingleBarrier) {
            results_.value = standardBarrierEquivalent();
            return;
        }

        // Haug's formula covers down barriers for calls and up barriers for
        // puts
        boolean isDown = (barrierType == Barrier.Type.DownIn
                || barrierType == Barrier.Type.DownOut);
        QL_REQUIRE((optionType == Option.Type.Call) == isDown,
                "soft-barrier formula not available for "
                        + barrierType + " " + optionType + " options");

        // soft barrier pricing logic
        double w = knockInValue(S, X, r, sigma, T, U, L, b, optionType, eta);
        results_.value = (barrierType == Barrier.Type.DownIn
                || barrierType == Barrier.Type.UpIn)
                ? w                     // knock in price
                : vanillaEquivalent() - w;  // knock out price
    }

    /** Implements the formula to calculate 'w' from the Haug textbook. */
    private double knockInValue(double S, double X, double r, double sigma,
                                double T, double U, double L, double b,
                                Option.Type optionType, int eta) {
        // constant terms
        final double mu = (b + 0.5 * sigma * sigma) / (sigma * sigma);
        final double sqrtT = Math.sqrt(T);
        final double lambda1 =
                Math.exp(-0.5 * sigma * sigma * T * (mu + 0.5) * (mu - 0.5));
        final double lambda2 =
                Math.exp(-0.5 * sigma * sigma * T * (mu - 0.5) * (mu - 1.5));
        final double SX = S * X;
        final double logU2_SX = Math.log((U * U) / SX);
        final double logL2_SX = Math.log((L * L) / SX);

        // d and e terms
        final double d1 = logU2_SX / (sigma * sqrtT) + mu * sigma * sqrtT;
        final double d2 = d1 - (mu + 0.5) * sigma * sqrtT;
        final double d3 = logU2_SX / (sigma * sqrtT) + (mu - 1) * sigma * sqrtT;
        final double d4 = d3 - (mu - 0.5) * sigma * sqrtT;

        final double e1 = logL2_SX / (sigma * sqrtT) + mu * sigma * sqrtT;
        final double e2 = e1 - (mu + 0.5) * sigma * sqrtT;
        final double e3 = logL2_SX / (sigma * sqrtT) + (mu - 1) * sigma * sqrtT;
        final double e4 = e3 - (mu - 0.5) * sigma * sqrtT;

        final double Nd1 = f_.value(eta * d1);
        final double Nd2 = f_.value(eta * d2);
        final double Nd3 = f_.value(eta * d3);
        final double Nd4 = f_.value(eta * d4);
        final double Ne1 = f_.value(eta * e1);
        final double Ne2 = f_.value(eta * e2);
        final double Ne3 = f_.value(eta * e3);
        final double Ne4 = f_.value(eta * e4);

        // term 1
        double term1 = eta * S * Math.exp((b - r) * T) * Math.pow(S, -2.0 * mu)
                * Math.pow(SX, mu + 0.5) / (2.0 * (mu + 0.5));

        term1 *= Math.pow(U * U / SX, mu + 0.5) * Nd1 - lambda1 * Nd2
                - Math.pow(L * L / SX, mu + 0.5) * Ne1 + lambda1 * Ne2;

        // term 2
        double term2 = eta * X * Math.exp(-r * T)
                * Math.pow(S, -2.0 * (mu - 1))
                * Math.pow(SX, mu - 0.5) / (2.0 * (mu - 0.5));

        term2 *= Math.pow(U * U / SX, mu - 0.5) * Nd3 - lambda2 * Nd4
                - Math.pow(L * L / SX, mu - 0.5) * Ne3 + lambda2 * Ne4;

        // final result
        return (1.0 / (U - L)) * (term1 - term2);
    }

    /** Helper function to check inputs are reasonable. */
    private void validateInputs(double S, double X, double r, double q,
                                double T, double U, double L,
                                Option.Type optionType,
                                Barrier.Type barrierType, double sigma) {
        // Core parameter checks
        QL_REQUIRE(S > 0.0, "Spot price must be > 0");
        QL_REQUIRE(X > 0.0, "Strike price must be > 0");
        QL_REQUIRE(T > 0.0, "Option must have time to maturity > 0");
        QL_REQUIRE(sigma > 0, "Volatility must be > 0");
        QL_REQUIRE(optionType == Option.Type.Call
                        || optionType == Option.Type.Put,
                "Invalid option type");
        QL_REQUIRE(r <= 1.0 && r >= -0.05,
                "Interest rate must be between -5% and 100%");
        QL_REQUIRE(q <= 1.0 && q >= -0.1,
                "Dividend yield must be between -10% and 100%");

        // Barrier type checks
        QL_REQUIRE(barrierType == Barrier.Type.DownIn
                        || barrierType == Barrier.Type.DownOut
                        || barrierType == Barrier.Type.UpIn
                        || barrierType == Barrier.Type.UpOut,
                "Invalid barrier type");
        QL_REQUIRE(!Double.isNaN(L), "no low barrier given");
        QL_REQUIRE(!Double.isNaN(U), "no high barrier given");
        QL_REQUIRE(U > 0.0 && L > 0.0, "Barrier levels must be positive");
        QL_REQUIRE(U >= L,
                "Upper barrier must be greater than or equal to lower barrier");
    }

    // helper functions
    private double underlying() {
        return process_.x0();
    }

    private double strike() {
        PlainVanillaPayoff payoff =
                (arguments_.payoff instanceof PlainVanillaPayoff p) ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        return payoff.strike();
    }

    private double residualTime() {
        return process_.time(arguments_.exercise.lastDate());
    }

    private double volatility() {
        return process_.blackVolatility().getValue()
                .blackVol(residualTime(), strike());
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
                Frequency.NO_FREQUENCY, false).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().getValue().discount(residualTime());
    }

    private double dividendYield() {
        return process_.dividendYield().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().getValue().discount(residualTime());
    }

    private double vanillaEquivalent() {
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        double forwardPrice =
                underlying() * dividendDiscount() / riskFreeDiscount();
        BlackCalculator black = new BlackCalculator(payoff, forwardPrice,
                stdDeviation(), riskFreeDiscount());
        double vanilla = black.value();
        return Math.max(vanilla, 0.0);
    }

    private double standardBarrierEquivalent() {
        StrikedTypePayoff payoff =
                (arguments_.payoff instanceof StrikedTypePayoff p) ? p : null;
        QL_REQUIRE(payoff != null,
                "Payoff could not be cast to StrikedTypePayoff");

        BarrierOption tempOption = new BarrierOption(
                arguments_.barrierType,
                arguments_.barrier_hi,
                0.0,
                payoff,
                arguments_.exercise);

        double spotVal = underlying();
        double qVal = dividendYield();
        double rVal = riskFreeRate();
        double volVal = volatility();

        Handle<Quote> spot = new Handle<>(new SimpleQuote(spotVal), true);
        Handle<YieldTermStructure> q = new Handle<>(
                new FlatForward(0, new Target(), qVal, new Actual360()), true);
        Handle<YieldTermStructure> r = new Handle<>(
                new FlatForward(0, new Target(), rVal, new Actual360()), true);
        Handle<BlackVolTermStructure> vol = new Handle<>(
                new BlackConstantVol(0, new Target(), volVal,
                        new Actual360()), true);

        GeneralizedBlackScholesProcess process =
                new BlackScholesMertonProcess(spot, q, r, vol);
        tempOption.setPricingEngine(new AnalyticBarrierEngine(process));

        double npv = tempOption.NPV();
        return Math.max(npv, 0.0);
    }
}
