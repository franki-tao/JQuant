package jquant.pricingengines.vanilla;

import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.EuropeanOption;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic Black-Scholes-Merton European engine.
 *
 * <p>Discount defaults to the risk-free curve inside the process. The forward
 * is {@code spot * dividendDiscount / riskFreeDiscount}. Variance, discounts
 * and the year fraction used for rho come from the existing term structures.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/vanilla/analyticeuropeanengine.cpp
 */
public class AnalyticEuropeanEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public AnalyticEuropeanEngine(GeneralizedBlackScholesProcess process) {
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
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-vanilla payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;
        Date exerciseDate = arguments_.exercise.lastDate();

        double variance = process_.blackVolatility().getValue()
                .blackVariance(exerciseDate, payoff.strike(), false);
        double dividendDiscount = process_.dividendYield().getValue().discount(exerciseDate, false);
        YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        double riskFreeDiscount = riskFree.discount(exerciseDate, false);
        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        double forwardPrice = spot * dividendDiscount / riskFreeDiscount;

        BlackCalculator black = new BlackCalculator(
                payoff, forwardPrice, Math.sqrt(variance), riskFreeDiscount);
        results_.value = black.value();
        results_.delta = black.delta(spot);
        results_.gamma = black.gamma(spot);
        results_.elasticity = black.elasticity(spot);

        DayCounter rfdc = riskFree.dayCounter();
        double t = rfdc.yearFraction(riskFree.referenceDate(), exerciseDate, new Date(), new Date());
        results_.rho = black.rho(t);

        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        t = divdc.yearFraction(process_.dividendYield().getValue().referenceDate(),
                exerciseDate, new Date(), new Date());
        results_.dividendRho = black.dividendRho(t);

        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        t = voldc.yearFraction(process_.blackVolatility().getValue().referenceDate(),
                exerciseDate, new Date(), new Date());
        results_.vega = black.vega(t);
        results_.theta = black.theta(spot, t);
        results_.thetaPerDay = black.thetaPerDay(spot, t);
        results_.valuationDate = riskFree.referenceDate();
        results_.errorEstimate = Double.NaN;
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
