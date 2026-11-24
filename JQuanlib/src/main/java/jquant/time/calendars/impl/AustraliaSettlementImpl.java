package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * Australian settlement calendar, copied verbatim from
 * {@code ql/time/calendars/australia.cpp} {@code SettlementImpl}.
 *
 * <p>Note that, as in the official implementation, ANZAC Day is not moved
 * when 25 April falls on a weekend, and Bank Holiday / Labour Day are included
 * only in the settlement calendar (not in the ASX one).
 */
public class AustraliaSettlementImpl extends WesternImpl {
    @Override
    public String name() {
        return "Australia settlement";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth(), dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);
        if (isWeekend(w)
                // New Year's Day (possibly moved to Monday)
                || ((d == 1 || ((d == 2 || d == 3) && w == Weekday.MONDAY))
                && m == Month.JANUARY)
                // Australia Day, January 26th (possibly moved to Monday)
                || ((d == 26 || ((d == 27 || d == 28) && w == Weekday.MONDAY))
                && m == Month.JANUARY)
                // Good Friday
                || (dd == em - 3)
                // Easter Monday
                || (dd == em)
                // ANZAC Day, April 25th
                || (d == 25 && m == Month.APRIL)
                // Queen's Birthday, second Monday in June
                || ((d > 7 && d <= 14) && w == Weekday.MONDAY && m == Month.JUNE)
                // Bank Holiday, first Monday in August
                || (d <= 7 && w == Weekday.MONDAY && m == Month.AUGUST)
                // Labour Day, first Monday in October
                || (d <= 7 && w == Weekday.MONDAY && m == Month.OCTOBER)
                // Christmas, December 25th (possibly Monday or Tuesday)
                || ((d == 25 || (d == 27 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                && m == Month.DECEMBER)
                // Boxing Day, December 26th (possibly Monday or Tuesday)
                || ((d == 26 || (d == 28 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                && m == Month.DECEMBER)
                // National Day of Mourning for Her Majesty, September 22 (only 2022)
                || (d == 22 && m == Month.SEPTEMBER && y == 2022))
            return false;
        return true;
    }
}
