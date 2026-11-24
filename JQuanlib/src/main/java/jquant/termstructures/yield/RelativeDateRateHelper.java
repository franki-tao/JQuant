package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for rate helpers whose dates depend on the evaluation date.
 *
 * <p>Port of QuantLib's {@code RelativeDateBootstrapHelper} in
 * {@code ql/termstructures/bootstraphelper.hpp}: an observer of the global
 * evaluation date that rebuilds its dates synchronously when that date
 * changes, and whose observers (the curve using the helper) are notified so
 * that they re-bootstrap.
 *
 * <p>In this port {@link RateHelper} is not an {@code Observable}. Curves
 * instead register directly with the observables the helper depends on:
 * {@link RateHelper#notifications()} supplies the quote, and
 * {@link #notificationObservables()} supplies the evaluation date. This is the
 * equivalent of the official bootstrapper's
 * {@code registerWithObservables(helper)} call.
 *
 * <p>The evaluation date is stored as a copy: Java {@code Date} is mutable and
 * must never alias the live {@code Settings} date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/bootstraphelper.hpp
 */
public abstract class RelativeDateRateHelper extends RateHelper implements Observer {
    private final Set<Observable> registeredObservables = new HashSet<>();

    protected Date evaluationDate_;
    protected boolean updateDates_;

    protected RelativeDateRateHelper(Handle<Quote> quote) {
        this(quote, true);
    }

    protected RelativeDateRateHelper(Handle<Quote> quote, boolean updateDates) {
        super(quote);
        updateDates_ = updateDates;
        if (updateDates) {
            registerWith(Settings.instance.evaluationDate());
            evaluationDate_ = Settings.instance.evaluationDate().Date().copy();
        }
    }

    @Override
    public void update() {
        // Official RelativeDateBootstrapHelper::update(): rebuild the dates
        // synchronously when the evaluation date changed. Observers created by
        // initializeDates() register with the evaluation date and with the
        // index while this notification is running; ObservableValue and Index
        // notify over a snapshot so that is safe.
        Date currentEvaluationDate = Settings.instance.evaluationDate().Date();
        if (updateDates_ && TimeUtils.neq(evaluationDate_, currentEvaluationDate)) {
            evaluationDate_ = currentEvaluationDate.copy();
            initializeDates();
        }
        // Official BootstrapHelper::update() then notifies the helper's
        // observers. RateHelper is not Observable here; the curve observes the
        // evaluation date directly through notificationObservables() instead.
    }

    /**
     * Observables a curve must register with to be told about changes that
     * require a re-bootstrap. Mirrors the official bootstrapper registering
     * the curve with all observables of the helper.
     */
    @Override
    public List<Observable> notificationObservables() {
        if (!updateDates_)
            return List.of();
        return List.of(Settings.instance.evaluationDate());
    }

    /** Rebuild the helper dates after the evaluation date changed. */
    protected abstract void initializeDates();

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
}
