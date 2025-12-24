package jquant.patterns;
import java.util.*;

public class ObservableSettings {
    private static final ObservableSettings instance = new ObservableSettings();
    private boolean updatesEnabled = true;
    private boolean updatesDeferred = false;
    // 存储等待更新的观察者
    private final Set<Observer> deferredObservers = new LinkedHashSet<>();

    public static ObservableSettings getInstance() { return instance; }

    public void disableUpdates(boolean deferred) {
        this.updatesEnabled = false;
        this.updatesDeferred = deferred;
    }

    public void enableUpdates() {
        updatesEnabled = true;
        updatesDeferred = false;
        applyDeferredUpdates();
    }

    public void registerDeferred(Collection<Observer> observers) {
        if (updatesDeferred) {
            deferredObservers.addAll(observers);
        }
    }

    private void applyDeferredUpdates() {
        if (!deferredObservers.isEmpty()) {
            List<Observer> toUpdate = new ArrayList<>(deferredObservers);
            deferredObservers.clear();
            for (Observer observer : toUpdate) {
                observer.update();
            }
        }
    }

    public boolean isUpdatesEnabled() { return updatesEnabled; }
    public boolean isUpdatesDeferred() { return updatesDeferred; }
}
