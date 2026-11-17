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
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import org.apache.commons.math3.special.Erf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.M_SQRT2;
import static jquant.math.MathUtils.M_SQRTPI;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.squared;

/**
 * Bjerksund and Stensland approximation engine for American options (1993).
 *
 * <p>The 1993 paper gives a heuristic for the flat exercise trigger. When the
 * value still increases above it, this engine uses the trigger that maximises
 * the value instead, so results can differ from the published ones; this
 * happens mostly for negative carry and long maturities, where the published
 * trigger falls below the strike and the approximation collapses to the
 * European value.
 *
 * <p>Ported from
 * {@code ql/pricingengines/vanilla/bjerksundstenslandengine.cpp}.
 */
public class BjerksundStenslandApproximationEngine implements PricingEngine {
    private static final CumulativeNormalDistribution cumNormalDist =
            new CumulativeNormalDistribution();

    private final GeneralizedBlackScholesProcess process_;
    private final Option.arguments arguments_ = new Option.arguments();
    private EuropeanOption.results results_ = newResults();
    private final Set<Observer> observers_ = new HashSet<>();

    public BjerksundStenslandApproximationEngine(
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

    private static double phi(double S, double gamma, double H, double I,
                              double rT, double bT, double variance) {
        double lambda = (-rT + gamma * bT + 0.5 * gamma * (gamma - 1.0)
                * variance);
        double d = -(Math.log(S / H) + (bT + (gamma - 0.5) * variance))
                / Math.sqrt(variance);
        double kappa = 2.0 * bT / variance + (2.0 * gamma - 1.0);
        return Math.exp(lambda) * (cumNormalDist.value(d)
                - Math.pow((I / S), kappa)
                * cumNormalDist.value(d - 2.0 * Math.log(I / S) / Math.sqrt(variance)));
    }

    private static double phi_S(double S, double gamma, double H, double I,
                                double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * ((-(Math.pow(I / S, 2 * (gamma + bT / v))
                / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8. * v)) * I))
                - 1 / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8. * v)) * S))
                / (M_SQRT2 * M_SQRTPI * sv)
                + (Math.pow(I / S, 2 * (gamma + bT / v)) * (2 * bT + (-1 + 2 * gamma) * v)
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv)))
                / (2. * I * v));
    }

    private static double phi_SS(double S, double gamma, double H, double I,
                                 double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);
        final double ex = Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8. * v));
        final double ey = Math.exp(squared(2 * bT + (-1 + 2 * gamma) * v + 2 * lsh) / (8. * v));

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * ((M_SQRT2 * I * v * sv) / ey
                + (2 * M_SQRT2 * Math.pow(I / S, 2 * (gamma + bT / v)) * S * sv
                * (2 * bT + (-1 + 2 * gamma) * v)) / ex
                - 2 * Math.sqrt(M_PI) * Math.pow(I / S, 2 * (gamma + bT / v)) * S
                * (bT + gamma * v) * (2 * bT + (-1 + 2 * gamma) * v)
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv))
                + (M_SQRT2 * I * sv * (bT + (-0.5 + gamma) * v + lsh)) / ey
                - (Math.pow(I / S, 2 * (gamma + bT / v)) * S * sv
                * (2 * bT - 3 * v + 2 * gamma * v + 4 * lis + 2 * lsh))
                / (M_SQRT2 * ex)))
                / (2. * I * M_SQRTPI * squared(S * v));
    }

    private static double phi_gamma(double S, double gamma, double H, double I,
                                    double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2)
                * (((-Math.exp(-squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8 * v))
                + Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v)
                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8 * v)))
                * sv)
                / (M_SQRT2 * M_SQRTPI)
                + ((2 * bT + (-1 + 2 * gamma) * v)
                * Erf.erfc((2 * bT + (-1 + 2 * gamma) * v + 2 * lsh)
                / (2. * M_SQRT2 * sv))) / 4.
                - (Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v)
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv))
                * (2 * bT + (-1 + 2 * gamma) * v + 4 * lis)) / 4.);
    }

    private static double phi_H(double S, double gamma, double H, double I,
                                double rT, double bT, double v) {
        final double lsh = Math.log(S / H);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * (I / Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8. * v))
                - (Math.pow(I / S, 2 * (gamma + bT / v)) * S)
                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * Math.log(I / S) + 2 * lsh) / (8. * v))))
                / (H * I * Math.sqrt(2 * M_PI) * Math.sqrt(v));
    }

    private static double phi_I(double S, double gamma, double H, double I,
                                double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * Math.pow(I / S, 2 * (gamma + bT / v)) * S
                * ((2 * Math.sqrt(2 / M_PI))
                / (Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8. * v)) * sv)
                + (1 - 2 * gamma - (2 * bT) / v)
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv))))
                / (2. * I * I);
    }

    private static double phi_rt(double S, double gamma, double H, double I,
                                 double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * (-(I * Erf.erfc((2 * bT - v + 2 * gamma * v + 2 * lsh)
                / (2. * Math.sqrt(2 * v))))
                + Math.pow(I / S, 2 * (gamma + bT / v)) * S
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * Math.log(I / S) + 2 * lsh)
                / (2. * Math.sqrt(2 * v)))))
                / (2. * I);
    }

    private static double phi_bt(double S, double gamma, double H, double I,
                                 double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * (M_SQRT2 * (-(I / Math.exp(squared(2 * bT - v + 2 * gamma * v + 2 * lsh) / (8. * v)))
                + (Math.pow(I / S, 2 * (gamma + bT / v)) * S)
                / Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8. * v))) * sv
                + gamma * I * Math.sqrt(M_PI) * v
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 2 * lsh) / (2. * M_SQRT2 * sv))
                - M_SQRTPI * Math.pow(I / S, 2 * (gamma + bT / v)) * S
                * Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv)) * (gamma * v + 2 * lis)))
                / (2. * I * Math.sqrt(M_PI) * v);
    }

    private static double phi_v(double S, double gamma, double H, double I,
                                double rT, double bT, double v) {
        final double lsh = Math.log(S / H);
        final double lis = Math.log(I / S);
        final double sv = Math.sqrt(v);
        final double er = Erf.erfc((2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh)
                / (2. * M_SQRT2 * sv));

        return (Math.exp(bT * gamma - rT + ((-1 + gamma) * gamma * v) / 2.)
                * (((-1 + gamma) * gamma
                * (I * Erf.erfc((2 * bT - v + 2 * gamma * v + 2 * lsh) / (2. * M_SQRT2 * sv))
                - Math.pow(I / S, 2 * (gamma + bT / v)) * S * er)) / (2. * I)
                + (2 * bT * Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v) * er * lis) / (v * v)
                + (2 * bT + v - 2 * gamma * v + 2 * lsh)
                / (2. * Math.exp(Math.pow(2 * bT + (-1 + 2 * gamma) * v + 2 * lsh, 2) / (8. * v))
                * M_SQRT2 * M_SQRTPI * v * sv)
                - (Math.pow(I / S, -1 + 2 * gamma + (2 * bT) / v)
                * (2 * bT + v - 2 * gamma * v + 4 * lis + 2 * lsh))
                / (2. * Math.exp(squared(2 * bT - v + 2 * gamma * v + 4 * lis + 2 * lsh) / (8. * v))
                * M_SQRT2 * M_SQRTPI * v * sv))) / 2.;
    }

    // value of exercising the first time the spot reaches the flat trigger I
    private static double flatTriggerValue(double S, double X, double I,
                                           double beta, double rT, double bT,
                                           double v) {
        if (S >= I) {
            return S - X;
        }
        return (I - X) * Math.pow(S / I, beta) * (1 - phi(S, beta, I, I, rT, bT, v))
                + S * phi(S, 1.0, I, I, rT, bT, v) - S * phi(S, 1.0, X, I, rT, bT, v)
                - X * phi(S, 0.0, I, I, rT, bT, v) + X * phi(S, 0.0, X, I, rT, bT, v);
    }

    // Golden-section search for the trigger in [lower, upper] giving the
    // highest flat-trigger value. It works on log(I), and ties keep the
    // lower interval: the value peaks close to lower and then flattens
    // into a plateau at large triggers.
    private static double optimalFlatTrigger(double S, double X, double lower,
                                             double upper, double beta,
                                             double rT, double bT, double v) {
        final double g = (Math.sqrt(5.0) - 1.0) / 2.0;
        double a = Math.log(lower);
        double b = Math.log(upper);
        double c = b - g * (b - a);
        double d = a + g * (b - a);
        double fc = flatTriggerValue(S, X, Math.exp(c), beta, rT, bT, v);
        double fd = flatTriggerValue(S, X, Math.exp(d), beta, rT, bT, v);
        while (b - a > 1.0e-8) {
            if (fc >= fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - g * (b - a);
                fc = flatTriggerValue(S, X, Math.exp(c), beta, rT, bT, v);
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + g * (b - a);
                fd = flatTriggerValue(S, X, Math.exp(d), beta, rT, bT, v);
            }
        }
        return Math.exp(fc >= fd ? c : d);
    }

    private EuropeanOption.results europeanCallResults(double S, double X,
                                                       double rfD, double dD,
                                                       double variance) {
        EuropeanOption.results results = newResults();

        final double forwardPrice = S * dD / rfD;
        final BlackCalculator black = new BlackCalculator(
                Option.Type.Call, X, forwardPrice, Math.sqrt(variance), rfD);

        results.value = black.value();
        results.delta = black.delta(S);
        results.gamma = black.gamma(S);

        final DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        final DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        final DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        double t = rfdc.yearFraction(
                process_.riskFreeRate().getValue().referenceDate(),
                arguments_.exercise.lastDate(), new Date(), new Date());
        results.rho = black.rho(t);

        t = divdc.yearFraction(
                process_.dividendYield().getValue().referenceDate(),
                arguments_.exercise.lastDate(), new Date(), new Date());
        results.dividendRho = black.dividendRho(t);

        t = voldc.yearFraction(
                process_.blackVolatility().getValue().referenceDate(),
                arguments_.exercise.lastDate(), new Date(), new Date());
        results.vega = black.vega(t);
        results.theta = black.theta(S, t);
        results.thetaPerDay = black.thetaPerDay(S, t);

        results.strikeSensitivity = black.strikeSensitivity();
        results.additionalResults.put("strikeGamma", results.gamma * squared(S / X));
        results.additionalResults.put("exerciseType", "European");

        return results;
    }

    private EuropeanOption.results immediateExercise(double S, double X) {
        EuropeanOption.results results = newResults();
        results.value = Math.max(0.0, S - X);
        results.delta = (S >= X) ? 1.0 : 0.0;
        results.gamma = 0.0;
        results.rho = 0.0;
        results.dividendRho = 0.0;
        results.vega = 0.0;
        results.theta = 0.0;
        results.thetaPerDay = 0.0;

        results.strikeSensitivity = -results.delta;
        results.additionalResults.put("strikeGamma", 0.0);
        results.additionalResults.put("exerciseType", "Immediate");

        return results;
    }

    private EuropeanOption.results americanCallApproximation(
            double S, double X, double rfD, double dD, double variance) {

        final EuropeanOption.results europeanResults =
                europeanCallResults(S, X, rfD, dD, variance);

        EuropeanOption.results results = newResults();

        final double bT = Math.log(dD / rfD);
        final double rT = Math.log(1.0 / rfD);

        // with q = 0 the radicand is a perfect square, so rounding can take
        // it just below zero where r = -variance/2
        final double beta = (0.5 - bT / variance)
                + Math.sqrt(Math.max(0.0, squared(bT / variance - 0.5) + 2.0 * rT / variance));

        final double BInfinity = beta / (beta - 1.0) * X;
        final double B0 = (bT == rT) ? X : Math.max(X, rT / (rT - bT) * X);
        final double ht = -(bT + 2.0 * Math.sqrt(variance)) * B0 / (BInfinity - B0);

        // as beta tends to 1 the perpetual boundary runs to infinity, and I
        // and its derivatives tend to limits in a = (bT + 2 sqrt(v)) B0
        final boolean infiniteBoundary = Math.abs(beta - 1.0) < 1e-8;
        final double a = (bT + 2.0 * Math.sqrt(variance)) * B0;
        double I = infiniteBoundary
                ? B0 + a
                : B0 + (BInfinity - B0) * (1 - Math.exp(ht));

        // Each flat trigger gives a lower bound for the American price, and
        // the published one is a heuristic for the best of them that breaks
        // down for negative carry: near and past T* = (2 sigma/|b|)^2 it drops
        // below the strike and the price collapses to the European one. When
        // the value still increases above it (or above the spot, since lower
        // triggers mean immediate exercise), take the best trigger instead.
        boolean optimalTrigger = false;
        if (!infiniteBoundary) {
            final double lower = Math.max(I, S);
            if (lower < BInfinity
                    && flatTriggerValue(S, X, lower * (1.0 + 1.0e-6), beta, rT, bT, variance)
                    > flatTriggerValue(S, X, lower, beta, rT, bT, variance)) {
                I = optimalFlatTrigger(S, X, lower, BInfinity, beta, rT, bT, variance);
                optimalTrigger = true;
            }
        }

        final double fwd = S * dD / rfD;
        final double q = Math.log(I / fwd) / Math.sqrt(variance);

        if (S >= I) {
            results = immediateExercise(S, X);
        } else if (q > 12.5) {
            // We have a run away exercise boundary. It is numerically
            // more accurate to use the Greeks of the European engine.
            results = europeanResults;
        } else {
            final double phi_S_beta_I_I_rT_bT_v
                    = phi(S, beta, I, I, rT, bT, variance);
            final double phi_S_1_I_I_rT_bT_v
                    = phi(S, 1.0, I, I, rT, bT, variance);
            final double phi_S_1_X_I_rT_bT_V
                    = phi(S, 1.0, X, I, rT, bT, variance);
            results.value = (I - X) * Math.pow(S / I, beta)
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    + S * phi_S_1_I_I_rT_bT_v
                    - S * phi_S_1_X_I_rT_bT_V
                    - X * phi(S, 0.0, I, I, rT, bT, variance)
                    + X * phi(S, 0.0, X, I, rT, bT, variance);

            final double phi_S_S_beta_I_I_rT_bT_v
                    = phi_S(S, beta, I, I, rT, bT, variance);
            final double phi_S_S_1_I_I_rT_bT_v
                    = phi_S(S, 1.0, I, I, rT, bT, variance);
            final double phi_S_S_1_X_I_rT_bT_v
                    = phi_S(S, 1.0, X, I, rT, bT, variance);
            results.delta = (I - X) * Math.pow(S / I, beta - 1) * beta / I
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    - (I - X) * Math.pow(S / I, beta)
                    * phi_S_S_beta_I_I_rT_bT_v
                    + phi_S_1_I_I_rT_bT_v
                    + S * phi_S_S_1_I_I_rT_bT_v
                    - phi_S_1_X_I_rT_bT_V
                    - S * phi_S_S_1_X_I_rT_bT_v
                    - X * phi_S(S, 0.0, I, I, rT, bT, variance)
                    + X * phi_S(S, 0.0, X, I, rT, bT, variance);

            final Date refDate = process_.riskFreeRate().getValue().referenceDate();
            final Date exerciseDate = arguments_.exercise.lastDate();
            final DayCounter qdc = process_.dividendYield().getValue().dayCounter();
            final double tq = qdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date());

            final double betaDq = tq * (1 / variance
                    - 1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                    * 2 * (bT / variance - 0.5) / variance);
            final double BInfinityDq = -X / squared(beta - 1.0) * betaDq;
            final double B0Dq = (dD <= rfD) ? 0.0
                    : X * Math.log(rfD) / squared(Math.log(dD)) * tq;

            final double htDq = tq * B0 / (BInfinity - B0)
                    - (bT + 2.0 * Math.sqrt(variance))
                    * (B0Dq * (BInfinity - B0) - B0 * (BInfinityDq - B0Dq))
                    / squared(BInfinity - B0);
            final double IDq = optimalTrigger ? 0.0
                    : infiniteBoundary
                    ? B0Dq - tq * B0 + (bT + 2.0 * Math.sqrt(variance)) * B0Dq
                        - squared(a) * betaDq / (2 * X)
                    : B0Dq + (BInfinityDq - B0Dq) * (1 - Math.exp(ht))
                        - (BInfinity - B0) * Math.exp(ht) * htDq;

            final double phi_H_S_beta_I_I_rT_bT_v
                    = phi_H(S, beta, I, I, rT, bT, variance);
            final double phi_I_S_beta_I_I_rT_bT_v
                    = phi_I(S, beta, I, I, rT, bT, variance);
            final double phi_gamma_S_beta_I_I_rT_bT_v
                    = phi_gamma(S, beta, I, I, rT, bT, variance);
            final double phi_bt_S_beta_I_I_rT_bT_v
                    = phi_bt(S, beta, I, I, rT, bT, variance);
            final double phi_H_S_1_I_I_rT_bT_v
                    = phi_H(S, 1.0, I, I, rT, bT, variance);
            final double phi_I_S_1_I_I_rT_bT_v
                    = phi_I(S, 1.0, I, I, rT, bT, variance);
            final double phi_bt_S_1_I_I_rT_bT_v
                    = phi_bt(S, 1.0, I, I, rT, bT, variance);
            final double phi_I_S_1_X_I_rT_bT_v
                    = phi_I(S, 1.0, X, I, rT, bT, variance);
            final double phi_bt_S_1_X_I_rT_bT_v
                    = phi_bt(S, 1.0, X, I, rT, bT, variance);
            final double phi_H_S_0_I_I_rT_bT_v
                    = phi_H(S, 0.0, I, I, rT, bT, variance);
            final double phi_I_S_0_I_I_rT_bT_v
                    = phi_I(S, 0.0, I, I, rT, bT, variance);
            final double phi_bt_S_0_I_I_rT_bT_v
                    = phi_bt(S, 0.0, I, I, rT, bT, variance);
            final double phi_I_S_0_X_I_rT_bT_v
                    = phi_I(S, 0.0, X, I, rT, bT, variance);
            final double phi_bt_S_0_X_I_rT_bT_v
                    = phi_bt(S, 0.0, X, I, rT, bT, variance);

            results.dividendRho =
                    (IDq * Math.pow(S / I, beta)
                    + (I - X) * Math.pow(S / I, beta)
                        * (betaDq * Math.log(S / I) - beta * 1 / I * IDq))
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    - (I - X) * Math.pow(S / I, beta)
                        * (phi_H_S_beta_I_I_rT_bT_v * IDq
                        + phi_I_S_beta_I_I_rT_bT_v * IDq
                        + phi_gamma_S_beta_I_I_rT_bT_v * betaDq
                        - phi_bt_S_beta_I_I_rT_bT_v * tq)
                    + S * (phi_H_S_1_I_I_rT_bT_v * IDq
                        + phi_I_S_1_I_I_rT_bT_v * IDq
                        - phi_bt_S_1_I_I_rT_bT_v * tq)
                    - S * (phi_I_S_1_X_I_rT_bT_v * IDq
                        - phi_bt_S_1_X_I_rT_bT_v * tq)
                    - X * (phi_H_S_0_I_I_rT_bT_v * IDq
                        + phi_I_S_0_I_I_rT_bT_v * IDq
                        - phi_bt_S_0_I_I_rT_bT_v * tq)
                    + X * (phi_I_S_0_X_I_rT_bT_v * IDq
                        - phi_bt_S_0_X_I_rT_bT_v * tq);

            final DayCounter rdc = process_.riskFreeRate().getValue().dayCounter();
            final double tr = rdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date());

            final double betaDr = tr * (-1 / variance
                    + 1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                    * 2 * ((bT / variance - 0.5) / variance + 1 / variance));
            final double BInfinityDr = -X / squared(beta - 1.0) * betaDr;
            final double B0Dr = (dD <= rfD) ? 0.0 : -X * tr / Math.log(dD);
            final double htDr = -tr * B0 / (BInfinity - B0)
                    - (bT + 2.0 * Math.sqrt(variance))
                    * (B0Dr * (BInfinity - B0) - B0 * (BInfinityDr - B0Dr))
                    / squared(BInfinity - B0);
            final double IDr = optimalTrigger ? 0.0
                    : infiniteBoundary
                    ? B0Dr + tr * B0 + (bT + 2.0 * Math.sqrt(variance)) * B0Dr
                        - squared(a) * betaDr / (2 * X)
                    : B0Dr + (BInfinityDr - B0Dr) * (1 - Math.exp(ht))
                        - (BInfinity - B0) * Math.exp(ht) * htDr;

            results.rho =
                    (IDr * Math.pow(S / I, beta)
                    + (I - X) * Math.pow(S / I, beta)
                        * (betaDr * Math.log(S / I) - beta / I * IDr))
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    - (I - X) * Math.pow(S / I, beta)
                        * (phi_H_S_beta_I_I_rT_bT_v * IDr
                        + phi_I_S_beta_I_I_rT_bT_v * IDr
                        + phi_gamma_S_beta_I_I_rT_bT_v * betaDr
                        + phi_rt(S, beta, I, I, rT, bT, variance) * tr
                        + phi_bt_S_beta_I_I_rT_bT_v * tr)
                    + S * (phi_H_S_1_I_I_rT_bT_v * IDr
                        + phi_I_S_1_I_I_rT_bT_v * IDr
                        + phi_rt(S, 1.0, I, I, rT, bT, variance) * tr
                        + phi_bt_S_1_I_I_rT_bT_v * tr)
                    - S * (phi_I_S_1_X_I_rT_bT_v * IDr
                        + phi_rt(S, 1.0, X, I, rT, bT, variance) * tr
                        + phi_bt_S_1_X_I_rT_bT_v * tr)
                    - X * (phi_H_S_0_I_I_rT_bT_v * IDr
                        + phi_I_S_0_I_I_rT_bT_v * IDr
                        + phi_rt(S, 0.0, I, I, rT, bT, variance) * tr
                        + phi_bt_S_0_I_I_rT_bT_v * tr)
                    + X * (phi_I_S_0_X_I_rT_bT_v * IDr
                        + phi_rt(S, 0.0, X, I, rT, bT, variance) * tr
                        + phi_bt_S_0_X_I_rT_bT_v * tr);

            final DayCounter vdc = process_.blackVolatility().getValue().dayCounter();
            final double tv = vdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date());
            final double varianceDv = 2 * Math.sqrt(variance * tv);

            final double betaDv = bT / squared(variance) * varianceDv
                    + -1 / (2 * Math.sqrt(squared(bT / variance - 0.5) + 2.0 * rT / variance))
                    * (2 * (bT / variance - 0.5) * bT * varianceDv / squared(variance)
                    + 2 * rT / squared(variance) * varianceDv);
            final double BInfinityDv = -X / squared(beta - 1.0) * betaDv;
            final double htDv = -1 / Math.sqrt(variance) * varianceDv * B0 / (BInfinity - B0)
                    + (bT + 2 * Math.sqrt(variance)) * B0
                    / squared(BInfinity - B0) * BInfinityDv;

            final double IDv = optimalTrigger ? 0.0
                    : infiniteBoundary
                    ? varianceDv / Math.sqrt(variance) * B0
                        - squared(a) * betaDv / (2 * X)
                    : BInfinityDv * (1 - Math.exp(ht))
                        - (BInfinity - B0) * Math.exp(ht) * htDv;

            results.vega =
                    (IDv * Math.pow(S / I, beta)
                    + (I - X) * Math.pow(S / I, beta)
                        * (betaDv * Math.log(S / I) - beta / I * IDv))
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    - (I - X) * Math.pow(S / I, beta)
                        * (phi_H_S_beta_I_I_rT_bT_v * IDv
                        + phi_I_S_beta_I_I_rT_bT_v * IDv
                        + phi_gamma_S_beta_I_I_rT_bT_v * betaDv
                        + phi_v(S, beta, I, I, rT, bT, variance) * varianceDv)
                    + S * (phi_H_S_1_I_I_rT_bT_v * IDv
                        + phi_I_S_1_I_I_rT_bT_v * IDv
                        + phi_v(S, 1.0, I, I, rT, bT, variance) * varianceDv)
                    - S * (phi_I_S_1_X_I_rT_bT_v * IDv
                        + phi_v(S, 1.0, X, I, rT, bT, variance) * varianceDv)
                    - X * (phi_H_S_0_I_I_rT_bT_v * IDv
                        + phi_I_S_0_I_I_rT_bT_v * IDv
                        + phi_v(S, 0.0, I, I, rT, bT, variance) * varianceDv)
                    + X * (phi_I_S_0_X_I_rT_bT_v * IDv
                        + phi_v(S, 0.0, X, I, rT, bT, variance) * varianceDv);

            results.gamma =
                    (I - X) * Math.pow(S / I, beta - 2) * beta * (beta - 1) / squared(I)
                    * (1 - phi_S_beta_I_I_rT_bT_v)
                    - 2 * (I - X) * Math.pow(S / I, beta - 1) * beta / I
                    * phi_S_S_beta_I_I_rT_bT_v
                    - (I - X) * Math.pow(S / I, beta)
                    * phi_SS(S, beta, I, I, rT, bT, variance)

                    + 2 * phi_S_S_1_I_I_rT_bT_v
                    + S * phi_SS(S, 1.0, I, I, rT, bT, variance)

                    - 2 * phi_S_S_1_X_I_rT_bT_v
                    - S * phi_SS(S, 1.0, X, I, rT, bT, variance)

                    - X * phi_SS(S, 0.0, I, I, rT, bT, variance)
                    + X * phi_SS(S, 0.0, X, I, rT, bT, variance);

            if (optimalTrigger) {
                // the value is stationary in I, so the trigger drops out of
                // the first-order greeks. The optimal trigger still moves with
                // the spot, though, which adds -V_SI^2/V_II to the gamma.
                java.util.function.BiFunction<Double, Double, Double> value =
                        (s, i) -> flatTriggerValue(s, X, i, beta, rT, bT, variance);
                final double h = 1.0e-4 * S, k = 1.0e-4 * I;
                final double vII = (value.apply(S, I + k) - 2.0 * value.apply(S, I)
                        + value.apply(S, I - k)) / (k * k);
                final double vSI = (value.apply(S + h, I + k)
                        - value.apply(S + h, I - k)
                        - value.apply(S - h, I + k)
                        + value.apply(S - h, I - k)) / (4.0 * h * k);
                if (vII < 0.0) {
                    results.gamma -= vSI * vSI / vII;
                }
            }

            final double vol = Math.sqrt(variance / tv);

            final Date tomorrow = refDate.add(1);
            final double dtq = qdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date())
                    - qdc.yearFraction(tomorrow, exerciseDate, new Date(), new Date());
            final double dtr = rdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date())
                    - rdc.yearFraction(tomorrow, exerciseDate, new Date(), new Date());
            final double dtv = vdc.yearFraction(refDate, exerciseDate,
                    new Date(), new Date())
                    - vdc.yearFraction(tomorrow, exerciseDate, new Date(), new Date());

            results.thetaPerDay = -(0.5 * results.vega * vol / tv * dtv
                    + results.rho * rT / (tr * tr) * dtr
                    + results.dividendRho * (rT - bT) / (tq * tq) * dtq);
            results.theta = 365 * results.thetaPerDay;

            results.strikeSensitivity = results.value / X - S / X * results.delta;
            results.additionalResults.put("strikeGamma", results.gamma * squared(S / X));

            results.additionalResults.put("exerciseType", "American");
        }

        // check if European engine gives higher NPV
        if (results.value < europeanResults.value) {
            results = europeanResults;
        }

        return results;
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

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        double variance = process_.blackVolatility().getValue().blackVariance(
                ex.lastDate(), payoff.strike(), false);
        double dividendDiscount = process_.dividendYield().getValue().discount(
                ex.lastDate(), false);
        double riskFreeDiscount = process_.riskFreeRate().getValue().discount(
                ex.lastDate(), false);
        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        double strike = payoff.strike();

        final boolean isPut = payoff.optionType() == Option.Type.Put;
        if (isPut) {
            // use put-call symmetry
            double tmp = spot;
            spot = strike;
            strike = tmp;
            double tmpDiscount = riskFreeDiscount;
            riskFreeDiscount = dividendDiscount;
            dividendDiscount = tmpDiscount;
            payoff = new PlainVanillaPayoff(Option.Type.Call, strike);
        }

        if (dividendDiscount > 1.0 && riskFreeDiscount > dividendDiscount) {
            QL_FAIL("double-boundary case r<q<0 for a call given");
        }

        if (variance < QL_EPSILON
                || (dividendDiscount >= 1.0 && dividendDiscount >= riskFreeDiscount)) {
            // with no variance the approximation divides by zero; the European
            // value is floored at intrinsic below, as in Barone-Adesi-Whaley
            results_ = europeanCallResults(
                    spot, strike, riskFreeDiscount, dividendDiscount, variance);
        } else {
            // early exercise can be optimal - use approximation
            results_ = americanCallApproximation(
                    spot, strike, riskFreeDiscount, dividendDiscount, variance);
        }

        // check if immediate exercise gives higher NPV
        if (results_.value < (spot - strike) * (1 + 10 * QL_EPSILON)) {
            results_ = immediateExercise(spot, strike);
        }

        if (isPut) {
            double tmp = results_.delta;
            results_.delta = results_.strikeSensitivity;
            results_.strikeSensitivity = tmp;

            tmp = results_.gamma;
            results_.gamma = (Double) results_.additionalResults.get("strikeGamma");
            results_.additionalResults.put("strikeGamma", tmp);

            tmp = results_.rho;
            results_.rho = results_.dividendRho;
            results_.dividendRho = tmp;

            double tr = process_.riskFreeRate().getValue().dayCounter().yearFraction(
                    process_.riskFreeRate().getValue().referenceDate(),
                    arguments_.exercise.lastDate(), new Date(), new Date());
            double tq = process_.dividendYield().getValue().dayCounter().yearFraction(
                    process_.dividendYield().getValue().referenceDate(),
                    arguments_.exercise.lastDate(), new Date(), new Date());

            results_.rho *= tr / tq;
            results_.dividendRho *= tq / tr;
        }
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
