package jquant.indexes;

import jquant.TimeSeries;
import jquant.indexes.util.FuncDateValid;
import jquant.math.MathUtils;
import jquant.patterns.Observable;
import jquant.patterns.Singleton;
import jquant.time.Date;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

//! global repository for past index fixings
public class IndexManager implements Singleton<IndexManager> {
    private static IndexManager Instance = new IndexManager();
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
        for(String s : data_.keySet()) {
            notifiers_.get(s).notifyObservers();
        }
        data_.clear();
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
        return data_.get(name);
    }
    /*! \deprecated Use Index::clearFixings instead.
                    Deprecated in version 1.37.
    */
    public void clearHistory(final String name) {
        data_.remove(name);
        notifiers_.get(name).notifyObservers();
    }
    /*! \deprecated Use Index::hasHistoricalFixing instead.
                    Deprecated in version 1.37.
    */
    public boolean hasHistoricalFixing(final String name, final Date fixingDate) {
        return data_.get(name).getValue(fixingDate) != null;
    }
    /*! \deprecated Use Index::addFixings instead.
                    Deprecated in version 1.37.
    */
    public void setHistory(final String name, final TimeSeries<Double> fixing) {
        data_.put(name, fixing);
        notifiers_.get(name).notifyObservers();
    }
    /*! \deprecated Register with the relevant index instead.
                    Deprecated in version 1.37.
    */
    public Observable notifier(final String name) {
        return notifiers_.get(name);
    }
    //! add fixings
    public void addFixing(final String name,
                                 final Date fixingDate,
                          double fixing,
                          boolean forceOverwrite) {
        List<Date> dates = Arrays.asList(fixingDate, fixingDate.add(1));
        List<Double> values = List.of(fixing);
        addFixings(name, dates, values, forceOverwrite, null);
    }
    public void addFixings(final String name, List<Date> dates, List<Double> values,
                           boolean forceOverwrite, FuncDateValid isValidFixingDate) {
        TimeSeries<Double> h = data_.get(name);
        boolean noInvalidFixing = true, noDuplicatedFixing = true;
        Date invalidDate = new Date(), duplicatedDate = new Date();
        double nullValue = MathUtils.NULL_REAL;
        double invalidValue = MathUtils.NULL_REAL;
        double duplicatedValue = MathUtils.NULL_REAL;
        for (int i = 0; i < dates.size(); ++i) {
            boolean validFixing = isValidFixingDate == null || isValidFixingDate.value(dates.get(i));
            double currentValue = h.getValue(dates.get(i));
            boolean missingFixing = forceOverwrite || currentValue == nullValue;
            if (validFixing) {
                if (missingFixing)
                    h.insert(dates.get(i), values.get(i));
                else if (close(currentValue, values.get(i))) {
                    continue;
                } else {
                    noDuplicatedFixing = false;
                    duplicatedDate = dates.get(i);
                    duplicatedValue = values.get(i);
                }
            } else {
                noInvalidFixing = false;
                invalidDate = dates.get(i);
                invalidValue = values.get(i);
            }
        }
        notifier(name).notifyObservers();
        QL_REQUIRE(noInvalidFixing, "At least one invalid fixing provided: "
                + invalidDate.weekday() + " " + invalidDate + ", "
                + invalidValue);
        QL_REQUIRE(noDuplicatedFixing, "At least one duplicated fixing provided: "
                + duplicatedDate + ", " + duplicatedValue
                + " while " + h.getValue(duplicatedDate)
                + " value is already present");
    }
}
