package jquant.pricingengines.vanilla;

import jquant.AmericanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;
import static jquant.pricingengines.BlackFormula.blackFormula;

/**
 * Pricing engine for American options with Ju quadratic approximation.
 *
 * <p>Reference: An Approximate Formula for Pricing American Options,
 * Journal of Derivatives Winter 1999, Ju, N.
 *
 * <p>Ported from {@code ql/pricingengines/vanilla/juquadraticengine.cpp}.
 */
public class JuQuadraticApproximationEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final Option.arguments arguments_ = new Option.arguments();
    private EuropeanOption.results results_ = newResults();
    private final Set<Observer> observers_ = new HashSet<>();

    public JuQuadraticApproximationEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        process_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                notifyObservers();
            }
        });
    }

    @Override
    public arguments getArguments() {
        return arguments_;
    }

    @Override
    public results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_ = newResults();
    }

    @Override
    public void calculate() {
        results_ = newResults();

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.American,
                "not an American Option");

        QL_REQUIRE(arguments_.exercise instanceof AmericanExercise,
                "non-American exercise given");
        AmericanExercise ex = (AmericanExercise) arguments_.exercise;
        QL_REQUIRE(!ex.payoffAtExpiry(),
                "payoff at expiry not handled");

        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        double variance = process_.blackVolatility().getValue().blackVariance(
                ex.lastDate(), payoff.strike(), false);
        double dividendDiscount = process_.dividendYield().getValue().discount(
                ex.lastDate(), false);
        double riskFreeDiscount = process_.riskFreeRate().getValue().discount(
                ex.lastDate(), false);
        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        double forwardPrice = spot * dividendDiscount / riskFreeDiscount;
        BlackCalculator black = new BlackCalculator(payoff, forwardPrice,
                Math.sqrt(variance), riskFreeDiscount);

        // For a call, exercising yields S-K while holding is worth at least
        // S*dividendDiscount - K*riskFreeDiscount, so early exercise is never
        // optimal when dividendDiscount >= 1 and dividendDiscount >=
        // riskFreeDiscount. The mirror image holds for a put: exercising
        // yields K-S and holding is worth at least K*riskFreeDiscount -
        // S*dividendDiscount, so early exercise is never optimal when
        // riskFreeDiscount >= 1 and riskFreeDiscount >= dividendDiscount.
        // The put case also has to be caught here because the critical price
        // is 0 when the risk-free rate is null, which would make hA vanish
        // and break down the approximation below.
        boolean earlyExerciseNeverOptimal =
                (dividendDiscount >= 1.0 && dividendDiscount >= riskFreeDiscount
                        && payoff.optionType() == Option.Type.Call)
                        || (riskFreeDiscount >= 1.0 && riskFreeDiscount >= dividendDiscount
                        && payoff.optionType() == Option.Type.Put);

        if (earlyExerciseNeverOptimal) {
            results_.value = black.value();
            results_.delta = black.delta(spot);
            results_.elasticity = black.elasticity(spot);
            results_.gamma = black.gamma(spot);

            DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
            DayCounter divdc = process_.dividendYield().getValue().dayCounter();
            DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
            double t = rfdc.yearFraction(
                    process_.riskFreeRate().getValue().referenceDate(),
                    arguments_.exercise.lastDate(), new Date(), new Date());
            results_.rho = black.rho(t);

            t = divdc.yearFraction(
                    process_.dividendYield().getValue().referenceDate(),
                    arguments_.exercise.lastDate(), new Date(), new Date());
            results_.dividendRho = black.dividendRho(t);

            t = voldc.yearFraction(
                    process_.blackVolatility().getValue().referenceDate(),
                    arguments_.exercise.lastDate(), new Date(), new Date());
            results_.vega = black.vega(t);
            results_.theta = black.theta(spot, t);
            results_.thetaPerDay = black.thetaPerDay(spot, t);

            results_.strikeSensitivity = black.strikeSensitivity();
        } else if (variance < QL_EPSILON) {
            // no time value: the critical-price calculation degenerates, and
            // the American price is just the European one floored at intrinsic
            results_.value = Math.max(black.value(), payoff.value(spot));
        } else {
            // early exercise can be optimal
            CumulativeNormalDistribution cumNormalDist = new CumulativeNormalDistribution();
            NormalDistribution normalDist = new NormalDistribution();

            double tolerance = 1e-6;
            double Sk = BaroneAdesiWhaleyApproximationEngine.criticalPrice(
                    payoff, riskFreeDiscount, dividendDiscount, variance,
                    tolerance);

            double forwardSk = Sk * dividendDiscount / riskFreeDiscount;

            double alpha = -2.0 * Math.log(riskFreeDiscount) / variance;
            double beta = 2.0 * Math.log(dividendDiscount / riskFreeDiscount)
                    / variance;
            double h = 1 - riskFreeDiscount;
            double phi;
            switch (payoff.optionType()) {
                case Call:
                    phi = 1;
                    break;
                case Put:
                    phi = -1;
                    break;
                default:
                    QL_FAIL("unknown option type");
                    return;
            }
            // When the risk-free rate goes to 0, both alpha and h go to 0
            // as well; their ratio, though, tends to the finite limit
            // 2/variance (the same limit already used inside
            // BaroneAdesiWhaleyApproximationEngine.criticalPrice above).
            // The formulas below are therefore written in terms of
            // alpha/h, alpha*lambda_prime and alpha*V_E_h so that no
            // intermediate quantity diverges in that case.
            double alphaOverH = (!close(riskFreeDiscount, 1.0, 1000))
                    ? alpha / h
                    : 2.0 / variance;

            double temp_root = Math.sqrt((beta - 1) * (beta - 1) + 4 * alphaOverH);
            double lambda = (-(beta - 1) + phi * temp_root) / 2;
            // lambda_prime = -phi*alpha/(h*h*temp_root) diverges when the
            // risk-free rate goes to 0, but it only ever enters the formulas
            // below multiplied by alpha, and that product doesn't.
            double alpha_lambda_prime =
                    -phi * alphaOverH * alphaOverH / temp_root;

            double black_Sk = blackFormula(payoff.optionType(), payoff.strike(),
                    forwardSk, Math.sqrt(variance), 1.0, 0.0) * riskFreeDiscount;
            double hA = phi * (Sk - payoff.strike()) - black_Sk;

            double d1_Sk = (Math.log(forwardSk / payoff.strike()) + 0.5 * variance)
                    / Math.sqrt(variance);
            double d2_Sk = d1_Sk - Math.sqrt(variance);
            // the three terms below are alpha times the corresponding
            // terms of V_E_h; in part2, alpha/std::log(riskFreeDiscount)
            // simplifies exactly to -2/variance.
            double part1 = forwardSk * normalDist.value(d1_Sk) / Math.sqrt(variance);
            double part2 = 2.0 * phi * forwardSk * cumNormalDist.value(phi * d1_Sk)
                    * Math.log(dividendDiscount) / variance;
            double part3 = +phi * alpha * payoff.strike()
                    * cumNormalDist.value(phi * d2_Sk);
            double alpha_V_E_h = part1 + part2 + part3;

            double b = (1 - h) * alpha_lambda_prime / (2 * (2 * lambda + beta - 1));
            double c = -((1 - h) / (2 * lambda + beta - 1))
                    * (alpha_V_E_h / (hA) + alphaOverH
                    + alpha_lambda_prime / (2 * lambda + beta - 1));
            double temp_spot_ratio = Math.log(spot / Sk);
            double chi = temp_spot_ratio * (b * temp_spot_ratio + c);

            if (phi * (Sk - spot) > 0) {
                results_.value = black.value()
                        + hA * Math.pow((spot / Sk), lambda) / (1 - chi);
                double temp_chi_prime = (2 * b / spot) * Math.log(spot / Sk);
                double chi_prime = temp_chi_prime + c / spot;
                double chi_double_prime = 2 * b / (spot * spot)
                        - temp_chi_prime / spot - c / (spot * spot);
                double d1_S = (Math.log(forwardPrice / payoff.strike()) + 0.5 * variance)
                        / Math.sqrt(variance);
                // There is a typo in the original paper from Ju-Zhong
                // the first term is the Black-Scholes delta/gamma.
                results_.delta = phi * dividendDiscount * cumNormalDist.value(phi * d1_S)
                        + (lambda / (spot * (1 - chi)) + chi_prime / ((1 - chi) * (1 - chi)))
                        * (phi * (Sk - payoff.strike()) - black_Sk)
                        * Math.pow((spot / Sk), lambda);

                results_.gamma = dividendDiscount * normalDist.value(phi * d1_S)
                        / (spot * Math.sqrt(variance))
                        + (2 * lambda * chi_prime / (spot * (1 - chi) * (1 - chi))
                        + 2 * chi_prime * chi_prime / ((1 - chi) * (1 - chi) * (1 - chi))
                        + chi_double_prime / ((1 - chi) * (1 - chi))
                        + lambda * (lambda - 1) / (spot * spot * (1 - chi)))
                        * (phi * (Sk - payoff.strike()) - black_Sk)
                        * Math.pow((spot / Sk), lambda);
                // deep in the money the correction can land below the
                // exercise value, which the holder can always take instead
                double exerciseValue = payoff.value(spot);
                if (results_.value < exerciseValue) {
                    results_.value = exerciseValue;
                    results_.delta = phi;
                    results_.gamma = 0;
                }
            } else {
                results_.value = phi * (spot - payoff.strike());
                results_.delta = phi;
                results_.gamma = 0;
            }

            // for short-dated, highly volatile options the correction can
            // also take the price below the European one, which is a lower
            // bound for the American option; fall back to it in that case
            if (results_.value < black.value()) {
                results_.value = black.value();
                results_.delta = black.delta(spot);
                results_.gamma = black.gamma(spot);
            }

        } // end of "early exercise can be optimal"
    }

    @Override
    public void registerObserver(Observer observer) {
        observers_.add(observer);
    }

    @Override
    public void unregisterObserver(Observer observer) {
        observers_.remove(observer);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }

    private static EuropeanOption.results newResults() {
        EuropeanOption.results results = new EuropeanOption.results();
        results.delta = Double.NaN;
        results.gamma = Double.NaN;
        results.theta = Double.NaN;
        results.thetaPerDay = Double.NaN;
        results.vega = Double.NaN;
        results.rho = Double.NaN;
        results.dividendRho = Double.NaN;
        results.elasticity = Double.NaN;
        results.strikeSensitivity = Double.NaN;
        results.additionalResults = new HashMap<>();
        return results;
    }
}
