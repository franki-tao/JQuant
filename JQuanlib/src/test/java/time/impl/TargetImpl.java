package time.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;


public class TargetImpl extends WesternImpl {
    @Override
    public String name() {
        return "TARGET";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth(), dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);
        if (isWeekend(w)
                // New Year's Day
                || (d == 1  && m == Month.JANUARY)
                // Good Friday
                || (dd == em-3 && y >= 2000)
                // Easter Monday
                || (dd == em && y >= 2000)
                // Labour Day
                || (d == 1  && m == Month.MAY && y >= 2000)
                // Christmas
                || (d == 25 && m == Month.DECEMBER)
                // Day of Goodwill
                || (d == 26 && m == Month.DECEMBER && y >= 2000)
                // December 31st, 1998, 1999, and 2001 only
                || (d == 31 && m == Month.DECEMBER &&
                (y == 1998 || y == 1999 || y == 2001)))
            return false; // NOLINT(readability-simplify-boolean-expr)
        return true;
    }
}
