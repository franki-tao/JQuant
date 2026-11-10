package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * UK bank-holiday rules shared by settlement, stock exchange and metals.
 * QuantLib v1.36 gives those three markets the same {@code isBusinessDay}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/time/calendars/unitedkingdom.cpp
 */
public class UnitedKingdomImpl extends WesternImpl {
    private final String name_;

    public UnitedKingdomImpl(String name) {
        name_ = name;
    }

    @Override
    public String name() {
        return name_;
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
                // New Year's Day (possibly moved to Monday)
                || ((d == 1 || ((d == 2 || d == 3) && w == Weekday.MONDAY)) && m == Month.JANUARY)
                // Good Friday
                || (dd == em - 3)
                // Easter Monday
                || (dd == em)
                || isBankHoliday(d, w, m, y)
                // Christmas (possibly moved to Monday or Tuesday)
                || ((d == 25 || (d == 27 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                && m == Month.DECEMBER)
                // Boxing Day (possibly moved to Monday or Tuesday)
                || ((d == 26 || (d == 28 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                && m == Month.DECEMBER)
                // December 31st, 1999 only
                || (d == 31 && m == Month.DECEMBER && y == 1999))
            return false;
        return true;
    }

    private static boolean isBankHoliday(int d, Weekday w, Month m, int y) {
        return (d <= 7 && w == Weekday.MONDAY && m == Month.MAY && y != 1995 && y != 2020)
                || (d == 8 && m == Month.MAY && (y == 1995 || y == 2020))
                || (d >= 25 && w == Weekday.MONDAY && m == Month.MAY && y != 2002 && y != 2012 && y != 2022)
                || ((d == 3 || d == 4) && m == Month.JUNE && y == 2002)
                || ((d == 4 || d == 5) && m == Month.JUNE && y == 2012)
                || ((d == 2 || d == 3) && m == Month.JUNE && y == 2022)
                || (d >= 25 && w == Weekday.MONDAY && m == Month.AUGUST)
                || (d == 29 && m == Month.APRIL && y == 2011)
                || (d == 19 && m == Month.SEPTEMBER && y == 2022)
                || (d == 8 && m == Month.MAY && y == 2023);
    }
}
