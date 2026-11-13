package jquant.termstructures;

import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.yield.MultiCurveBootstrap;
import jquant.termstructures.yield.MultiCurveBootstrapContributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Builds a set of curves that form a dependency cycle by solving all of them
 * jointly.
 *
 * <p>Port of QuantLib's {@code MultiCurve} in
 * {@code ql/termstructures/multicurve.cpp}.
 *
 * <p>Member curves are constructed first, using empty "internal" relinkable
 * handles wherever they refer to each other. They are then added here: the
 * internal handle is linked to the curve without observing it (to avoid
 * notification cycles), while the returned "external" handle is what callers
 * should use.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/multicurve.cpp
 */
public class MultiCurve implements Observer {
    private final MultiCurveBootstrap multiCurveBootstrap_;
    private final List<YieldTermStructure> curves_ = new ArrayList<>();
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers_ = new HashSet<>();

    public MultiCurve(double accuracy) {
        multiCurveBootstrap_ = new MultiCurveBootstrap(accuracy);
    }

    public MultiCurve(OptimizationMethod optimizer, EndCriteria endCriteria) {
        multiCurveBootstrap_ = new MultiCurveBootstrap(optimizer, endCriteria);
    }

    public Handle<YieldTermStructure> addBootstrappedCurve(
            RelinkableHandle<YieldTermStructure> internalHandle,
            YieldTermStructure curve) {
        QL_REQUIRE(internalHandle.empty(),
                "internal handle must be empty; was the curve added already?");
        QL_REQUIRE(curve instanceof MultiCurveBootstrapProvider,
                "curve must be a MultiCurveBootstrapProvider");
        MultiCurveBootstrapContributor bootstrap =
                ((MultiCurveBootstrapProvider) curve).multiCurveBootstrapContributor();
        QL_REQUIRE(bootstrap != null,
                "curve does not provide a valid multi curve bootstrap contributor");
        multiCurveBootstrap_.add(bootstrap);
        return addCurve(internalHandle, curve);
    }

    public Handle<YieldTermStructure> addNonBootstrappedCurve(
            RelinkableHandle<YieldTermStructure> internalHandle,
            YieldTermStructure curve) {
        QL_REQUIRE(internalHandle.empty(),
                "internal handle must be empty; was the curve added already?");
        QL_REQUIRE(curve != null, "curve must not be null");
        multiCurveBootstrap_.addObserver(curve);
        return addCurve(internalHandle, curve);
    }

    private Handle<YieldTermStructure> addCurve(
            RelinkableHandle<YieldTermStructure> internalHandle,
            YieldTermStructure curve) {
        internalHandle.linkTo(curve, false);
        Handle<YieldTermStructure> externalHandle = new OwnedHandle(curve, this);
        // As in official MultiCurve::addCurve: observe the member curve's
        // observables (quotes, evaluation date, ...), but not the curve
        // itself, to avoid notification cycles.
        registerWithObservables(curve);
        curves_.add(curve);
        return externalHandle;
    }

    @Override
    public void update() {
        for (YieldTermStructure c : curves_)
            c.update();
    }

    /**
     * Mirrors {@code Observer::registerWithObservables}: registers with every
     * observable the given curve already observes. It deliberately does not
     * register with the curve itself.
     */
    private void registerWithObservables(YieldTermStructure curve) {
        if (curve == null)
            return;
        for (Observable observable : curve.observables())
            registerWith(observable);
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable observable) {
        if (observable != null) {
            observable.unregisterObserver(this);
            registeredObservables.remove(observable);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable observable : registeredObservables)
            observable.unregisterObserver(this);
        registeredObservables.clear();
    }

    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    public void unregisterObserver(Observer o) {
        observers_.remove(o);
    }

    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_))
                observer.update();
        }
    }

    /**
     * External handle. It points at the curve but keeps the owning
     * {@link MultiCurve} alive, mirroring the aliasing shared pointer used by
     * the official implementation.
     */
    private static final class OwnedHandle extends Handle<YieldTermStructure> {
        @SuppressWarnings("unused")
        private final MultiCurve owner_;

        OwnedHandle(YieldTermStructure curve, MultiCurve owner) {
            super(curve, true);
            owner_ = owner;
        }
    }
}
