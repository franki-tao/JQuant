package time;

import jquant.time.Calendar;
import time.impl.WeekendsOnlyImpl;

//! Weekends-only calendar
/*! This calendar has no bank holidays except for weekends
    (Saturdays and Sundays) as required by ISDA for calculating
    conventional CDS spreads.

    \ingroup calendars
*/
public class WeekendsOnly extends Calendar {
    public WeekendsOnly() {
        super();
        impl_ = new WeekendsOnlyImpl();
    }
}
