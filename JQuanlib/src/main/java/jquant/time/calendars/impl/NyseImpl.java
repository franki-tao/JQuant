package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

public class NyseImpl extends WesternImpl {
    @Override
    public String name() {
        return "New York stock exchange";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth(), dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);
        if (isWeekend(w)
                // New Year's Day (possibly moved to Monday if on Sunday)
                || ((d == 1 || (d == 2 && w == Weekday.MONDAY)) && m == Month.JANUARY)
                // Washington's birthday (third Monday in February)
                || US.isWashingtonBirthday(d, m, y, w)
                // Good Friday
                || (dd == em-3)
                // Memorial Day (last Monday in May)
                || US.isMemorialDay(d, m, y, w)
                // Juneteenth (Monday if Sunday or Friday if Saturday)
                || US.isJuneteenth(d, m, y, w, true)
                // Independence Day (Monday if Sunday or Friday if Saturday)
                || ((d == 4 || (d == 5 && w == Weekday.MONDAY) ||
                (d == 3 && w == Weekday.FRIDAY)) && m == Month.JULY)
                // Labor Day (first Monday in September)
                || US.isLaborDay(d, m, y, w)
                // Thanksgiving Day (fourth Thursday in November)
                || ((d >= 22 && d <= 28) && w == Weekday.THURSDAY && m == Month.NOVEMBER)
                // Christmas (Monday if Sunday or Friday if Saturday)
                || ((d == 25 || (d == 26 && w == Weekday.MONDAY) ||
                (d == 24 && w == Weekday.FRIDAY)) && m == Month.DECEMBER)
        ) return false;

        if (y >= 1998 && (d >= 15 && d <= 21) && w == Weekday.MONDAY && m == Month.JANUARY)
            // Martin Luther King's birthday (third Monday in January)
            return false;

        if ((y <= 1968 || (y <= 1980 && y % 4 == 0)) && m == Month.NOVEMBER
                && d <= 7 && w == Weekday.TUESDAY)
            // Presidential election days
            return false;

        // Special closings
        // President Carter's Funeral
        return (y != 2025 || m != Month.JANUARY || d != 9)
                // President Bush's Funeral
                && (y != 2018 || m != Month.DECEMBER || d != 5)
                // Hurricane Sandy
                && (y != 2012 || m != Month.OCTOBER || (d != 29 && d != 30))
                // President Ford's funeral
                && (y != 2007 || m != Month.JANUARY || d != 2)
                // President Reagan's funeral
                && (y != 2004 || m != Month.JUNE || d != 11)
                // September 11-14, 2001
                && (y != 2001 || m != Month.SEPTEMBER || (11 > d || d > 14))
                // President Nixon's funeral
                && (y != 1994 || m != Month.APRIL || d != 27)
                // Hurricane Gloria
                && (y != 1985 || m != Month.SEPTEMBER || d != 27)
                // 1977 Blackout
                && (y != 1977 || m != Month.JULY || d != 14)
                // Funeral of former President Lyndon B. Johnson.
                && (y != 1973 || m != Month.JANUARY || d != 25)
                // Funeral of former President Harry S. Truman
                && (y != 1972 || m != Month.DECEMBER || d != 28)
                // National Day of Participation for the lunar exploration.
                && (y != 1969 || m != Month.JULY || d != 21)
                // Funeral of former President Eisenhower.
                && (y != 1969 || m != Month.MARCH || d != 31)
                // Closed all day - heavy snow.
                && (y != 1969 || m != Month.FEBRUARY || d != 10)
                // Day after Independence Day.
                && (y != 1968 || m != Month.JULY || d != 5)
                // June 12-Dec. 31, 1968
                // Four day week (closed on Wednesdays) - Paperwork Crisis
                && (y != 1968 || dd < 163 || w != Weekday.WEDNESDAY)
                // Day of mourning for Martin Luther King Jr.
                && (y != 1968 || m != Month.APRIL || d != 9)
                // Funeral of President Kennedy
                && (y != 1963 || m != Month.NOVEMBER || d != 25)
                // Day before Decoration Day
                && (y != 1961 || m != Month.MAY || d != 29)
                // Day after Christmas
                && (y != 1958 || m != Month.DECEMBER || d != 26)
                // Christmas Eve
                && ((y != 1954 && y != 1956 && y != 1965)
                || m != Month.DECEMBER || d != 24);
    }
}
