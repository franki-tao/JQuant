package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for rate helpers whose dates depend on the evaluation date.
 *
 * <p>Port of QuantLib's {@code RelativeDateBootstrapHelper} in
 * {@code ql/termstructures/bootstraphelper.hpp}. The official class is an
 * observer of the evaluation date and, on a date change, rebuilds its dates
 * and notifies its own observers. In this port {@link RateHelper} is not an
 * {@code Observable}, so this class registers itself as an observer of the
 * evaluation date but has no helper observers to forward the notification to.
 *
 * <p>The evaluation date is stored as a copy: Java {@code Date} is mutable and
 * must never alias the live {@code Settings} date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/bootstraphelper.hpp
 */
public abstract class RelativeDateRateHelper extends RateHelper implements Observer {
    private final Set<Observable> registeredObservables = new HashSet<>();
    private boolean datesStale_ = false;

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
        Date currentEvaluationDate = Settings.instance.evaluationDate().Date();
        if (updateDates_ && TimeUtils.neq(evaluationDate_, currentEvaluationDate)) {
            evaluationDate_ = currentEvaluationDate.copy();
            // initializeDates() builds instruments (and their coupons), which
            // register themselves with the evaluation date. The port's
            // DateProxy notifies observers by iterating its observer set
            // directly, so rebuilding here would modify that set during the
            // iteration and throw ConcurrentModificationException. Mark the
            // dates stale instead and rebuild on the next accessor; the
            // official helper's observers are notified before any of them
            // can read dates anyway.
            datesStale_ = true;
        }
        // Official BootstrapHelper::update() then notifies the helper's
        // observers; RateHelper is not Observable in this port, so there is
        // nothing to notify here.
    }

    /** Rebuild dates on first use after the evaluation date changed. */
    protected void ensureDates() {
        if (datesStale_) {
            datesStale_ = false;
            initializeDates();
        }
    }

    @Override
    public Date earliestDate() {
        ensureDates();
        return super.earliestDate();
    }

    @Override
    public Date latestRelevantDate() {
        ensureDates();
        return super.latestRelevantDate();
    }

    @Override
    public Date pillarDate() {
        ensureDates();
        return super.pillarDate();
    }

    @Override
    public Date maturityDate() {
        ensureDates();
        return super.maturityDate();
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
