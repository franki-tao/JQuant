package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.Payoff;
import jquant.PricingEngine;
import jquant.math.Function;
import jquant.math.integrals.SegmentIntegral;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;

/**
 * Pricing engine for European vanilla options using the integral approach.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/vanilla/integralengine.cpp
 */
public class IntegralEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    private static class Integrand implements Function {
        private final Payoff payoff_;
        private final double s0_;
        private final double drift_;
        private final double variance_;

        Integrand(Payoff payoff, double s0, double drift, double variance) {
            payoff_ = payoff;
            s0_ = s0;
            drift_ = drift;
            variance_ = variance;
        }

        @Override
        public double value(double x) {
            double temp = s0_ * Math.exp(x);
            double result = payoff_.value(temp);
            return result * Math.exp(-(x - drift_) * (x - drift_) / (2.0 * variance_));
        }
    }

    public IntegralEngine(GeneralizedBlackScholesProcess process) {
        process_ = process;
        results_.additionalResults = new HashMap<>();
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

    public GeneralizedBlackScholesProcess process() {
        return process_;
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
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European, "not an European option");
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff, "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        Date exerciseDate = arguments_.exercise.lastDate();

        double variance = process_.blackVolatility().getValue()
                .blackVariance(exerciseDate, payoff.strike(), false);
        double dividendDiscount = process_.dividendYield().getValue().discount(exerciseDate, false);
        YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        double riskFreeDiscount = riskFree.discount(exerciseDate, false);
        double drift = Math.log(dividendDiscount / riskFreeDiscount) - 0.5 * variance;

        Integrand f = new Integrand(arguments_.payoff,
                process_.stateVariable().getValue().value(), drift, variance);
        SegmentIntegral integrator = new SegmentIntegral(5000);

        double infinity = 10.0 * Math.sqrt(variance);
        results_.value = riskFreeDiscount
                / Math.sqrt(2.0 * M_PI * variance)
                * integrator.value(f, drift - infinity, drift + infinity);
    }

    @Override
    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers_.remove(o);
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
