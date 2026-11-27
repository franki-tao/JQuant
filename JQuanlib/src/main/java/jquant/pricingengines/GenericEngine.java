package jquant.pricingengines;

import jquant.PricingEngine;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for pricing engines holding an arguments/result pair.
 *
 * <p>Ported from QuantLib {@code ql/pricingengine.hpp}
 * ({@code GenericEngine<ArgumentsType, ResultsType>}).
 */
public abstract class GenericEngine<
        A extends PricingEngine.arguments,
        R extends PricingEngine.results> implements PricingEngine, Observer {
    protected final A arguments_;
    protected final R results_;
    private final Set<Observer> observers_ = new HashSet<>();
    private final Set<Observable> registeredObservables_ = new HashSet<>();

    protected GenericEngine(A arguments, R results) {
        arguments_ = arguments;
        results_ = results;
    }

    @Override
    public A getArguments() {
        return arguments_;
    }

    @Override
    public R getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.reset();
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

    /**
     * Official {@code GenericEngine} is an {@code Observer}. Register {@code this}
     * (not an anonymous wrapper) so the engine destructor / {@code unregisterWithAll}
     * can detach from process-wide observables such as a discount curve or a
     * temporary constant-volatility structure.
     */
    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables_.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable o) {
        if (o != null) {
            o.unregisterObserver(this);
            registeredObservables_.remove(o);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable o : registeredObservables_) {
            o.unregisterObserver(this);
        }
        registeredObservables_.clear();
    }
}
