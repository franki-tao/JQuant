package jquant.cashflows;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;

import java.util.HashSet;
import java.util.Set;

public abstract class FloatingRateCouponPricer implements Observable, Observer, AcyclicVisitor {
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    public abstract double swapletPrice();

    public abstract double swapletRate();

    public abstract double capletPrice(double effectiveCap);

    public abstract double capletRate(double effectiveCap);

    public abstract double floorletPrice(double effectiveFloor);

    public abstract double floorletRate(double effectiveFloor);

    public abstract void initialize(final FloatingRateCoupon coupon);


    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable o) {
        if (o != null) {
            o.unregisterObserver(this);
            registeredObservables.remove(o);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable o : registeredObservables) {
            o.unregisterObserver(this);
        }
        registeredObservables.clear();
    }

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
