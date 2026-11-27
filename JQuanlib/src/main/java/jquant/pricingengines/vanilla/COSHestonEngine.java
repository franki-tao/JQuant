package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.models.equity.HestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.HestonProcess;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.squared;

/**
 * Official QuantLib {@code ql/pricingengines/vanilla/coshestonengine.hpp/.cpp}
 * port.
 *
 * <p>COS-method Heston engine based on efficient Fourier series expansions.
 *
 * <p>References:
 * <ul>
 *   <li>F. Fang, C.W. Oosterlee: A Novel Pricing Method for European Options
 *       based on Fourier-Cosine Series Expansions,
 *       http://ta.twi.tudelft.nl/mf/users/oosterle/oosterlee/COS.pdf</li>
 *   <li>Fabien Le Floc'h: Fourier Integration and Stochastic Volatility
 *       Calibration,
 *       https://papers.ssrn.com/sol3/papers2.cfm?abstract_id=2362968</li>
 * </ul>
 */
public class COSHestonEngine implements PricingEngine {

    private final HestonModel model_;
    private final double L_;
    private final int N_;
    private double kappa_, theta_, sigma_, rho_, v0_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public COSHestonEngine(HestonModel model) {
        this(model, 16, 200);
    }

    public COSHestonEngine(HestonModel model, double L, int N) {
        model_ = model;
        L_ = L;
        N_ = N;
        kappa_ = model.kappa();
        theta_ = model.theta();
        sigma_ = model.sigma();
        rho_ = model.rho();
        v0_ = model.v0();

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
                COSHestonEngine.this.update();
            }
        });
    }

    public void update() {
        kappa_ = model_.kappa();
        theta_ = model_.theta();
        sigma_ = model_.sigma();
        rho_ = model_.rho();
        v0_ = model_.v0();

        notifyObservers();
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
        double maturity = process.time(maturityDate);

        double cum1 = c1(maturity);
        double w = Math.sqrt(Math.abs(c2(maturity))
                // the 4th order doesn't necessarily improve the precision
                // + std::sqrt(std::fabs(c4(maturity)))
        );

        double k = payoff.strike();
        double spot = process.s0().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        double df = process.riskFreeRate().getValue().discount(maturityDate);
        double qf = process.dividendYield().getValue().discount(maturityDate);
        double fwd = spot * qf / df;
        double x = Math.log(fwd / k);

        double a = x + cum1 - L_ * w;
        double b = x + cum1 + L_ * w;

        // Check if it exceeds the truncation bound

        if (x >= b / 2 || x <= a / 2) {
            // returns lower/upper bounds
            if (payoff.optionType() == Option.Type.Put)
                results_.value = Math.max(-spot * qf + k * df, 0.0);
            else if (payoff.optionType() == Option.Type.Call)
                results_.value = Math.max(spot * qf - k * df, 0.0);
            else
                QL_FAIL("unknown payoff type");
            return;
        }

        double d = 1.0 / (b - a);

        double expA = Math.exp(a);
        double s = chF(0, maturity).getReal() * (expA - 1 - a) * d;

        for (int n = 1; n < N_; ++n) {
            double r = n * M_PI * d;
            double U_n = 2.0 * d * (1.0 / (1.0 + r * r)
                    * (expA + r * Math.sin(r * a) - Math.cos(r * a))
                    - 1.0 / r * Math.sin(r * a));

            s += U_n * (chF(r, maturity)
                    .multiply(new Complex(0, r * (x - a)).exp())).getReal();
        }

        if (payoff.optionType() == Option.Type.Put)
            results_.value = k * df * s;
        else if (payoff.optionType() == Option.Type.Call) {
            double qfCall = process.dividendYield().getValue().discount(maturityDate);
            results_.value = spot * qfCall - k * df * (1 - s);
        } else
            QL_FAIL("unknown payoff type");
    }

    private double muT(double t) {
        return Math.log(model_.process().dividendYield().getValue().discount(t)
                / model_.process().riskFreeRate().getValue().discount(t));
    }

    // normalized characteristic function
    public Complex chF(double u, double t) {
        double sigma2 = sigma_ * sigma_;

        Complex g = new Complex(kappa_, -rho_ * sigma_ * u);

        Complex D = g.multiply(g)
                .add(new Complex(u * u, u).multiply(sigma2))
                .sqrt();

        Complex G = g.subtract(D).divide(g.add(D));

        Complex expDt = D.multiply(-t).exp();

        Complex first = Complex.ONE.subtract(expDt)
                .divide(Complex.ONE.subtract(G.multiply(expDt)))
                .multiply(g.subtract(D))
                .multiply(v0_ / sigma2);

        Complex logArg = Complex.ONE.subtract(G.multiply(expDt))
                .divide(Complex.ONE.subtract(G));

        Complex second = g.subtract(D).multiply(t)
                .subtract(logArg.log().multiply(2.0))
                .multiply(kappa_ * theta_ / sigma2);

        return first.add(second).exp();
    }

    /*
     Mathematica program to calculate the n-th cumulant

     d[z_] := Sqrt[(kappa -i*rho*sigma*z)^2 + (z*z+i*z)*sigma^2]

     g[z_] := (kappa -i*rho*sigma*z - d[z])/(kappa -i*rho*sigma*z + d[z])

     phi[z_] := Exp[ v0/(sigma^2)*(1-Exp[-d[z]*t])/(1-g[z]*Exp[-d[z]*t])
         *(kappa -i*rho*sigma*z - d[z]) + kappa*theta/sigma^2
         *((kappa -i*rho*sigma*z-d[z])*t
           -2*Log[(1-g[z]*Exp[-d[z]*t])/(1-g[z]) ]) ]

     e[z_] := Log[phi[-i*z]]

     // for C++ formatting see
     // http://mathematica.stackexchange.com/questions/114175/cform-not-getting-exp-pow-log-functions
     cpp = RawBoxes[Replace[ToBoxes@#,
         InterpretationBox[a_, b_, c___] :>  With[{aa =
         StringReplace[ a, {"Sqrt" -> "std::sqrt", "Power(E," -> "std::exp(",
                            "Power" -> "std::pow"}]}, aa], {0, Infinity}]] &;

     c[n_] := cpp@CForm[FullSimplify[Derivative[n][e][0],
          kappa > 0 && theta > 0 && v0 > 0 && sigma > 0 &&
          rho [Element] {-1, 1} && i^2 == -1]]
     */
    public double c1(double t) {
        return (-theta_ + Math.exp(kappa_ * t)
                * (theta_ - kappa_ * t * theta_
                - v0_) + v0_) / (2 * Math.exp(kappa_ * t) * kappa_);
    }

    public double c2(double t) {
        double sigma2 = sigma_ * sigma_;
        double kappa2 = kappa_ * kappa_;
        double kappa3 = kappa2 * kappa_;

        return (sigma2 * (theta_ - 2 * v0_)
                + Math.exp(2 * kappa_ * t) * (8 * kappa3 * t * theta_
                - 8 * kappa2 * (theta_ + rho_ * sigma_ * t * theta_ - v0_)
                + sigma2 * (-5 * theta_ + 2 * v0_) + 2 * kappa_ * sigma_ * (8 * rho_ * theta_
                + sigma_ * t * theta_ - 4 * rho_ * v0_))
                + 4 * Math.exp(kappa_ * t) * (sigma2 * theta_
                - 2 * kappa2 * (-1 + rho_ * sigma_ * t) * (theta_ - v0_)
                + kappa_ * sigma_ * (sigma_ * t * (theta_ - v0_) + 2 * rho_ * (-2 * theta_
                + v0_)))) / (8. * Math.exp(2 * kappa_ * t) * kappa3);
    }

    public double c3(double t) {
        double sigma2 = sigma_ * sigma_;
        double sigma3 = sigma2 * sigma_;
        double kappa2 = kappa_ * kappa_;
        double kappa3 = kappa2 * kappa_;
        double kappa4 = kappa3 * kappa_;
        double rho2 = rho_ * rho_;

        return
                -(sigma_ * (sigma3 * (theta_ - 3 * v0_)
                        + Math.exp(3 * kappa_ * t) * (2 * (-11 * sigma3
                        - 24 * kappa4 * rho_ * t + 3 * kappa_ * sigma2 * (20 * rho_
                        + sigma_ * t) - 6 * kappa2 * sigma_ * (5 + 3 * rho_ * (4 * rho_ + sigma_ * t))
                        + 12 * kappa3 * (sigma_ * t + 2 * rho_ * (2 + rho_ * sigma_ * t))) * theta_
                        - 6 * (2 * kappa_ * rho_ - sigma_) * (4 * kappa2 - 4 * kappa_ * rho_ * sigma_
                        + sigma2) * v0_) + 6 * Math.exp(kappa_ * t) * sigma_ * (-2 * kappa2 * (-1
                        + rho_ * sigma_ * t) * (theta_ - 2 * v0_) + sigma2 * (theta_ - v0_)
                        + kappa_ * sigma_ * (-4 * rho_ * theta_ + sigma_ * t * theta_ + 6 * rho_ * v0_ - 2 * sigma_ * t * v0_))
                        + 3 * Math.exp(2 * kappa_ * t) * (2 * kappa_ * sigma2 * (-16 * rho_ * theta_
                        + sigma_ * t * (3 * theta_ - v0_)) + 8 * kappa4 * rho_ * t * (-2
                        + rho_ * sigma_ * t) * (theta_ - v0_) + sigma3 * (5 * theta_ + v0_)
                        + 8 * kappa3 * (-(rho_ * (4 + sigma2 * t * t) * theta_) + 2 * sigma_ * t * (theta_ - v0_)
                        + 2 * rho2 * sigma_ * t * (2 * theta_ - v0_) + rho_ * (2
                        + sigma2 * t * t) * v0_) + 2 * kappa2 * sigma_ * ((8
                        + 24 * rho2 - 16 * rho_ * sigma_ * t + sigma2 * t * t) * theta_ - (8 * rho2
                        - 8 * rho_ * sigma_ * t + sigma2 * t * t) * v0_)))) / (16. * Math.exp(3 * kappa_ * t)
                        * kappa_ * kappa4);
    }

    public double c4(double t) {
        double sigma2 = sigma_ * sigma_;
        double sigma3 = sigma2 * sigma_;
        double sigma4 = sigma2 * sigma2;
        double kappa2 = kappa_ * kappa_;
        double kappa3 = kappa2 * kappa_;
        double kappa4 = kappa2 * kappa2;
        double kappa5 = kappa2 * kappa3;
        double kappa6 = kappa3 * kappa3;
        double kappa7 = kappa4 * kappa3;
        double rho2 = rho_ * rho_;
        double rho3 = rho2 * rho_;
        double t2 = t * t;
        double t3 = t2 * t;

        return
                (sigma2 * (3 * sigma4 * (theta_ - 4 * v0_)
                        + 3 * Math.exp(4 * kappa_ * t) * ((-93 * sigma4
                        + 64 * kappa5 * (t + 4 * rho2 * t)
                        + 4 * kappa_ * sigma3 * (176 * rho_ + 5 * sigma_ * t)
                        - 32 * kappa2 * sigma2 * (11 + 50 * rho2
                        + 5 * rho_ * sigma_ * t) + 32 * kappa3 * sigma_ * (3 * sigma_ * t + 4 * rho_ * (10
                        + 8 * rho2 + 3 * rho_ * sigma_ * t)) - 32 * kappa4 * (5
                        + 4 * rho_ * (6 * rho_ + (3 + 2 * rho2) * sigma_ * t))) * theta_
                        + 4 * (4 * kappa2 - 4 * kappa_ * rho_ * sigma_
                        + sigma2) * (4 * kappa2 * (1 + 4 * rho2)
                        - 20 * kappa_ * rho_ * sigma_ + 5 * sigma2) * v0_)
                        + 24 * Math.exp(kappa_ * t) * sigma2 * (-2 * kappa2 * (-1
                        + rho_ * sigma_ * t) * (theta_ - 3 * v0_) + sigma2 * (theta_ - 2 * v0_)
                        + kappa_ * sigma_ * (-4 * rho_ * theta_ + sigma_ * t * theta_ + 10 * rho_ * v0_
                        - 3 * sigma_ * t * v0_)) + 12 * Math.exp(2 * kappa_ * t) * (sigma4 * (7 * theta_
                        - 4 * v0_) + 8 * kappa4 * (1 + 2 * rho_ * sigma_ * t * (-2
                        + rho_ * sigma_ * t)) * (theta_ - 2 * v0_)
                        + 2 * kappa_ * sigma3 * (-24 * rho_ * theta_ + 5 * sigma_ * t * theta_
                        + 20 * rho_ * v0_ - 6 * sigma_ * t * v0_) + 4 * kappa2 * sigma2 * ((6
                        + 20 * rho2 - 14 * rho_ * sigma_ * t
                        + sigma2 * t2) * theta_ - 2 * (3 + 12 * rho2
                        - 10 * rho_ * sigma_ * t + sigma2 * t2) * v0_)
                        + 8 * kappa3 * sigma_ * ((3 * sigma_ * t + 2 * rho_ * (-4 + sigma_ * t * (4 * rho_
                        - sigma_ * t))) * theta_ + 2 * (-3 * sigma_ * t + 2 * rho_ * (3 + sigma_ * t * (-3 * rho_
                        + sigma_ * t))) * v0_))
                        - 8 * Math.exp(3 * kappa_ * t) * (16 * kappa6 * rho2 * t2 * (-3 + rho_ * sigma_ * t) * (theta_ - v0_) - 3 * sigma4 * (7 * theta_
                        + 2 * v0_) + 2 * kappa3 * sigma_ * ((192 * (rho_ + rho3)
                        - 6 * (9 + 40 * rho2) * sigma_ * t + 42 * rho_ * sigma2 * t2
                        - sigma3 * t3) * theta_ + (-48 * rho3 + 18 * (1
                        + 4 * rho2) * sigma_ * t - 24 * rho_ * sigma2 * t2
                        + sigma3 * t3) * v0_) + 12 * kappa4 * ((-4
                        - 24 * rho2 + 8 * rho_ * (4 + 3 * rho2) * sigma_ * t - (3
                        + 14 * rho2) * sigma2 * t2 + rho_ * sigma3 * t3) * theta_ + (8 * rho2
                        - 8 * rho_ * (2 + rho2) * sigma_ * t + (3
                        + 8 * rho2) * sigma2 * t2 - rho_ * sigma3 * t3) * v0_)
                        - 6 * kappa2 * sigma2 * ((15 + 80 * rho2
                        - 35 * rho_ * sigma_ * t + 2 * sigma2 * t2) * theta_ + (3
                        + sigma_ * t * (7 * rho_ - sigma_ * t)) * v0_) + 24 * kappa5 * t * ((-2
                        + rho_ * (4 * sigma_ * t + rho_ * (-8 + sigma_ * t * (4 * rho_ - sigma_ * t)))) * theta_ + (2
                        + rho_ * (-4 * sigma_ * t + rho_ * (4 + sigma_ * t * (-2 * rho_ + sigma_ * t)))) * v0_)
                        + 3 * kappa_ * sigma3 * (sigma_ * t * (-9 * theta_ + v0_) + 10 * rho_ * (6 * theta_
                        + v0_))))) / (64. * Math.exp(4 * kappa_ * t) * kappa7);
    }

    public double mu(double t) {
        return c1(t);
    }

    public double var(double t) {
        return c2(t);
    }

    public double skew(double t) {
        return c3(t) / Math.pow(c2(t), 1.5);
    }

    public double kurtosis(double t) {
        return c4(t) / squared(c2(t));
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
