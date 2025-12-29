package jquant.patterns;

import java.util.HashSet;
import java.util.Set;

public interface Observable {
    // private final Set<Observer> observers = new HashSet<>();

    void registerObserver(Observer o);
//    {
//        observers.add(o);
//    }

    void unregisterObserver(Observer o);
//    {
//        observers.remove(o);
//    }

    void notifyObservers();
//    {
//        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
//            ObservableSettings.getInstance().registerDeferred(observers);
//        } else {
//            for (Observer observer : observers) {
//                try {
//                    observer.update();
//                } catch (Exception e) {
//                    System.err.println("Error notifying observer: " + e.getMessage());
//                }
//            }
//        }
//    }
}
