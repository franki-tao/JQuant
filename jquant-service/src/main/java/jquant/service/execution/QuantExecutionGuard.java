package jquant.service.execution;

import jakarta.annotation.PreDestroy;
import jquant.SavedSettings;
import jquant.indexes.IndexManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Serializes JQuanlib calculations and restores global state ({@code Settings}
 * and {@code IndexManager}) after every request.
 *
 * <p>Because JQuanlib currently uses process-wide singletons for evaluation
 * date, observable settings, and historical fixings, all pricing endpoints
 * execute through this guard. Once JQuanlib supports thread-isolated
 * evaluation contexts, only this class needs to change.
 */
@Component
public class QuantExecutionGuard {

    private final ExecutorService executor;
    private final long timeoutSeconds;

    public QuantExecutionGuard(@Value("${jquant.execution.timeout-seconds:15}") long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jquant-pricing-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public <T> T execute(Supplier<T> task) {
        Future<T> future = executor.submit(() -> {
            SavedSettings saved = new SavedSettings();
            try {
                return task.get();
            } finally {
                IndexManager.Instance.clearHistories();
                saved.restore();
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("Pricing calculation exceeded timeout of " + timeoutSeconds + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pricing calculation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Pricing calculation failed: " + cause.getMessage(), cause);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
