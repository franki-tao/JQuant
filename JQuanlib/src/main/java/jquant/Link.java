package jquant;

import jquant.impl.HandleImpl;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Link<T extends Observable & HandleImpl> implements Observer, Observable {
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();
    private T h_;
    private boolean isObserver_ = false;
    // 注意此处支持h为引用输入，以及final输入，具体使用时候要加以区分
    public Link(T h, boolean registerAsObserver) {
        linkTo(h, registerAsObserver);
    }

    public void linkTo(T h, boolean registerAsObserver) {
        if ((!Objects.equals(h, this.h_)) || (isObserver_ != registerAsObserver)) {
            if (h_ != null && isObserver_)
                unregisterWith(h_);
            h_ = h;
            isObserver_ = registerAsObserver;
            if (h_ != null && isObserver_)
                registerWith(h_);
            notifyObservers();
        }
    }

    public boolean empty() {
        return h_ == null;
    }

    public T currentLink() {
        return h_;
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
    public void update() {
        notifyObservers();
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
