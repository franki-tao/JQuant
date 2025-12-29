package jquant.patterns;

import java.util.HashSet;
import java.util.Set;

public interface Observer {

    void registerWith(Observable observable);
//    {
//        if (observable != null) {
//            observable.registerObserver(this);
//            registeredObservables.add(observable);
//        }
//    }

    void unregisterWithAll();
//    {
//        for (Observable o : registeredObservables) {
//            o.unregisterObserver(this);
//        }
//        registeredObservables.clear();
//    }

    void unregisterWith(final Observable o);

    // 子类必须实现的业务逻辑
    void update();
}
