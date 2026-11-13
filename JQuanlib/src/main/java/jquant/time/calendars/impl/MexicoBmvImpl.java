package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * Mexican stock exchange (BMV) holidays.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/time/calendars/mexico.cpp
 */
public class MexicoBmvImpl extends WesternImpl {
    @Override
    public String name() {
        return "Mexican stock exchange";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        int dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);
        if (isWeekend(w)
                // New Year's Day
                || (d == 1 && m == Month.JANUARY)
                // Constitution Day
                || (y <= 2005 && d == 5 && m == Month.FEBRUARY)
                || (y >= 2006 && d <= 7 && w == Weekday.MONDAY && m == Month.FEBRUARY)
                // Birthday of Benito Juarez
                || (y <= 2005 && d == 21 && m == Month.MARCH)
                || (y >= 2006 && (d >= 15 && d <= 21) && w == Weekday.MONDAY && m == Month.MARCH)
                // Holy Thursday
                || (dd == em - 4)
                // Good Friday
                || (dd == em - 3)
                // Labour Day
                || (d == 1 && m == Month.MAY)
                // National Day
                || (d == 16 && m == Month.SEPTEMBER)
                // Inauguration Day
                || (d == 1 && m == Month.OCTOBER && y >= 2024 && (y - 2024) % 6 == 0)
                // All Souls Day
                || (d == 2 && m == Month.NOVEMBER)
                // Revolution Day
                || (y <= 2005 && d == 20 && m == Month.NOVEMBER)
                || (y >= 2006 && (d >= 15 && d <= 21) && w == Weekday.MONDAY && m == Month.NOVEMBER)
                // Our Lady of Guadalupe
                || (d == 12 && m == Month.DECEMBER)
                // Christmas
                || (d == 25 && m == Month.DECEMBER))
            return false;
        return true;
    }
}
