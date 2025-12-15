package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.impl.NullCalendarImpl;

//! %Calendar for reproducing theoretical calculations.
/*! This calendar has no holidays. It ensures that dates at
    whole-month distances have the same day of month.

    \ingroup calendars
*/
public class NullCalendar extends Calendar {
    public NullCalendar() {
        super();
        super.impl_ = new NullCalendarImpl();
    }
}
