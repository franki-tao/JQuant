package jquant.indexes;

import jquant.TimeSeries;
import jquant.indexes.util.FuncDateValid;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.patterns.Singleton;
import jquant.time.Date;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

//! global repository for past index fixings
public class IndexManager implements Singleton<IndexManager> {
    public static IndexManager Instance = new IndexManager();
    private TreeMap<String, TimeSeries<Double>> data_;
    private TreeMap<String, Observable> notifiers_;
    private IndexManager() {
        data_ = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        notifiers_ = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
    //! returns all names of the indexes for which fixings were stored
    public List<String> histories() {
        return data_.keySet().stream().toList();
    }
    //! clears all stored fixings
    public void clearHistories() {
        data_.clear();
        for (Observable notifier : notifiers_.values())
            notifier.notifyObservers();
    }
    // deprecated in order to be moved into the private section
    /*! \deprecated Use Index::hasHistoricalFixing instead.
                    Deprecated in version 1.37.
    */
    public boolean hasHistory(String name) {
        return data_.containsKey(name);
    }
    /*! \deprecated Use Index::timeSeries instead.
                    Deprecated in version 1.37.
    */
    public final TimeSeries<Double> getHistory(final String name) {
        TimeSeries<Double> history = data_.get(name);
        return history == null ? new TimeSeries<>() : history;
    }
    /*! \deprecated Use Index::clearFixings instead.
                    Deprecated in version 1.37.
    */
    public void clearHistory(final String name) {
        data_.remove(name);
        Observable notifier = notifiers_.get(name);
        if (notifier != null)
            notifier.notifyObservers();
    }
    /*! \deprecated Use Index::hasHistoricalFixing instead.
                    Deprecated in version 1.37.
    */
    public boolean hasHistoricalFixing(final String name, final Date fixingDate) {
        TimeSeries<Double> history = data_.get(name);
        return history != null && history.getValue(fixingDate) != null;
    }
    /*! \deprecated Use Index::addFixings instead.
                    Deprecated in version 1.37.
    */
    public void setHistory(final String name, final TimeSeries<Double> fixing) {
        QL_REQUIRE(fixing != null, "null fixing history");
        data_.put(name, fixing);
        notifier(name).notifyObservers();
    }
    /*! \deprecated Register with the relevant index instead.
                    Deprecated in version 1.37.
    */
    public Observable notifier(final String name) {
        return notifiers_.computeIfAbsent(name, key -> new IndexNotifier());
    }

    public void addFixing(final String name,
                          final Date fixingDate,
                          double fixing,
                          boolean forceOverwrite) {
        addFixings(name, List.of(fixingDate), List.of(fixing), forceOverwrite, null);
    }

    public void addFixings(final String name, List<Date> dates, List<Double> values,
                           boolean forceOverwrite, FuncDateValid isValidFixingDate) {
        QL_REQUIRE(dates != null && values != null && dates.size() == values.size(),
                "dates and values must have the same size");
        TimeSeries<Double> history = data_.computeIfAbsent(name, key -> new TimeSeries<>());
        boolean updated = false;
        boolean invalid = false;
        boolean duplicated = false;
        Date invalidDate = new Date();
        Date duplicatedDate = new Date();
        double invalidValue = Double.NaN;
        double duplicatedValue = Double.NaN;
        for (int i = 0; i < dates.size(); ++i) {
            Date date = dates.get(i);
            double value = values.get(i);
            boolean validFixing = isValidFixingDate == null || isValidFixingDate.value(date);
            if (!validFixing) {
                invalid = true;
                invalidDate = date;
                invalidValue = value;
                continue;
            }
            Double current = history.getValue(date);
            if (current == null) {
                history.insert(date, value);
                updated = true;
            } else if (close(current, value)) {
                continue;
            } else if (forceOverwrite) {
                history.insert(date, value);
                updated = true;
            } else {
                duplicated = true;
                duplicatedDate = date;
                duplicatedValue = value;
            }
        }
        if (updated)
            notifier(name).notifyObservers();
        QL_REQUIRE(!invalid, "At least one invalid fixing provided: "
                + invalidDate.weekday() + " " + invalidDate + ", " + invalidValue);
        QL_REQUIRE(!duplicated, "At least one duplicated fixing provided: "
                + duplicatedDate + ", " + duplicatedValue
                + " while " + history.getValue(duplicatedDate)
                + " value is already present");
    }

    private static final class IndexNotifier implements Observable {
        private final Set<Observer> observers = new HashSet<>();

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
                for (Observer observer : Set.copyOf(observers)) {
                    try {
                        observer.update();
                    } catch (Exception e) {
                        System.err.println("Error notifying observer: " + e.getMessage());
                    }
                }
            }
        }
    }
}
