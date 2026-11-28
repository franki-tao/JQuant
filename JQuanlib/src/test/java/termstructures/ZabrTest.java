package termstructures;

import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.patterns.Observer;
import jquant.termstructures.volatility.SabrSmileSection;
import jquant.termstructures.volatility.ZabrEvaluation;
import jquant.termstructures.volatility.ZabrSmileSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/zabr.cpp} (suite {@code ZabrTests}).
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the case;
 * the messages are accumulated and reported with a single failure at the end
 * of the case, matching the Boost test semantics.
 */
public class ZabrTest {

    private SavedSettings savedSettings;
    private Set<Observer> evalObserversAtStart;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        evalObserversAtStart = snapshotEvalDateObservers();
    }

    @AfterEach
    void restoreSettings() {
        final Set<Observer> added = snapshotEvalDateObservers();
        if (evalObserversAtStart != null) {
            added.removeAll(evalObserversAtStart);
        }
        for (Observer o : added) {
            o.unregisterWithAll();
        }
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    @SuppressWarnings("unchecked")
    private static Set<Observer> snapshotEvalDateObservers() {
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
        return Set.of();
    }

    @Test
    public void testConsistency() {
        System.out.println("Testing the consistency of ZABR interpolation...");

        final List<String> boostErrors = new ArrayList<>();

        final double tol = 1E-4;

        final double alpha = 0.08;
        final double beta = 0.70;
        final double nu = 0.20;
        final double rho = -0.30;
        final double tau = 5.0;
        final double forward = 0.03;

        SabrSmileSection sabr = new SabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho});

        ZabrSmileSection zabr0 = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                ZabrEvaluation.ZabrShortMaturityLognormal,
                new double[0], 5);

        ZabrSmileSection zabr1 = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                ZabrEvaluation.ZabrShortMaturityNormal,
                new double[0], 5);

        ZabrSmileSection zabr2 = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                ZabrEvaluation.ZabrLocalVolatility,
                new double[0], 5);

        // for full finite prices reduce the number of intermediate points
        // here below the recommended value to speed up the test
        ZabrSmileSection zabr3 = new ZabrSmileSection(tau, forward,
                new double[]{alpha, beta, nu, rho, 1.0},
                ZabrEvaluation.ZabrFullFd,
                new double[0], 2);

        double k = 0.0001;
        while (k <= 0.70) {
            final double c0 = sabr.optionPrice(k, Option.Type.Call, 1.0);
            final double z0 = zabr0.optionPrice(k, Option.Type.Call, 1.0);
            final double z1 = zabr1.optionPrice(k, Option.Type.Call, 1.0);
            final double z2 = zabr2.optionPrice(k, Option.Type.Call, 1.0);
            final double z3 = zabr3.optionPrice(k, Option.Type.Call, 1.0);
            if (Math.abs(z0 - c0) > tol)
                boostErrors.add("Zabr short maturity lognormal expansion price "
                        + "(" + z0 + ") deviates from Sabr Hagan 2002 price "
                        + "by " + (z0 - c0));
            if (Math.abs(z1 - c0) > tol)
                boostErrors.add("Zabr short maturity normal expansion price "
                        + "(" + z1 + ") deviates from Sabr Hagan 2002 price "
                        + "by " + (z1 - c0));
            if (Math.abs(z2 - c0) > tol)
                boostErrors.add("Zabr local volatility price "
                        + "(" + z2 + ") deviates from Sabr Hagan 2002 price "
                        + "by " + (z2 - c0));
            if (Math.abs(z3 - c0) > tol)
                boostErrors.add("Zabr full finite difference price "
                        + "(" + z3 + ") deviates from Sabr Hagan 2002 price "
                        + "by " + (z3 - c0));
            k += 0.0001;
        }

        if (!boostErrors.isEmpty()) {
            fail(String.join("\n", boostErrors));
        }
    }
}
