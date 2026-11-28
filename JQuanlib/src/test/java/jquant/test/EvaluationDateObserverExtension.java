package jquant.test;

import jquant.Settings;
import jquant.patterns.Observer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Suite-wide evaluation-date Observer teardown.
 *
 * <p>Official C++ Observer destructors unregister at the end of each test
 * function. Java Observables keep strong {@code Set<Observer>} references, so
 * instruments and term structures created by one test class remain registered
 * with {@code Settings.evaluationDate} and can exhaust the heap later in a
 * long Surefire JVM (DON-36 / DON-39 / DON-40).
 *
 * <p>This extension snapshots the evaluation-date observer set before each
 * test and unregisters only observers that test added. It is auto-registered
 * via {@code junit.jupiter.extensions.autodetection.enabled} so existing
 * official test classes are not rewritten. Protected test sources are not
 * modified.
 */
public class EvaluationDateObserverExtension
        implements BeforeEachCallback, AfterEachCallback {

    private static final Namespace NS =
            Namespace.create(EvaluationDateObserverExtension.class);
    private static final String SNAPSHOT_KEY = "evalDateObserversAtStart";

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NS).put(SNAPSHOT_KEY, snapshotEvalDateObservers());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterEach(ExtensionContext context) {
        final Set<Observer> atStart =
                (Set<Observer>) context.getStore(NS).get(SNAPSHOT_KEY);
        final Set<Observer> added = snapshotEvalDateObservers();
        if (atStart != null) {
            added.removeAll(atStart);
        }
        for (Observer o : added) {
            try {
                o.unregisterWithAll();
            } catch (RuntimeException ignored) {
                // Teardown must not hide the test result or stop cleaning
                // the remaining observers.
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Set<Observer> snapshotEvalDateObservers() {
        final Object observable = Settings.instance.evaluationDate();
        Class<?> c = observable.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField("observers");
                f.setAccessible(true);
                final Object v = f.get(observable);
                if (v instanceof Set) {
                    return new HashSet<>((Set<Observer>) v);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            c = c.getSuperclass();
        }
        return new HashSet<>();
    }
}
