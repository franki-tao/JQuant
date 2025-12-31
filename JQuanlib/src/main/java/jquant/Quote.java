package jquant;

import jquant.impl.HandleImpl;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;

import java.util.HashSet;
import java.util.Set;

//! purely virtual base class for market observables
/*! \test the observability of class instances is tested.
 */
public abstract class Quote implements Observable, HandleImpl {
    private final Set<Observer> observers = new HashSet<>();
    //! returns the current value
    public abstract double value();
    //! returns true if the Quote holds a valid value
    public abstract boolean isValid();

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }
}
