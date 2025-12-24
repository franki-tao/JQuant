package jquant.patterns;

import java.util.HashSet;
import java.util.Set;

public abstract class Observer {
    // 模拟 C++ 的 observables_ 集合，用于追踪自己观察了谁
    private final Set<Observable> registeredObservables = new HashSet<>();

    public final void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    public final void unregisterWithAll() {
        for (Observable o : registeredObservables) {
            o.unregisterObserver(this);
        }
        registeredObservables.clear();
    }

    // 子类必须实现的业务逻辑
    public abstract void update();
}
