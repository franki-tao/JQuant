package jquant;

import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Container for historical data
public class TimeSeries <T extends Number> {
    private TreeMap<Date, T> values_;
    /*! Default constructor */
    public TimeSeries() {
        values_ = new TreeMap<>((o1, o2) -> {
            if (TimeUtils.equals(o1, o2))
                return 0;
            return TimeUtils.greater(o1, o2) ? 1 : -1;
        });
    }
    public TimeSeries(List<Date > dates, List<T> values) {
        values_ = new TreeMap<>((o1, o2) -> {
            if (TimeUtils.equals(o1, o2))
                return 0;
            return TimeUtils.greater(o1, o2) ? 1 : -1;
        });
        for (int i = 0; i < dates.size(); i++) {
            values_.put(dates.get(i), values.get(i));
        }
    }
    public TimeSeries(Date firstDate, List<T> values) {
        values_ = new TreeMap<>((o1, o2) -> {
            if (TimeUtils.equals(o1, o2))
                return 0;
            return TimeUtils.greater(o1, o2) ? 1 : -1;
        });
        Date d = firstDate.copy();
        for (T value : values) {
            values_.put(d.copy(), value);
            d = d.add(1);
        }
    }
    //! returns the first date for which a historical datum exists
    public Date firstDate() {
        QL_REQUIRE(!values_.isEmpty(), "empty timeseries");
        return values_.firstKey();
    }
    //! returns the last date for which a historical datum exists
    public Date lastDate() {
        QL_REQUIRE(!values_.isEmpty(), "empty timeseries");
        return values_.lastKey();
    }
    //! returns the number of historical data including null ones
    public int size() {
        return values_.size();
    }
    //! returns whether the series contains any data
    public boolean empty() {
        return values_.isEmpty();
    }
    //! returns the (possibly null) datum corresponding to the given date
    public T getValue(Date d) {
        return values_.get(d);
    }
    public T insert(Date d) {
        return values_.put(d, null);
    }
    public T insert(Date d, T v) {
        return values_.put(d, v);
    }
    public boolean find(Date d) {
        return values_.containsKey(d);
    }
    //! returns the dates for which historical data exist
    public List<Date> keys() {
        return new ArrayList<Date>(values_.keySet());
    }
    //! returns the historical data
    public List<T> values() {
        return new ArrayList<T>(values_.values());
    }
}
