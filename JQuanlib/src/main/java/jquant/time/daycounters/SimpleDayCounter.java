package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.SimpleDayCounterImpl;

//! Simple day counter for reproducing theoretical calculations.
/*! This day counter tries to ensure that whole-month distances
    are returned as a simple fraction, i.e., 1 year = 1.0,
    6 months = 0.5, 3 months = 0.25 and so forth.

    \warning this day counter should be used together with
             NullCalendar, which ensures that dates at whole-month
             distances share the same day of month. It is <b>not</b>
             guaranteed to work with any other calendar.

    \ingroup daycounters

    \test the correctness of the results is checked against known
          good values.
*/
public class SimpleDayCounter extends DayCounter {
    public SimpleDayCounter() {
        super(new SimpleDayCounterImpl());
    }
}
