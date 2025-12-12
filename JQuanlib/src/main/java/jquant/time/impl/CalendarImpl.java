package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Weekday;

import java.util.HashSet;
import java.util.Set;

public abstract class CalendarImpl {
    public Set<Date> addedHolidays, removedHolidays;
    public CalendarImpl() {
        addedHolidays = new HashSet<>();
        removedHolidays = new HashSet<>();
    }
    public abstract String name();
    public abstract boolean isBusinessDay(final Date date);
    public abstract boolean isWeekend(final Weekday w);
}
