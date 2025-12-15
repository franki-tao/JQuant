package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Weekday;

public class NullCalendarImpl extends CalendarImpl{
    @Override
    public String name() {
        return "Null";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        return true;
    }

    @Override
    public boolean isWeekend(Weekday w) {
        return false;
    }
}
