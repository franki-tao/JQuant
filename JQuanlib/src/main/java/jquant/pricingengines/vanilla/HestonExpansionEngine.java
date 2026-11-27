package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.models.equity.HestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/pricingengines/vanilla/hestonexpansionengine.hpp/.cpp}
 * port: Heston-model engine for European options based on analytic expansions.
 *
 * <p>References:
 *
 * <p>M Forde, A Jacquier, R Lee, The small-time smile and term
 * structure of implied volatility under the Heston model
 * SIAM Journal on Financial Mathematics, 2012 - SIAM
 *
 * <p>M Lorig, S Pagliarani, A Pascucci, Explicit implied vols for
 * multifactor local-stochastic vol models
 * arXiv preprint arXiv:1306.5447v3, 2014 - arxiv.org
 *
 * <p>The expansion classes are exposed as public static nested classes, e.g.
 * {@code new HestonExpansionEngine.LPP2HestonExpansion(kappa, theta, sigma,
 * v0, rho, term)}.
 */
public class HestonExpansionEngine implements PricingEngine {

    public enum HestonExpansionFormula { LPP2, LPP3, Forde }

    private final HestonModel model_;
    private final HestonExpansionFormula formula_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public HestonExpansionEngine(HestonModel model,
                                 HestonExpansionFormula formula) {
        model_ = model;
        formula_ = formula;

        results_.additionalResults = new HashMap<>();
        model_.registerObserver(new Observer() {
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
    public Option.arguments getArguments() {
        return arguments_;
    }

    @Override
    public EuropeanOption.results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        results_.strikeSensitivity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        // this is a european option pricer
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        // plain vanilla
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        HestonProcess process = model_.process();

        Date maturityDate = arguments_.exercise.lastDate();

        final double riskFreeDiscount = process.riskFreeRate().getValue()
                .discount(maturityDate);
        final double dividendDiscount = process.dividendYield().getValue()
                .discount(maturityDate);

        final double spotPrice = process.s0().getValue().value();
        QL_REQUIRE(spotPrice > 0.0, "negative or null underlying given");

        final double strikePrice = payoff.strike();
        final double term = process.time(maturityDate);

        //possible optimization:
        //  if term=lastTerm & model=lastModel & formula=lastApprox, reuse approx.
        final double forward = spotPrice * dividendDiscount / riskFreeDiscount;
        double vol;
        switch (formula_) {
            case LPP2: {
                LPP2HestonExpansion expansion = new LPP2HestonExpansion(
                        model_.kappa(), model_.theta(),
                        model_.sigma(), model_.v0(),
                        model_.rho(), term);
                vol = expansion.impliedVolatility(strikePrice, forward);
                break;
            }
            case LPP3: {
                LPP3HestonExpansion expansion = new LPP3HestonExpansion(
                        model_.kappa(), model_.theta(),
                        model_.sigma(), model_.v0(),
                        model_.rho(), term);
                vol = expansion.impliedVolatility(strikePrice, forward);
                break;
            }
            case Forde: {
                FordeHestonExpansion expansion = new FordeHestonExpansion(
                        model_.kappa(), model_.theta(),
                        model_.sigma(), model_.v0(),
                        model_.rho(), term);
                vol = expansion.impliedVolatility(strikePrice, forward);
                break;
            }
            default:
                QL_FAIL("unknown expansion formula");
                vol = 0.0;
        }
        final double price = BlackFormula.blackFormula(payoff.optionType(),
                strikePrice, forward, vol * Math.sqrt(term),
                riskFreeDiscount, 0.0);
        results_.value = price;
    }

    /*! Interface to represent some Heston expansion formula.
        During calibration, it would typically be initialized once per
        implied volatility surface slice, then calls for each surface
        strike to impliedVolatility(strike, forward) would be
        performed.
    */
    public interface HestonExpansion {
        double impliedVolatility(double strike, double forward);
    }

    /*! Lorig Pagliarani Pascucci expansion of order-2 for the Heston model.
        During calibration, it can be initialized once per expiry, and
        called many times with different strikes.  The formula is also
        available in the Mathematica notebook from the authors at
        http://explicitsolutions.wordpress.com/
    */
    public static class LPP2HestonExpansion implements HestonExpansion {
        private final double[] coeffs = new double[3];
        private double ekt, e2kt, e3kt, e4kt;

        public LPP2HestonExpansion(double kappa, double theta, double sigma,
                                   double v0, double rho, double term) {
            ekt  = exp(kappa*term);
            e2kt = ekt*ekt;
            e3kt = e2kt*ekt;
            e4kt = e2kt*e2kt;
            coeffs[0] = z0(term, kappa, theta, sigma, v0, rho);
            coeffs[1] = z1(term, kappa, theta, sigma, v0, rho);
            coeffs[2] = z2(term, kappa, theta, sigma, v0, rho);
        }

        @Override
        public double impliedVolatility(final double strike,
                                        final double forward) {
            double x = log(strike/forward);
            double vol = coeffs[0]+x*(coeffs[1]+(x*coeffs[2]));
            return Math.max(1e-8,vol);
        }

        private double z0(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (4*pow(delta,2)*kappa*(-theta - 4*ekt*(theta + kappa*t*(theta - y)) +
                e2kt*((5 - 2*kappa*t)*theta - 2*y) + 2*y)*
                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                128*ekt*pow(kappa,3)*
                pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) +
                32*delta*ekt*pow(kappa,2)*rho*
                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                    (-1 + ekt - kappa*t)*y) +
                    pow(delta,2)*ekt*pow(rho,2)*
                    (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                    pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                        (-1 + ekt - kappa*t)*y,2) +
                        (48*pow(delta,2)*e2kt*pow(kappa,2)*pow(rho,2)*
                            pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                (-1 + ekt - kappa*t)*y,2))/
                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                pow(delta,2)*pow(rho,2)*((1 + ekt*(-1 + kappa*t))*theta +
                                    (-1 + ekt)*y)*pow((2 + kappa*t + ekt*(-2 + kappa*t))*
                                        theta + (-1 + ekt - kappa*t)*y,2) +
                                        2*pow(delta,2)*kappa*((1 + ekt*(-1 + kappa*t))*theta +
                                            (-1 + ekt)*y)*(theta - 2*y +
                                                e2kt*(-5*theta + 2*kappa*t*theta + 2*y +
                                                    8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                    4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                        pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) -
                                                        (8*pow(delta,2)*pow(kappa,2)*((1 + ekt*(-1 + kappa*t))*theta +
                                                            (-1 + ekt)*y)*(theta - 2*y +
                                                                e2kt*(-5*theta + 2*kappa*t*theta + 2*y +
                                                                    8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                    4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                        pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                        /(-theta + kappa*t*theta + (theta - y)/ekt + y))/
                                                                        (128.*e3kt*pow(kappa,5)*pow(t,2)*
                                                                            pow((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t),1.5));
        }

        private double z1(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (delta*rho*(-(delta*fastpow(-1 + ekt,2)*rho*(4*theta - y)*y) +
                2*ekt*fastpow(kappa,3)*fastpow(t,2)*theta*
                ((2 + 2*ekt + delta*rho*t)*theta - (2 + delta*rho*t)*y) -
                2*(-1 + ekt)*kappa*(2*theta - y)*
                ((-1 + ekt)*(-2 + delta*rho*t)*theta +
                    (-2 + 2*ekt + delta*rho*t)*y) +
                    fastpow(kappa,2)*t*((-1 + ekt)*
                        (-4 + delta*rho*t + ekt*(-12 + delta*rho*t))*fastpow(theta,2) +
                        2*(-4 + 4*e2kt + delta*rho*t + 3*delta*ekt*rho*t)*theta*
                        y - (-4 + delta*rho*t + 2*ekt*(2 + delta*rho*t))*fastpow(y,2))))/
                        (8.*fastpow(kappa,2)*t*sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/
                            (kappa*t))*fastpow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,
                                2));
        }

        private double z2(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (fastpow(delta,2)*sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t))*
                (-12*fastpow(rho,2)*fastpow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                    (-1 + ekt - kappa*t)*y,2) +
                    (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                    (theta - 2*y + e2kt*
                        (-5*theta + 2*kappa*t*theta + 2*y + 8*fastpow(rho,2)*((-3 + kappa*t)*theta + y)) +
                        4*ekt*(theta + kappa*t*theta - kappa*t*y +
                            fastpow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                )/(16.*e2kt*fastpow(-theta + kappa*t*theta + (theta - y)/ekt + y,
                    4));
        }
    }

    /*! Lorig Pagliarani Pascucci expansion of order-3 for the Heston model.
        During calibration, it can be initialized once per expiry, and
        called many times with different strikes.  The formula is also
        available in the Mathematica notebook from the authors at
        http://explicitsolutions.wordpress.com/
    */
    public static class LPP3HestonExpansion implements HestonExpansion {
        private final double[] coeffs = new double[4];
        private double ekt, e2kt, e3kt, e4kt;

        public LPP3HestonExpansion(double kappa, double theta, double sigma,
                                   double v0, double rho, double term) {
            ekt  = exp(kappa*term);
            e2kt = ekt*ekt;
            e3kt = e2kt*ekt;
            e4kt = e2kt*e2kt;
            coeffs[0] = z0(term, kappa, theta, sigma, v0, rho);
            coeffs[1] = z1(term, kappa, theta, sigma, v0, rho);
            coeffs[2] = z2(term, kappa, theta, sigma, v0, rho);
            coeffs[3] = z3(term, kappa, theta, sigma, v0, rho);
        }

        @Override
        public double impliedVolatility(final double strike,
                                        final double forward) {
            double x = log(strike/forward);
            double vol = coeffs[0]+x*(coeffs[1]+x*(coeffs[2]+x*(coeffs[3])));
            return Math.max(1e-8,vol);
        }

        private double z0(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (96*pow(delta,2)*ekt*pow(kappa,3)*
                (-theta - 4*ekt*(theta + kappa*t*(theta - y)) +
                    e2kt*((5 - 2*kappa*t)*theta - 2*y) + 2*y)*
                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                    3072*e2kt*pow(kappa,5)*
                    pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) +
                    96*pow(delta,3)*ekt*pow(kappa,2)*rho*
                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                    (-2*theta - kappa*t*theta - 2*ekt*(2 + kappa*t)*
                        (2*theta + kappa*t*(theta - y)) + e2kt*((10 - 3*kappa*t)*theta - 3*y) +
                        3*y + 2*kappa*t*y) + 768*delta*e2kt*pow(kappa,4)*rho*
                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                        ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                            (-1 + ekt - kappa*t)*y) +
                            6*pow(delta,3)*kappa*rho*(-theta - 4*ekt*(theta + kappa*t*(theta - y)) +
                                e2kt*((5 - 2*kappa*t)*theta - 2*y) + 2*y)*
                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                    (-1 + ekt - kappa*t)*y) +
                                    24*pow(delta,2)*e2kt*pow(kappa,2)*pow(rho,2)*
                                    (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                                    pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                        (-1 + ekt - kappa*t)*y,2) +
                                        (1152*pow(delta,2)*e3kt*pow(kappa,4)*pow(rho,2)*
                                            pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                (-1 + ekt - kappa*t)*y,2))/
                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                                24*pow(delta,2)*ekt*pow(kappa,2)*pow(rho,2)*
                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                    (-1 + ekt - kappa*t)*y,2) +
                                                    80*pow(delta,3)*ekt*kappa*pow(rho,3)*
                                                    pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                        (-1 + ekt - kappa*t)*y,3) +
                                                        pow(delta,3)*ekt*pow(rho,3)*
                                                        (-theta + kappa*t*theta + (theta - y)/ekt + y)*
                                                        pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                            (-1 + ekt - kappa*t)*y,3) -
                                                            (1440*pow(delta,3)*e3kt*pow(kappa,3)*pow(rho,3)*
                                                                pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                    (-1 + ekt - kappa*t)*y,3))/
                                                                    pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) -
                                                                    (528*pow(delta,3)*e2kt*pow(kappa,2)*pow(rho,3)*
                                                                        pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                            (-1 + ekt - kappa*t)*y,3))/
                                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                                                            3*pow(delta,3)*pow(rho,3)*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                (-1 + ekt)*y)*pow((2 + kappa*t + ekt*(-2 + kappa*t))*
                                                                                    theta + (-1 + ekt - kappa*t)*y,3) +
                                                                                    384*pow(delta,3)*e2kt*pow(kappa,3)*rho*
                                                                                    ((2 + kappa*t + 2*ekt*pow(2 + kappa*t,2) +
                                                                                        e2kt*(-10 + 3*kappa*t))*theta +
                                                                                        (-3 + 3*e2kt - 2*kappa*t - 2*ekt*kappa*t*(2 + kappa*t))*y) -
                                                                                        (576*pow(delta,3)*e2kt*pow(kappa,3)*rho*
                                                                                            ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                (-1 + ekt - kappa*t)*y)*
                                                                                                ((1 + e2kt*(-5 + 2*kappa*t + 4*pow(rho,2)*(-3 + kappa*t)) +
                                                                                                    2*ekt*(2 + 2*kappa*t +
                                                                                                        pow(rho,2)*(6 + 4*kappa*t + pow(kappa,2)*pow(t,2))))*theta +
                                                                                                        2*(-1 + e2kt*(1 + 2*pow(rho,2)) -
                                                                                                            ekt*(2*kappa*t +
                                                                                                                pow(rho,2)*(2 + 2*kappa*t + pow(kappa,2)*pow(t,2))))*y))/
                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                                pow(delta,3)*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                    (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                        (theta*(12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                            8*pow(-1 + ekt,2)*pow(rho,2)*theta -
                                                                                                                            (-1 + ekt)*kappa*
                                                                                                                            (3 + 8*pow(rho,2)*t*theta + ekt*(15 + 8*pow(rho,2)*(9 + t*theta)))
                                                                                                                            + 2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                2*ekt*(3 + pow(rho,2)*(12 + t*theta)) +
                                                                                                                                e2kt*(3 + pow(rho,2)*(12 + t*theta)))) -
                                                                                                                                2*(6*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                                    4*pow(-1 + ekt,2)*pow(rho,2)*theta +
                                                                                                                                    2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                        ekt*(3 + pow(rho,2)*(6 + t*theta))) -
                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                        (3 + 6*pow(rho,2)*t*theta + ekt*(3 + 2*pow(rho,2)*(6 + t*theta))))*
                                                                                                                                        y + 2*pow(rho,2)*pow(1 - ekt + kappa*t,2)*pow(y,2)) -
                                                                                                                                        (40*pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                            (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                (-1 + ekt - kappa*t)*y)*
                                                                                                                                                (theta*(12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                                                    8*pow(-1 + ekt,2)*pow(rho,2)*theta -
                                                                                                                                                    (-1 + ekt)*kappa*
                                                                                                                                                    (3 + 8*pow(rho,2)*t*theta +
                                                                                                                                                        ekt*(15 + 8*pow(rho,2)*(9 + t*theta))) +
                                                                                                                                                        2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                                            2*ekt*(3 + pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                            e2kt*(3 + pow(rho,2)*(12 + t*theta)))) -
                                                                                                                                                            2*(6*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                                                                4*pow(-1 + ekt,2)*pow(rho,2)*theta +
                                                                                                                                                                2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                                                    ekt*(3 + pow(rho,2)*(6 + t*theta))) -
                                                                                                                                                                    (-1 + ekt)*kappa*
                                                                                                                                                                    (3 + 6*pow(rho,2)*t*theta + ekt*(3 + 2*pow(rho,2)*(6 + t*theta)))
                                                                                                                                                                )*y + 2*pow(rho,2)*pow(1 - ekt + kappa*t,2)*pow(y,2)))/
                                                                                                                                                                (-theta + kappa*t*theta + (theta - y)/ekt + y) -
                                                                                                                                                                12*pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                    (-1 + ekt)*y)*(2*theta + kappa*t*theta - y - kappa*t*y +
                                                                                                                                                                        ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 4*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                            2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                                pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) +
                                                                                                                                                                                (288*pow(delta,3)*pow(kappa,2)*rho*
                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                    (2*theta + kappa*t*theta - y - kappa*t*y + ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                                    (theta - 2*y + e2kt*
                                                                                                                                                                                        (-5*theta + 2*kappa*t*theta + 2*y + 4*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                        2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                                            pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                            /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                            48*pow(delta,2)*ekt*pow(kappa,3)*
                                                                                                                                                                                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                            (theta - 2*y + e2kt*
                                                                                                                                                                                                (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                    pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) -
                                                                                                                                                                                                    (192*pow(delta,2)*ekt*pow(kappa,4)*
                                                                                                                                                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                            4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                                pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                                                /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                                3*pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                    (-1 + ekt)*y)*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                                            4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                                                pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))) -
                                                                                                                                                                                                                                (12*pow(delta,3)*pow(kappa,2)*rho*
                                                                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                                                            4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                                                                pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))))
                                                                                                                                                                                                                                                /(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                                                                4*pow(delta,3)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                                                    (-1 + ekt)*y)*(3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                                        3*ekt*(6*pow(theta,2) + theta*y - 2*pow(y,2) +
                                                                                                                                                                                                                                                            kappa*(13*t*pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                                            4*pow(kappa,2)*t*(theta + t*pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                                            3*e3kt*(10*pow(theta,2) +
                                                                                                                                                                                                                                                                2*pow(kappa,2)*t*theta*(6 + 8*pow(rho,2) + t*theta) - 9*theta*y + 2*pow(y,2) +
                                                                                                                                                                                                                                                                kappa*(-9*t*pow(theta,2) + 4*(3 + 4*pow(rho,2))*y +
                                                                                                                                                                                                                                                                    theta*(-40 - 64*pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                                    e2kt*(-54*pow(theta,2) +
                                                                                                                                                                                                                                                                        8*pow(kappa,4)*pow(rho,2)*pow(t,3)*(theta - y) + 39*theta*y - 6*pow(y,2) +
                                                                                                                                                                                                                                                                        24*pow(kappa,3)*pow(t,2)*(theta + 2*pow(rho,2)*theta - (1 + pow(rho,2))*y) +
                                                                                                                                                                                                                                                                        6*pow(kappa,2)*t*(3*t*pow(theta,2) - 8*(1 + pow(rho,2))*y +
                                                                                                                                                                                                                                                                            theta*(16 + 24*pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                                            3*kappa*(5*t*pow(theta,2) + 2*y*(8*pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                                                theta*(32 + 64*pow(rho,2) + 17*t*y)))) -
                                                                                                                                                                                                                                                                                (48*pow(delta,3)*pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                                    ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)*
                                                                                                                                                                                                                                                                                    (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                                                                        3*ekt*(6*pow(theta,2) + theta*y - 2*pow(y,2) +
                                                                                                                                                                                                                                                                                            kappa*(13*t*pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                                                                            4*pow(kappa,2)*t*(theta + t*pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                                                                            3*e3kt*(10*pow(theta,2) +
                                                                                                                                                                                                                                                                                                2*pow(kappa,2)*t*theta*(6 + 8*pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                                                                                                                2*pow(y,2) + kappa*(-9*t*pow(theta,2) + 4*(3 + 4*pow(rho,2))*y +
                                                                                                                                                                                                                                                                                                    theta*(-40 - 64*pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                                                                    e2kt*(-54*pow(theta,2) +
                                                                                                                                                                                                                                                                                                        8*pow(kappa,4)*pow(rho,2)*pow(t,3)*(theta - y) + 39*theta*y - 6*pow(y,2) +
                                                                                                                                                                                                                                                                                                        24*pow(kappa,3)*pow(t,2)*
                                                                                                                                                                                                                                                                                                        (theta + 2*pow(rho,2)*theta - (1 + pow(rho,2))*y) +
                                                                                                                                                                                                                                                                                                        6*pow(kappa,2)*t*(3*t*pow(theta,2) - 8*(1 + pow(rho,2))*y +
                                                                                                                                                                                                                                                                                                            theta*(16 + 24*pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                                                                            3*kappa*(5*t*pow(theta,2) + 2*y*(8*pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                                                                                theta*(32 + 64*pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                                                                                                                (-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                                                                                                                                                (240*pow(delta,3)*e2kt*pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                                                                        (12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                                                                            2*pow(-1 + ekt,2)*pow(rho,2)*pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                                                                            (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                                                                            (8*(1 + ekt)*pow(rho,2)*t*pow(theta,2) +
                                                                                                                                                                                                                                                                                                                                2*y*(-3 - 3*ekt*(1 + 4*pow(rho,2)) + 2*pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                                                                                theta*(3 - 12*pow(rho,2)*t*y + ekt*(15 + pow(rho,2)*(72 - 4*t*y)))
                                                                                                                                                                                                                                                                                                                                ) + 2*pow(kappa,2)*t*(e2kt*theta*
                                                                                                                                                                                                                                                                                                                                    (3 + pow(rho,2)*(12 + t*theta)) + pow(rho,2)*t*pow(theta - y,2) +
                                                                                                                                                                                                                                                                                                                                    2*ekt*(pow(rho,2)*t*pow(theta,2) - 3*(y + 2*pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                                                                        theta*(3 + pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y))/
                                                                                                                                                                                                                                                                                                                                        (3072.*e4kt*pow(kappa,7)*pow(t,2)*
                                                                                                                                                                                                                                                                                                                                            pow((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t),1.5));
        }

        private double z1(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (delta*(768*e2kt*pow(kappa,4)*rho*
                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                    (-1 + ekt - kappa*t)*y) -
                    (576*delta*e2kt*pow(kappa,3)*pow(rho,2)*
                        pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                            (-1 + ekt - kappa*t)*y,2))/
                            ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                            10*pow(delta,2)*pow(rho,3)*pow((2 + kappa*t + ekt*(-2 + kappa*t))*
                                theta + (-1 + ekt - kappa*t)*y,3) +
                                (6*pow(delta,2)*kappa*pow(rho,3)*
                                    pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                        (-1 + ekt - kappa*t)*y,3))/
                                        (-theta + kappa*t*theta + (theta - y)/ekt + y) -
                                        (3360*pow(delta,2)*e3kt*pow(kappa,3)*pow(rho,3)*
                                            pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                (-1 + ekt - kappa*t)*y,3))/
                                                pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,3) -
                                                (288*pow(delta,2)*e2kt*pow(kappa,2)*pow(rho,3)*
                                                    pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                        (-1 + ekt - kappa*t)*y,3))/
                                                        pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) +
                                                        (234*pow(delta,2)*ekt*kappa*pow(rho,3)*
                                                            pow((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                (-1 + ekt - kappa*t)*y,3))/
                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) -
                                                                96*delta*ekt*pow(kappa,3)*
                                                                ((1 + 4*ekt*(1 + kappa*t) + e2kt*(-5 + 2*kappa*t))*theta +
                                                                    2*(-1 + e2kt - 2*ekt*kappa*t)*y) -
                                                                    12*pow(delta,2)*kappa*rho*((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                        (-1 + ekt - kappa*t)*y)*
                                                                        ((1 + 4*ekt*(1 + kappa*t) + e2kt*(-5 + 2*kappa*t))*theta +
                                                                            2*(-1 + e2kt - 2*ekt*kappa*t)*y) -
                                                                            192*pow(delta,2)*ekt*pow(kappa,2)*rho*
                                                                            ((2 + kappa*t + 2*ekt*pow(2 + kappa*t,2) +
                                                                                e2kt*(-10 + 3*kappa*t))*theta +
                                                                                (-3 + 3*e2kt - 2*kappa*t - 2*ekt*kappa*t*(2 + kappa*t))*y)
                                                                                - (12*pow(delta,2)*ekt*pow(kappa,2)*rho*
                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                        ((1 + e2kt*(-5 + 2*kappa*t + 8*pow(rho,2)*(-3 + kappa*t)) +
                                                                                            4*ekt*(1 + kappa*t +
                                                                                                pow(rho,2)*(6 + 4*kappa*t + pow(kappa,2)*pow(t,2))))*theta +
                                                                                                2*(-1 + e2kt*(1 + 4*pow(rho,2)) -
                                                                                                    2*ekt*(kappa*t +
                                                                                                        pow(rho,2)*(2 + 2*kappa*t + pow(kappa,2)*pow(t,2))))*y))/
                                                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                        (576*pow(delta,2)*ekt*pow(kappa,2)*rho*
                                                                                                            ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                (-1 + ekt - kappa*t)*y)*
                                                                                                                ((1 + e2kt*(-5 + 2*kappa*t + 4*pow(rho,2)*(-3 + kappa*t)) +
                                                                                                                    2*ekt*(2 + 2*kappa*t +
                                                                                                                        pow(rho,2)*(6 + 4*kappa*t + pow(kappa,2)*pow(t,2))))*theta +
                                                                                                                        2*(-1 + e2kt*(1 + 2*pow(rho,2)) -
                                                                                                                            ekt*(2*kappa*t +
                                                                                                                                pow(rho,2)*(2 + 2*kappa*t + pow(kappa,2)*pow(t,2))))*y))/
                                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                                                (5*pow(delta,2)*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                    (-1 + ekt)*y)*
                                                                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                        (theta*(12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                                            8*pow(-1 + ekt,2)*pow(rho,2)*theta -
                                                                                                                                            (-1 + ekt)*kappa*
                                                                                                                                            (3 + 8*pow(rho,2)*t*theta +
                                                                                                                                                ekt*(15 + 8*pow(rho,2)*(9 + t*theta))) +
                                                                                                                                                2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                                    2*ekt*(3 + pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                    e2kt*(3 + pow(rho,2)*(12 + t*theta)))) -
                                                                                                                                                    2*(6*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2) +
                                                                                                                                                        4*pow(-1 + ekt,2)*pow(rho,2)*theta +
                                                                                                                                                        2*pow(kappa,2)*t*(pow(rho,2)*t*theta +
                                                                                                                                                            ekt*(3 + pow(rho,2)*(6 + t*theta))) -
                                                                                                                                                            (-1 + ekt)*kappa*
                                                                                                                                                            (3 + 6*pow(rho,2)*t*theta +
                                                                                                                                                                ekt*(3 + 2*pow(rho,2)*(6 + t*theta))))*y +
                                                                                                                                                                2*pow(rho,2)*pow(1 - ekt + kappa*t,2)*pow(y,2)))/
                                                                                                                                                                (ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) -
                                                                                                                                                                (48*pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                    (-1 + ekt)*y)*
                                                                                                                                                                    (2*theta + kappa*t*theta - y - kappa*t*y +
                                                                                                                                                                        ekt*((-2 + kappa*t)*theta + y))*
                                                                                                                                                                        (theta - 2*y + e2kt*
                                                                                                                                                                            (-5*theta + 2*kappa*t*theta + 2*y + 4*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                            2*ekt*(2*(theta + kappa*t*(theta - y)) +
                                                                                                                                                                                pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                            ))/(ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) +
                                                                                                                                                                            (96*delta*pow(kappa,3)*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                (-1 + ekt)*y)*
                                                                                                                                                                                (theta - 2*y + e2kt*
                                                                                                                                                                                    (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                    4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                        pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                                    ))/(-theta + kappa*t*theta + (theta - y)/ekt + y) +
                                                                                                                                                                                    (9*pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                        (-1 + ekt)*y)*
                                                                                                                                                                                        ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                            (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                            (theta - 2*y + e2kt*
                                                                                                                                                                                                (-5*theta + 2*kappa*t*theta + 2*y + 8*pow(rho,2)*((-3 + kappa*t)*theta + y)) +
                                                                                                                                                                                                4*ekt*(theta + kappa*t*theta - kappa*t*y +
                                                                                                                                                                                                    pow(rho,2)*((6 + kappa*t*(4 + kappa*t))*theta - (2 + kappa*t*(2 + kappa*t))*y))
                                                                                                                                                                                                ))/(ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) -
                                                                                                                                                                                                (48*pow(delta,2)*ekt*pow(kappa,2)*rho*
                                                                                                                                                                                                    (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                        3*ekt*(6*pow(theta,2) + theta*y - 2*pow(y,2) +
                                                                                                                                                                                                            kappa*(13*t*pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                            4*pow(kappa,2)*t*(theta + t*pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                            3*e3kt*(10*pow(theta,2) +
                                                                                                                                                                                                                2*pow(kappa,2)*t*theta*(6 + 8*pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                                2*pow(y,2) + kappa*(-9*t*pow(theta,2) + 4*(3 + 4*pow(rho,2))*y +
                                                                                                                                                                                                                    theta*(-40 - 64*pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                    e2kt*(-54*pow(theta,2) +
                                                                                                                                                                                                                        8*pow(kappa,4)*pow(rho,2)*pow(t,3)*(theta - y) + 39*theta*y -
                                                                                                                                                                                                                        6*pow(y,2) + 24*pow(kappa,3)*pow(t,2)*
                                                                                                                                                                                                                        (theta + 2*pow(rho,2)*theta - (1 + pow(rho,2))*y) +
                                                                                                                                                                                                                        6*pow(kappa,2)*t*(3*t*pow(theta,2) - 8*(1 + pow(rho,2))*y +
                                                                                                                                                                                                                            theta*(16 + 24*pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                            3*kappa*(5*t*pow(theta,2) + 2*y*(8*pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                theta*(32 + 64*pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                                ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y) +
                                                                                                                                                                                                                                (12*pow(delta,2)*kappa*rho*((1 + ekt*(-1 + kappa*t))*theta +
                                                                                                                                                                                                                                    (-1 + ekt)*y)*
                                                                                                                                                                                                                                    (3*(theta - 2*y)*((2 + kappa*t)*theta - (1 + kappa*t)*y) +
                                                                                                                                                                                                                                        3*ekt*(6*pow(theta,2) + theta*y - 2*pow(y,2) +
                                                                                                                                                                                                                                            kappa*(13*t*pow(theta,2) + theta*(8 - 18*t*y) + 4*y*(-3 + t*y)) +
                                                                                                                                                                                                                                            4*pow(kappa,2)*t*(theta + t*pow(theta,2) - 2*t*theta*y + y*(-2 + t*y))) +
                                                                                                                                                                                                                                            3*e3kt*(10*pow(theta,2) +
                                                                                                                                                                                                                                                2*pow(kappa,2)*t*theta*(6 + 8*pow(rho,2) + t*theta) - 9*theta*y +
                                                                                                                                                                                                                                                2*pow(y,2) + kappa*(-9*t*pow(theta,2) + 4*(3 + 4*pow(rho,2))*y +
                                                                                                                                                                                                                                                    theta*(-40 - 64*pow(rho,2) + 4*t*y))) +
                                                                                                                                                                                                                                                    e2kt*(-54*pow(theta,2) +
                                                                                                                                                                                                                                                        8*pow(kappa,4)*pow(rho,2)*pow(t,3)*(theta - y) + 39*theta*y -
                                                                                                                                                                                                                                                        6*pow(y,2) + 24*pow(kappa,3)*pow(t,2)*
                                                                                                                                                                                                                                                        (theta + 2*pow(rho,2)*theta - (1 + pow(rho,2))*y) +
                                                                                                                                                                                                                                                        6*pow(kappa,2)*t*(3*t*pow(theta,2) - 8*(1 + pow(rho,2))*y +
                                                                                                                                                                                                                                                            theta*(16 + 24*pow(rho,2) - 3*t*y)) -
                                                                                                                                                                                                                                                            3*kappa*(5*t*pow(theta,2) + 2*y*(8*pow(rho,2) + 3*t*y) -
                                                                                                                                                                                                                                                                theta*(32 + 64*pow(rho,2) + 17*t*y)))))/
                                                                                                                                                                                                                                                                (ekt*(-theta + kappa*t*theta + (theta - y)/ekt + y)) +
                                                                                                                                                                                                                                                                (240*pow(delta,2)*e2kt*pow(kappa,2)*rho*
                                                                                                                                                                                                                                                                    ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                        (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                        (12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                            2*pow(-1 + ekt,2)*pow(rho,2)*pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                            (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                            (8*(1 + ekt)*pow(rho,2)*t*pow(theta,2) +
                                                                                                                                                                                                                                                                                2*y*(-3 - 3*ekt*(1 + 4*pow(rho,2)) + 2*pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                                theta*(3 - 12*pow(rho,2)*t*y +
                                                                                                                                                                                                                                                                                    ekt*(15 + pow(rho,2)*(72 - 4*t*y)))) +
                                                                                                                                                                                                                                                                                    2*pow(kappa,2)*t*(e2kt*theta*(3 + pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                                                                                                                                                        pow(rho,2)*t*pow(theta - y,2) +
                                                                                                                                                                                                                                                                                        2*ekt*(pow(rho,2)*t*pow(theta,2) - 3*(y + 2*pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                            theta*(3 + pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                            pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,2) -
                                                                                                                                                                                                                                                                                            (120*pow(delta,2)*ekt*kappa*rho*
                                                                                                                                                                                                                                                                                                ((2 + kappa*t + ekt*(-2 + kappa*t))*theta +
                                                                                                                                                                                                                                                                                                    (-1 + ekt - kappa*t)*y)*
                                                                                                                                                                                                                                                                                                    (12*ekt*pow(kappa,3)*pow(rho,2)*pow(t,2)*(theta - y) +
                                                                                                                                                                                                                                                                                                        2*pow(-1 + ekt,2)*pow(rho,2)*pow(-2*theta + y,2) -
                                                                                                                                                                                                                                                                                                        (-1 + ekt)*kappa*
                                                                                                                                                                                                                                                                                                        (8*(1 + ekt)*pow(rho,2)*t*pow(theta,2) +
                                                                                                                                                                                                                                                                                                            2*y*(-3 - 3*ekt*(1 + 4*pow(rho,2)) + 2*pow(rho,2)*t*y) +
                                                                                                                                                                                                                                                                                                            theta*(3 - 12*pow(rho,2)*t*y +
                                                                                                                                                                                                                                                                                                                ekt*(15 + pow(rho,2)*(72 - 4*t*y)))) +
                                                                                                                                                                                                                                                                                                                2*pow(kappa,2)*t*(e2kt*theta*(3 + pow(rho,2)*(12 + t*theta)) +
                                                                                                                                                                                                                                                                                                                    pow(rho,2)*t*pow(theta - y,2) +
                                                                                                                                                                                                                                                                                                                    2*ekt*(pow(rho,2)*t*pow(theta,2) - 3*(y + 2*pow(rho,2)*y) +
                                                                                                                                                                                                                                                                                                                        theta*(3 + pow(rho,2)*(12 - t*y))))))/
                                                                                                                                                                                                                                                                                                                        ((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y)))/
                                                                                                                                                                                                                                                                                                                        (1536.*e3kt*pow(kappa,6)*pow(t,2)*
                                                                                                                                                                                                                                                                                                                            pow((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t),1.5));
        }

        private double z2(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (pow(delta,2)*(8*e3kt*pow(kappa,5)*pow(rho,2)*pow(t,4)*(2 + delta*rho*t)*
                pow(theta,2)*(theta - y) - delta*pow(-1 + ekt,3)*rho*
                (2*(-1 + ekt*(-5 + 24*pow(rho,2)))*pow(theta,3) +
                    (7 + ekt*(3 + 56*pow(rho,2)))*pow(theta,2)*y -
                    3*(1 + ekt*(-3 + 8*pow(rho,2)))*theta*pow(y,2) +
                    2*(-1 + ekt*(-1 + 2*pow(rho,2)))*pow(y,3)) -
                    pow(-1 + ekt,2)*kappa*
                    ((-4 + delta*rho*t - 8*ekt*
                        (2 - 12*pow(rho,2) - 4*delta*rho*t + 25*delta*pow(rho,3)*t) +
                        e2kt*(20 - 96*pow(rho,2) + 3*delta*rho*t + 56*delta*pow(rho,3)*t)
                        )*pow(theta,3) - 2*(-8 + 2*delta*rho*t +
                            e2kt*(24 - 80*pow(rho,2) - 9*delta*rho*t +
                                24*delta*pow(rho,3)*t) -
                                4*ekt*(4 - 20*pow(rho,2) - 10*delta*rho*t + 39*delta*pow(rho,3)*t)
                            )*pow(theta,2)*y + (5*(-4 + delta*rho*t) +
                                ekt*(-16 + 80*pow(rho,2) + 57*delta*rho*t -
                                    140*delta*pow(rho,3)*t) +
                                    2*e2kt*(18 - 40*pow(rho,2) - 3*delta*rho*t +
                                        6*delta*pow(rho,3)*t))*theta*pow(y,2) +
                                        2*(4 + e2kt*(-4 + 8*pow(rho,2)) - delta*rho*t +
                                            ekt*rho*(-8*rho - 7*delta*t + 14*delta*pow(rho,2)*t))*pow(y,3)) +
                                            ekt*(-1 + ekt)*pow(kappa,2)*t*
                                            ((-24 + 128*pow(rho,2) + 9*delta*rho*t - 144*delta*pow(rho,3)*t -
                                                4*ekt*(6 - 8*pow(rho,2) - 9*delta*rho*t + 6*delta*pow(rho,3)*t) +
                                                e2kt*(48 - 160*pow(rho,2) - 9*delta*rho*t +
                                                    24*delta*pow(rho,3)*t))*pow(theta,3) -
                                                    (-72 + 320*pow(rho,2) + 27*delta*rho*t - 360*delta*pow(rho,3)*t -
                                                        ekt*rho*(160*rho - 81*delta*t + 348*delta*pow(rho,2)*t) +
                                                        2*e2kt*(36 - 80*pow(rho,2) - 3*delta*rho*t +
                                                            6*delta*pow(rho,3)*t))*pow(theta,2)*y -
                                                            2*(32 - 128*pow(rho,2) + 12*e2kt*(-1 + 2*pow(rho,2)) -
                                                                15*delta*rho*t + 144*delta*pow(rho,3)*t +
                                                                2*ekt*(-10 + 52*pow(rho,2) - 13*delta*rho*t +
                                                                    58*delta*pow(rho,3)*t))*theta*pow(y,2) +
                                                                    4*(4 - 16*pow(rho,2) - 3*delta*rho*t + 18*delta*pow(rho,3)*t +
                                                                        ekt*(-4 + 16*pow(rho,2) - 2*delta*rho*t + 11*delta*pow(rho,3)*t))*
                                                                        pow(y,3)) - 4*e2kt*pow(kappa,4)*pow(t,3)*theta*
                                                                        (2*e2kt*(-1 + 2*pow(rho,2))*pow(theta,2) +
                                                                            pow(rho,2)*(4 + 13*delta*rho*t)*pow(theta - y,2) +
                                                                            ekt*((-4 + 16*pow(rho,2) - 2*delta*rho*t + 9*delta*pow(rho,3)*t)*
                                                                                pow(theta,2) + (4 - 32*pow(rho,2) + 2*delta*rho*t - 19*delta*pow(rho,3)*t)*
                                                                                theta*y + 4*pow(rho,2)*(2 + delta*rho*t)*pow(y,2))) -
                                                                                2*ekt*pow(kappa,3)*pow(t,2)*
                                                                                (-4*pow(rho,2)*(-4 + 3*delta*rho*t)*pow(theta - y,3) +
                                                                                    e3kt*pow(theta,2)*
                                                                                    ((18 - 40*pow(rho,2) - delta*rho*t + 2*delta*pow(rho,3)*t)*theta +
                                                                                        12*(-1 + 2*pow(rho,2))*y) +
                                                                                        2*ekt*((-9 + 36*pow(rho,2) + 19*delta*pow(rho,3)*t)*pow(theta,3) +
                                                                                            2*(9 - 30*pow(rho,2) + 7*delta*pow(rho,3)*t)*pow(theta,2)*y +
                                                                                            (-8 + 20*pow(rho,2) + delta*rho*t - 46*delta*pow(rho,3)*t)*theta*pow(y,2) +
                                                                                            pow(rho,2)*(4 + 13*delta*rho*t)*pow(y,3)) +
                                                                                            e2kt*(8*theta*y*(-3*theta + 2*y) +
                                                                                                delta*rho*t*theta*(7*pow(theta,2) - 23*theta*y + 8*pow(y,2)) -
                                                                                                8*pow(rho,2)*(6*pow(theta,3) - 18*pow(theta,2)*y + 11*theta*pow(y,2) -
                                                                                                    pow(y,3)) + 4*delta*pow(rho,3)*t*
                                                                                                    (-13*pow(theta,3) + 31*pow(theta,2)*y - 14*theta*pow(y,2) + pow(y,3))))))/
                                                                                                    (64.*pow(kappa,2)*t*sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/
                                                                                                        (kappa*t))*pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,
                                                                                                            4));
        }

        private double z3(double t, double kappa, double theta,
                          double delta, double y, double rho) {
            return (pow(delta,3)*ekt*rho*((-15*(2 + kappa*t) +
                3*e4kt*(50 - 79*kappa*t + 35*pow(kappa,2)*pow(t,2) -
                    6*pow(kappa,3)*pow(t,3) +
                    8*pow(rho,2)*(-18 + 15*kappa*t - 6*pow(kappa,2)*pow(t,2) +
                        pow(kappa,3)*pow(t,3))) +
                        ekt*(-3*(20 + 86*kappa*t + 29*pow(kappa,2)*pow(t,2)) +
                            pow(rho,2)*(432 + 936*kappa*t + 552*pow(kappa,2)*pow(t,2) +
                                92*pow(kappa,3)*pow(t,3))) +
                                e2kt*(360 + 324*kappa*t - 261*pow(kappa,2)*pow(t,2) -
                                    48*pow(kappa,3)*pow(t,3) -
                                    4*pow(rho,2)*(324 + 378*kappa*t - 12*pow(kappa,2)*pow(t,2) -
                                        2*pow(kappa,3)*pow(t,3) + 23*pow(kappa,4)*pow(t,4))) +
                                        e3kt*(3*(-140 + 62*kappa*t + 81*pow(kappa,2)*pow(t,2) -
                                            38*pow(kappa,3)*pow(t,3) + 8*pow(kappa,4)*pow(t,4)) +
                                            4*pow(rho,2)*(324 + 54*kappa*t - 114*pow(kappa,2)*pow(t,2) +
                                                77*pow(kappa,3)*pow(t,3) - 19*pow(kappa,4)*pow(t,4) +
                                                2*pow(kappa,5)*pow(t,5))))*pow(theta,3) +
                                                (15*(7 + 4*kappa*t) + 3*e4kt*
                                                    (-79 + 70*kappa*t - 18*pow(kappa,2)*pow(t,2) +
                                                        24*pow(rho,2)*(5 - 4*kappa*t + pow(kappa,2)*pow(t,2))) -
                                                        3*ekt*(26 - 200*kappa*t - 87*pow(kappa,2)*pow(t,2) +
                                                            4*pow(rho,2)*(30 + 142*kappa*t + 115*pow(kappa,2)*pow(t,2) +
                                                                23*pow(kappa,3)*pow(t,3))) +
                                                                2*e2kt*(3*(-66 - 195*kappa*t + 63*pow(kappa,2)*pow(t,2) +
                                                                    16*pow(kappa,3)*pow(t,3)) +
                                                                    4*pow(rho,2)*(135 + 390*kappa*t - 9*pow(kappa,2)*pow(t,2) -
                                                                        48*pow(kappa,3)*pow(t,3) + 23*pow(kappa,4)*pow(t,4))) +
                                                                        e3kt*(606 + 300*kappa*t - 585*pow(kappa,2)*pow(t,2) +
                                                                            210*pow(kappa,3)*pow(t,3) - 24*pow(kappa,4)*pow(t,4) -
                                                                            4*pow(rho,2)*(270 + 282*kappa*t - 345*pow(kappa,2)*pow(t,2) +
                                                                                153*pow(kappa,3)*pow(t,3) - 29*pow(kappa,4)*pow(t,4) +
                                                                                2*pow(kappa,5)*pow(t,5))))*pow(theta,2)*y +
                                                                                (-93 - 75*kappa*t + 3*e4kt*
                                                                                    (35 - 18*kappa*t + 24*pow(rho,2)*(-2 + kappa*t)) +
                                                                                    3*ekt*(58 - 123*kappa*t - 86*pow(kappa,2)*pow(t,2) +
                                                                                        4*pow(rho,2)*(12 + 80*kappa*t + 92*pow(kappa,2)*pow(t,2) +
                                                                                            23*pow(kappa,3)*pow(t,3))) +
                                                                                            e3kt*(-3*(74 + 137*kappa*t - 100*pow(kappa,2)*pow(t,2) +
                                                                                                16*pow(kappa,3)*pow(t,3)) -
                                                                                                16*pow(rho,2)*(-27 - 51*kappa*t + 45*pow(kappa,2)*pow(t,2) -
                                                                                                    12*pow(kappa,3)*pow(t,3) + pow(kappa,4)*pow(t,4))) +
                                                                                                    e2kt*(36 + 909*kappa*t - 42*pow(kappa,2)*pow(t,2) -
                                                                                                        60*pow(kappa,3)*pow(t,3) -
                                                                                                        4*pow(rho,2)*(108 + 462*kappa*t + 96*pow(kappa,2)*pow(t,2) -
                                                                                                            117*pow(kappa,3)*pow(t,3) + 23*pow(kappa,4)*pow(t,4))))*theta*pow(y,2)
                                                                                                            + 2*(9 + 3*e4kt*(-3 + 4*pow(rho,2)) + 15*kappa*t +
                                                                                                                e2kt*(-3*kappa*t*(33 + 10*kappa*t) +
                                                                                                                    pow(rho,2)*(36 + 192*kappa*t + 96*pow(kappa,2)*pow(t,2) -
                                                                                                                        46*pow(kappa,3)*pow(t,3))) +
                                                                                                                        e3kt*(18 + 57*kappa*t - 12*pow(kappa,2)*pow(t,2) -
                                                                                                                            2*pow(rho,2)*(18 + 48*kappa*t - 21*pow(kappa,2)*pow(t,2) +
                                                                                                                                2*pow(kappa,3)*pow(t,3))) +
                                                                                                                                ekt*(3*(-6 + 9*kappa*t + 14*pow(kappa,2)*pow(t,2)) -
                                                                                                                                    2*pow(rho,2)*(6 + 48*kappa*t + 69*pow(kappa,2)*pow(t,2) +
                                                                                                                                        23*pow(kappa,3)*pow(t,3))))*pow(y,3)))/
                                                                                                                                        (96.*kappa*t*sqrt((-theta + kappa*t*theta + (theta - y)/ekt + y)/(kappa*t))*
                                                                                                                                            pow((1 + ekt*(-1 + kappa*t))*theta + (-1 + ekt)*y,5));
        }
    }

    /*! Small-time expansion from
        "The small-time smile and term structure of implied volatility
        under the Heston model" M Forde, A Jacquier, R Lee - SIAM
        Journal on Financial Mathematics, 2012 - SIAM
    */
    public static class FordeHestonExpansion implements HestonExpansion {
        private final double[] coeffs = new double[5];

        public FordeHestonExpansion(double kappa, double theta,
                                    double sigma, double v0,
                                    double rho, double term) {
            double v0Sqrt = sqrt(v0);
            double rhoBarSquare = 1 - rho * rho;
            double sigma00 = v0Sqrt;
            double sigma01 = v0Sqrt * (rho * sigma / (4 * v0)); //term in x
            double sigma02 = v0Sqrt * ((1 - 5 * rho * rho / 2) / 24 * sigma * sigma/ (v0 * v0)); //term in x*x
            double a00 = -sigma * sigma / 12 * (1 - rho * rho / 4) + v0 * rho * sigma / 4 + kappa / 2 * (theta - v0);
            double a01 = rho * sigma / (24 * v0) * (sigma * sigma * rhoBarSquare - 2 * kappa * (theta + v0) + v0 * rho * sigma); //term in x
            double a02 = (176 * sigma * sigma - 480 * kappa * theta - 712 * rho * rho * sigma * sigma + 521 * rho * rho * rho * rho * sigma * sigma + 40 * sigma * rho * rho * rho * v0 + 1040 * kappa * theta * rho * rho - 80 * v0 * kappa * rho * rho) * sigma * sigma / (v0 * v0 * 7680);
            coeffs[0] = sigma00*sigma00+a00*term;
            coeffs[1] = sigma00*sigma01*2+a01*term;
            coeffs[2] = sigma00*sigma02*2+sigma01*sigma01+a02*term;
            coeffs[3] = sigma01*sigma02*2;
            coeffs[4] = sigma02*sigma02;
        }

        @Override
        public double impliedVolatility(final double strike,
                                        final double forward) {
            double x = log(strike/forward);
            double var = coeffs[0]+x*(coeffs[1]+(x*(coeffs[2]+x*(coeffs[3]+(x*coeffs[4])))));
            var = Math.max(1e-8,var);
            return sqrt(var);
        }
    }

    // Numeric helpers: named as in the official C++ so that the transcribed
    // expressions above stay literally identical to the QuantLib source.
    private static double fastpow(double x, int y) {
        return pow(x,y);
    }

    private static double pow(double x, double y) {
        return Math.pow(x, y);
    }

    private static double exp(double x) {
        return Math.exp(x);
    }

    private static double log(double x) {
        return Math.log(x);
    }

    private static double sqrt(double x) {
        return Math.sqrt(x);
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
}
