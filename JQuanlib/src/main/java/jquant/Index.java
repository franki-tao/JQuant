package jquant;

import jquant.indexes.IndexManager;
import jquant.indexes.util.FuncDateValid;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.time.Calendar;
import jquant.time.Date;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! purely virtual base class for indexes
/*! \warning this class performs no check that the
             provided/requested fixings are for dates in the past,
             i.e. for dates less than or equal to the evaluation
             date. It is up to the client code to take care of
             possible inconsistencies due to "seeing in the
             future"
*/
public abstract class Index implements Observer, Observable {
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();
    //! Returns the name of the index.
    public abstract String name();
    //! returns the calendar defining valid fixing dates
    public abstract Calendar fixingCalendar();
    //! returns TRUE if the fixing date is a valid one
    public abstract boolean isValidFixingDate(final Date fixingDate);
    //! returns whether a historical fixing was stored for the given date
    public boolean hasHistoricalFixing(final Date fixingDate) {
        return IndexManager.Instance.hasHistoricalFixing(name(), fixingDate);
    }
    //! returns the fixing at the given date
    /*! the date passed as arguments must be the actual calendar
        date of the fixing; no settlement days must be used.
        forecastTodaysFixing = false
    */
    public abstract double fixing(final Date fixingDate, boolean forecastTodaysFixing);
    //! returns a past fixing at the given date
    /*! the date passed as arguments must be the actual calendar
        date of the fixing; no settlement days must be used.
    */
    public double pastFixing(final Date fixingDate) {
        QL_REQUIRE(isValidFixingDate(fixingDate), fixingDate + " is not a valid fixing date");
        return timeSeries().getValue(fixingDate);
    }
    //! returns the fixing TimeSeries
    public final TimeSeries<Double> timeSeries() {
        return IndexManager.Instance.getHistory(name());
    }
    //! check if index allows for native fixings.
    /*! If this returns false, calls to addFixing and similar
        methods will raise an exception.
    */
    public boolean allowsNativeFixings() { return true; }
    //! stores the historical fixing at the given date
    /*! the date passed as arguments must be the actual calendar
        date of the fixing; no settlement days must be used.
    */
    @Override
    public void update() {
        notifyObservers();
    }
    //default forceOverwrite = false
    public void addFixing(final Date fixingDate, double fixing, boolean forceOverwrite) {
        checkNativeFixingsAllowed();
        List<Date> dates = Arrays.asList(fixingDate, fixingDate.add(1));
        List<Double> values = List.of(fixing);
        addFixings(dates, values, forceOverwrite);
    }
    //! stores historical fixings from a TimeSeries
    /*! the dates in the TimeSeries must be the actual calendar
        dates of the fixings; no settlement days must be used.
    */
    // default forceOverwrite = false
    public void addFixings(final TimeSeries<Double> t,
                           boolean forceOverwrite) {
        checkNativeFixingsAllowed();
        // is there a way of iterating over dates and values
        // without having to make a copy?
        List<Date> dates = t.dates();
        List<Double> values = t.values();
        addFixings(dates, values,
                forceOverwrite);
    }
    //! stores historical fixings at the given dates
    /*! the dates passed as arguments must be the actual calendar
        dates of the fixings; no settlement days must be used.
    */
    // default forceOverwrite = false
    public void addFixings(List<Date> dates,
                           List<Double> values,
                           boolean forceOverwrite) {
        checkNativeFixingsAllowed();
        IndexManager.Instance.addFixings(
                name(), dates, values, forceOverwrite, this::isValidFixingDate);
    }
    //! clears all stored historical fixings
    public void clearFixings() {
        checkNativeFixingsAllowed();
        IndexManager.Instance.clearHistory(name());
    }
    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable o) {
        if (o != null) {
            o.unregisterObserver(this);
            registeredObservables.remove(o);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable o : registeredObservables) {
            o.unregisterObserver(this);
        }
        registeredObservables.clear();
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }
    protected Observable notifier() {
        return IndexManager.Instance.notifier(name());
    }
    //! check if index allows for native fixings
    private void checkNativeFixingsAllowed() {
        QL_REQUIRE(allowsNativeFixings(),
                "native fixings not allowed for " + name()
                        + "; refer to underlying indices instead");
    }
}
