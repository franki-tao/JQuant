package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.methods.finitedifferences.utilities.HestonRNDCalculator;
import jquant.models.equity.HestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.processes.HestonProcess;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic engine for arbitrary European payoffs under the Heston model.
 *
 * <p>Port of
 * {@code ql/pricingengines/vanilla/analyticpdfhestonengine.{hpp,cpp}}.
 *
 * <p>References:
 * The formulas are taken from A. Dragulescu, V. Yakovenko, 2002.
 * Probability distribution of returns in the Heston model
 * with stochastic volatility.
 * http://arxiv.org/pdf/cond-mat/0203046.pdf
 */
public class AnalyticPDFHestonEngine implements PricingEngine {

    private final int maxIntegrationIterations_;
    private final double integrationEps_;

    private final HestonModel model_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public AnalyticPDFHestonEngine(final HestonModel model) {
        this(model, 1e-6, 10000);
    }

    public AnalyticPDFHestonEngine(final HestonModel model,
                                   double gaussLobattoEps) {
        this(model, gaussLobattoEps, 10000);
    }

    public AnalyticPDFHestonEngine(final HestonModel model,
                                   double gaussLobattoEps,
                                   int gaussLobattoIntegrationOrder) {
        maxIntegrationIterations_ = gaussLobattoIntegrationOrder;
        integrationEps_ = gaussLobattoEps;
        model_ = model;

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
        // this is an European option pricer
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        final HestonProcess process = model_.process();

        final double t = process.time(arguments_.exercise.lastDate());

        final double xMax = 8.0 * Math.sqrt(process.theta() * t
                + (process.v0() - process.theta())
                * (1 - Math.exp(-process.kappa() * t)) / process.kappa());

        final double x0 = Math.log(process.s0().getValue().value());
        final double rD = process.riskFreeRate().getValue().discount(t);
        final double qD = process.dividendYield().getValue().discount(t);

        final double drift = x0 + Math.log(rD / qD);

        results_.value = new GaussLobattoIntegral(
                maxIntegrationIterations_, integrationEps_).value(
                x -> weightedPayoff(x, t),
                -xMax + drift, xMax + drift);
    }

    // probability in x_t = ln(s_t)
    public double Pv(double x_t, double t) {
        return new HestonRNDCalculator(
                model_.process(), integrationEps_, maxIntegrationIterations_)
                .pdf(x_t, t);
    }

    // cumulative distribution function Pr(x < X)
    public double cdf(double X, double t) {
        final double x_t = Math.log(X);
        return new HestonRNDCalculator(
                model_.process(), integrationEps_, maxIntegrationIterations_)
                .cdf(x_t, t);
    }

    private double weightedPayoff(double x_t, double t) {
        final double rD
                = model_.process().riskFreeRate().getValue().discount(t);

        final double s_t = Math.exp(x_t);
        final double payoff = arguments_.payoff.value(s_t);

        return (payoff != 0.0) ? payoff * Pv(x_t, t) * rD : 0.0;
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
