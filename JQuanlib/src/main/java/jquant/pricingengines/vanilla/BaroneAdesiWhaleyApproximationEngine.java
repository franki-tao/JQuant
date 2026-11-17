package jquant.pricingengines.vanilla;

import jquant.AmericanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.distributions.CumulativeNormalDistribution;
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
 * Barone-Adesi and Whaley approximation engine for American options (1987).
 *
 * <p>Ported from {@code ql/pricingengines/vanilla/baroneadesiwhaleyengine.cpp}.
 */
public class BaroneAdesiWhaleyApproximationEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final Option.arguments arguments_ = new Option.arguments();
    private EuropeanOption.results results_ = newResults();
    private final Set<Observer> observers_ = new HashSet<>();

    public BaroneAdesiWhaleyApproximationEngine(
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

    /** Critical commodity price. */
    public static double criticalPrice(StrikedTypePayoff payoff,
                                       double riskFreeDiscount,
                                       double dividendDiscount,
                                       double variance) {
        return criticalPrice(payoff, riskFreeDiscount, dividendDiscount,
                variance, 1e-6);
    }

    /** Critical commodity price. */
    public static double criticalPrice(StrikedTypePayoff payoff,
                                       double riskFreeDiscount,
                                       double dividendDiscount,
                                       double variance,
                                       double tolerance) {
        QL_REQUIRE(riskFreeDiscount <= 1.0,
                "the Barone-Adesi-Whaley approximation is not applicable "
                        + "with negative interest rates "
                        + "(risk-free discount factor: " + riskFreeDiscount + ")");

        // Calculation of seed value, Si
        double n = 2.0 * Math.log(dividendDiscount / riskFreeDiscount) / variance;
        double m = -2.0 * Math.log(riskFreeDiscount) / variance;
        double bT = Math.log(dividendDiscount / riskFreeDiscount);

        double qu, Su, h, Si;
        switch (payoff.optionType()) {
            case Call:
                qu = (-(n - 1.0) + Math.sqrt(((n - 1.0) * (n - 1.0)) + 4.0 * m)) / 2.0;
                Su = payoff.strike() / (1.0 - 1.0 / qu);
                h = -(bT + 2.0 * Math.sqrt(variance)) * payoff.strike()
                        / (Su - payoff.strike());
                Si = payoff.strike() + (Su - payoff.strike())
                        * (1.0 - Math.exp(h));
                // when the carry outweighs 2*sqrt(variance), h is positive and
                // the seed can fall below the strike (even below zero), where
                // the critical price of a call can't be; Su is above the strike
                if (!(Si > payoff.strike()) || !Double.isFinite(Si)) {
                    Si = Su;
                }
                break;
            case Put:
                qu = (-(n - 1.0) - Math.sqrt(((n - 1.0) * (n - 1.0)) + 4.0 * m)) / 2.0;
                Su = payoff.strike() / (1.0 - 1.0 / qu);
                h = (bT - 2.0 * Math.sqrt(variance)) * payoff.strike()
                        / (payoff.strike() - Su);
                Si = Su + (payoff.strike() - Su) * Math.exp(h);
                // for a vanishing variance h diverges and the seed overflows;
                // Su is its finite limit (the critical price tends to the
                // strike). When the carry outweighs 2*sqrt(variance), h is
                // positive and the seed can also end up above the strike,
                // where the critical price of a put can't be; Su is between
                // zero and the strike
                if (!(Si > 0.0 && Si < payoff.strike()) || !Double.isFinite(Si)) {
                    Si = Su;
                }
                break;
            default:
                QL_FAIL("unknown option type");
                return Double.NaN;
        }

        // Newton Raphson algorithm for finding critical price Si
        double Q, LHS, RHS, bi;
        double forwardSi = Si * dividendDiscount / riskFreeDiscount;
        double d1 = (Math.log(forwardSi / payoff.strike()) + 0.5 * variance)
                / Math.sqrt(variance);
        CumulativeNormalDistribution cumNormalDist = new CumulativeNormalDistribution();
        double K = (!close(riskFreeDiscount, 1.0, 1000))
                ? -2.0 * Math.log(riskFreeDiscount)
                    / (variance * (1.0 - riskFreeDiscount))
                : 2.0 / variance;
        double temp = blackFormula(payoff.optionType(), payoff.strike(),
                forwardSi, Math.sqrt(variance), 1.0, 0.0) * riskFreeDiscount;
        switch (payoff.optionType()) {
            case Call:
                Q = (-(n - 1.0) + Math.sqrt(((n - 1.0) * (n - 1.0)) + 4 * K)) / 2;
                LHS = Si - payoff.strike();
                RHS = temp + (1 - dividendDiscount * cumNormalDist.value(d1)) * Si / Q;
                bi = dividendDiscount * cumNormalDist.value(d1) * (1 - 1 / Q)
                        + (1 - dividendDiscount
                        * cumNormalDist.derivative(d1) / Math.sqrt(variance)) / Q;
                while (Math.abs(LHS - RHS) / payoff.strike() > tolerance) {
                    Si = (payoff.strike() + RHS - bi * Si) / (1 - bi);
                    forwardSi = Si * dividendDiscount / riskFreeDiscount;
                    d1 = (Math.log(forwardSi / payoff.strike()) + 0.5 * variance)
                            / Math.sqrt(variance);
                    LHS = Si - payoff.strike();
                    double temp2 = blackFormula(payoff.optionType(), payoff.strike(),
                            forwardSi, Math.sqrt(variance), 1.0, 0.0) * riskFreeDiscount;
                    RHS = temp2 + (1 - dividendDiscount * cumNormalDist.value(d1)) * Si / Q;
                    bi = dividendDiscount * cumNormalDist.value(d1) * (1 - 1 / Q)
                            + (1 - dividendDiscount
                            * cumNormalDist.derivative(d1) / Math.sqrt(variance))
                            / Q;
                }
                break;
            case Put:
                Q = (-(n - 1.0) - Math.sqrt(((n - 1.0) * (n - 1.0)) + 4 * K)) / 2;
                LHS = payoff.strike() - Si;
                RHS = temp - (1 - dividendDiscount * cumNormalDist.value(-d1)) * Si / Q;
                bi = -dividendDiscount * cumNormalDist.value(-d1) * (1 - 1 / Q)
                        - (1 + dividendDiscount * cumNormalDist.derivative(-d1)
                        / Math.sqrt(variance)) / Q;
                while (Math.abs(LHS - RHS) / payoff.strike() > tolerance) {
                    Si = (payoff.strike() - RHS + bi * Si) / (1 + bi);
                    forwardSi = Si * dividendDiscount / riskFreeDiscount;
                    d1 = (Math.log(forwardSi / payoff.strike()) + 0.5 * variance)
                            / Math.sqrt(variance);
                    LHS = payoff.strike() - Si;
                    double temp2 = blackFormula(payoff.optionType(), payoff.strike(),
                            forwardSi, Math.sqrt(variance), 1.0, 0.0) * riskFreeDiscount;
                    RHS = temp2 - (1 - dividendDiscount * cumNormalDist.value(-d1)) * Si / Q;
                    bi = -dividendDiscount * cumNormalDist.value(-d1) * (1 - 1 / Q)
                            - (1 + dividendDiscount * cumNormalDist.derivative(-d1)
                            / Math.sqrt(variance)) / Q;
                }
                break;
            default:
                QL_FAIL("unknown option type");
        }

        return Si;
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

        // early exercise never optimal; the put case must be caught here
        // because criticalPrice() rejects a risk-free discount factor above 1
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
            double tolerance = 1e-6;
            double Sk = criticalPrice(payoff, riskFreeDiscount,
                    dividendDiscount, variance, tolerance);
            double forwardSk = Sk * dividendDiscount / riskFreeDiscount;
            double d1 = (Math.log(forwardSk / payoff.strike()) + 0.5 * variance)
                    / Math.sqrt(variance);
            double n = 2.0 * Math.log(dividendDiscount / riskFreeDiscount) / variance;
            double K = (!close(riskFreeDiscount, 1.0, 1000))
                    ? -2.0 * Math.log(riskFreeDiscount)
                        / (variance * (1.0 - riskFreeDiscount))
                    : 2.0 / variance;
            double Q, a;
            switch (payoff.optionType()) {
                case Call:
                    Q = (-(n - 1.0) + Math.sqrt(((n - 1.0) * (n - 1.0)) + 4.0 * K)) / 2.0;
                    a = (Sk / Q) * (1.0 - dividendDiscount * cumNormalDist.value(d1));
                    if (spot < Sk) {
                        results_.value = black.value()
                                + a * Math.pow((spot / Sk), Q);
                    } else {
                        results_.value = spot - payoff.strike();
                    }
                    break;
                case Put:
                    Q = (-(n - 1.0) - Math.sqrt(((n - 1.0) * (n - 1.0)) + 4.0 * K)) / 2.0;
                    a = -(Sk / Q)
                            * (1.0 - dividendDiscount * cumNormalDist.value(-d1));
                    if (spot > Sk) {
                        results_.value = black.value()
                                + a * Math.pow((spot / Sk), Q);
                    } else {
                        results_.value = payoff.strike() - spot;
                    }
                    break;
                default:
                    QL_FAIL("unknown option type");
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
