package jquant.pricingengines.vanilla;

import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.EuropeanOption;
import jquant.math.Array;
import jquant.methods.lattices.BlackScholesLattice;
import jquant.methods.lattices.CoxRossRubinstein;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * European Cox-Ross-Rubinstein binomial engine.
 *
 * <p>Builds a {@link CoxRossRubinstein} tree on the existing
 * Black-Scholes-Merton process, rolls the vanilla payoff back with
 * {@link BlackScholesLattice}, and returns the value at time 0.
 * American and Bermudan exercise are not applied.
 */
public class CRREuropeanEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final int steps_;
    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public CRREuropeanEngine(GeneralizedBlackScholesProcess process, int steps) {
        QL_REQUIRE(steps > 0, "steps (" + steps + ") must be positive");
        process_ = process;
        steps_ = steps;
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
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European, "not an European option");
        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff, "non-vanilla payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;
        Date exerciseDate = arguments_.exercise.lastDate();
        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        double riskFreeDiscount = riskFree.discount(exerciseDate, false);
        double maturity = riskFree.dayCounter().yearFraction(
                riskFree.referenceDate(), exerciseDate, new Date(), new Date());
        QL_REQUIRE(maturity > 0.0, "non-positive time to expiry");
        double rate = -Math.log(riskFreeDiscount) / maturity;

        CoxRossRubinstein tree = new CoxRossRubinstein(process_, maturity, steps_, payoff.strike());
        BlackScholesLattice lattice = new BlackScholesLattice(tree, rate, maturity, steps_);

        Array values = new Array(steps_ + 1);
        for (int j = 0; j <= steps_; j++) {
            values.set(j, payoff.value(tree.underlying(steps_, j)));
        }
        for (int i = steps_ - 1; i >= 0; i--) {
            Array rolled = new Array(i + 1);
            lattice.stepback(i, values, rolled);
            values = rolled;
        }
        results_.value = values.get(0);
        results_.valuationDate = riskFree.referenceDate();
        results_.errorEstimate = Double.NaN;
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
