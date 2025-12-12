package jquant.time.impl;

import jquant.time.Weekday;

import static jquant.time.TimeUtils.EasterMonday;

//! partial calendar implementation
/*! This class provides the means of determining the Easter
    Monday for a given year, as well as specifying Saturdays
    and Sundays as weekend days.
*/
public abstract class WesternImpl extends CalendarImpl{
    public WesternImpl() {
        super();
    }
    @Override
    public boolean isWeekend(final Weekday w) {
        return w == Weekday.SATURDAY || w == Weekday.SUNDAY;
    }
    //! expressed relative to first day of year
    public static int easterMonday(int y) {
        return EasterMonday[y-1901];
    }
}
