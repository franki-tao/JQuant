package jquant.time.calendars.impl;

import jquant.time.Month;
import jquant.time.Weekday;

public class US {
    // a few rules used by multiple calendars
    public static boolean isWashingtonBirthday(int d, Month m, int y, Weekday w) {
        if (y >= 1971) {
            // third Monday in February
            return (d >= 15 && d <= 21) && w == Weekday.MONDAY && m == Month.FEBRUARY;
        } else {
            // February 22nd, possibly adjusted
            return (d == 22 || (d == 23 && w == Weekday.MONDAY)
                    || (d == 21 && w == Weekday.FRIDAY)) && m == Month.FEBRUARY;
        }
    }

    public static boolean isMemorialDay(int d, Month m, int y, Weekday w) {
        if (y >= 1971) {
            // last Monday in May
            return d >= 25 && w == Weekday.MONDAY && m == Month.MAY;
        } else {
            // May 30th, possibly adjusted
            return (d == 30 || (d == 31 && w == Weekday.MONDAY)
                    || (d == 29 && w == Weekday.FRIDAY)) && m == Month.MAY;
        }
    }

    public static boolean isLaborDay(int d, Month m, int y, Weekday w) {
        // first Monday in September
        return d <= 7 && w == Weekday.MONDAY && m == Month.SEPTEMBER;
    }

    public static boolean isColumbusDay(int d, Month m, int y, Weekday w) {
        // second Monday in October
        return (d >= 8 && d <= 14) && w == Weekday.MONDAY && m == Month.OCTOBER
                && y >= 1971;
    }

    public static boolean isVeteransDay(int d, Month m, int y, Weekday w) {
        if (y <= 1970 || y >= 1978) {
            // November 11th, adjusted
            return (d == 11 || (d == 12 && w == Weekday.MONDAY) ||
                    (d == 10 && w == Weekday.FRIDAY)) && m == Month.NOVEMBER;
        } else {
            // fourth Monday in October
            return (d >= 22 && d <= 28) && w == Weekday.MONDAY && m == Month.OCTOBER;
        }
    }

    public static boolean isVeteransDayNoSaturday(int d, Month m, int y, Weekday w) {
        if (y <= 1970 || y >= 1978) {
            // November 11th, adjusted, but no Saturday to Friday
            return (d == 11 || (d == 12 && w == Weekday.MONDAY)) && m == Month.NOVEMBER;
        } else {
            // fourth Monday in October
            return (d >= 22 && d <= 28) && w == Weekday.MONDAY && m == Month.OCTOBER;
        }
    }

    public static boolean isJuneteenth(int d, Month m, int y, Weekday w, boolean moveToFriday) {
        // declared in 2021, but only observed by exchanges since 2022
        return (d == 19 || (d == 20 && w == Weekday.MONDAY) || ((d == 18 && w == Weekday.FRIDAY) && moveToFriday))
                && m == Month.JUNE && y >= 2022;
    }
}
