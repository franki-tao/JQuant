package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

public class CanadaTsxImpl extends WesternImpl {
    @Override
    public String name() {
        return "TSX";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth(), dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);
        return !isWeekend(w)
                // New Year's Day (possibly moved to Monday)
                && ((d != 1 && ((d != 2 && d != 3) || w != Weekday.MONDAY)) || m != Month.JANUARY)
                // Family Day (third Monday in February, since 2008)
                && ((d < 15 || d > 21) || w != Weekday.MONDAY || m != Month.FEBRUARY
                || y < 2008)
                // Good Friday
                && (dd != em - 3)
                // The Monday on or preceding 24 May (Victoria Day)
                && (d <= 17 || d > 24 || w != Weekday.MONDAY || m != Month.MAY)
                // July 1st, possibly moved to Monday (Canada Day)
                && ((d != 1 && ((d != 2 && d != 3) || w != Weekday.MONDAY)) || m != Month.JULY)
                // first Monday of August (Provincial Holiday)
                && (d > 7 || w != Weekday.MONDAY || m != Month.AUGUST)
                // first Monday of September (Labor Day)
                && (d > 7 || w != Weekday.MONDAY || m != Month.SEPTEMBER)
                // second Monday of October (Thanksgiving Day)
                && (d <= 7 || d > 14 || w != Weekday.MONDAY || m != Month.OCTOBER)
                // Christmas (possibly moved to Monday or Tuesday)
                && ((d != 25 && (d != 27 || (w != Weekday.MONDAY && w != Weekday.TUESDAY)))
                || m != Month.DECEMBER)
                // Boxing Day (possibly moved to Monday or Tuesday)
                && ((d != 26 && (d != 28 || (w != Weekday.MONDAY && w != Weekday.TUESDAY)))
                || m != Month.DECEMBER); // NOLINT(readability-simplify-boolean-expr)
    }
}
