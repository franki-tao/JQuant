package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * New Zealand calendar implementations.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/time/calendars/newzealand.cpp
 */
public class NewZealandImpl {
    private NewZealandImpl() {
    }

    // Wellington Anniversary Day, Monday nearest to January 22nd
    private static boolean isWellingtonAnniversary(int d, Month m, Weekday w) {
        return w == Weekday.MONDAY && d >= 19 && d <= 25 && m == Month.JANUARY;
    }

    // Auckland Anniversary Day, Monday nearest to January 29th
    private static boolean isAucklandAnniversary(int d, Month m, Weekday w) {
        return w == Weekday.MONDAY
                && ((d >= 26 && m == Month.JANUARY) || (d == 1 && m == Month.FEBRUARY));
    }

    public abstract static class CommonImpl extends WesternImpl {
        protected boolean isCommonBusinessDay(int d, int dayOfYear, Month m, int y, Weekday w) {
            int em = easterMonday(y);
            if (isWeekend(w)
                    // New Year's Day (possibly moved to Monday or Tuesday)
                    || ((d == 1 || (d == 3 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                    && m == Month.JANUARY)
                    // Day after New Year's Day (possibly moved to Mon or Tuesday)
                    || ((d == 2 || (d == 4 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                    && m == Month.JANUARY)
                    // Waitangi Day. February 6th (possibly moved to Monday since 2013)
                    || (d == 6 && m == Month.FEBRUARY)
                    || ((d == 7 || d == 8) && w == Weekday.MONDAY && m == Month.FEBRUARY && y > 2013)
                    // Good Friday
                    || (dayOfYear == em - 3)
                    // Easter Monday
                    || (dayOfYear == em)
                    // ANZAC Day. April 25th (possibly moved to Monday since 2013)
                    || (d == 25 && m == Month.APRIL)
                    || ((d == 26 || d == 27) && w == Weekday.MONDAY && m == Month.APRIL && y > 2013)
                    // Queen's Birthday, first Monday in June
                    || (d <= 7 && w == Weekday.MONDAY && m == Month.JUNE)
                    // Labour Day, fourth Monday in October
                    || ((d >= 22 && d <= 28) && w == Weekday.MONDAY && m == Month.OCTOBER)
                    // Christmas, December 25th (possibly Monday or Tuesday)
                    || ((d == 25 || (d == 27 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                    && m == Month.DECEMBER)
                    // Boxing Day, December 26th (possibly Monday or Tuesday)
                    || ((d == 26 || (d == 28 && (w == Weekday.MONDAY || w == Weekday.TUESDAY)))
                    && m == Month.DECEMBER)
                    // Matariki, it happens on Friday in June or July
                    // official calendar released by the NZ government for the
                    // next 30 years
                    || (d == 20 && m == Month.JUNE && y == 2025)
                    || (d == 21 && m == Month.JUNE && (y == 2030 || y == 2052))
                    || (d == 24 && m == Month.JUNE && (y == 2022 || y == 2033 || y == 2044))
                    || (d == 25 && m == Month.JUNE && (y == 2027 || y == 2038 || y == 2049))
                    || (d == 28 && m == Month.JUNE && y == 2024)
                    || (d == 29 && m == Month.JUNE && (y == 2035 || y == 2046))
                    || (d == 30 && m == Month.JUNE && y == 2051)
                    || (d == 2 && m == Month.JULY && y == 2032)
                    || (d == 3 && m == Month.JULY && (y == 2043 || y == 2048))
                    || (d == 6 && m == Month.JULY && (y == 2029 || y == 2040))
                    || (d == 7 && m == Month.JULY && (y == 2034 || y == 2045))
                    || (d == 10 && m == Month.JULY && (y == 2026 || y == 2037))
                    || (d == 11 && m == Month.JULY && (y == 2031 || y == 2042))
                    || (d == 14 && m == Month.JULY && (y == 2023 || y == 2028))
                    || (d == 15 && m == Month.JULY && (y == 2039 || y == 2050))
                    || (d == 18 && m == Month.JULY && y == 2036)
                    || (d == 19 && m == Month.JULY && (y == 2041 || y == 2047))
                    // Queen Elizabeth's funeral
                    || (d == 26 && m == Month.SEPTEMBER && y == 2022))
                return false;
            return true;
        }
    }

    public static class WellingtonImpl extends CommonImpl {
        @Override
        public String name() {
            return "New Zealand (Wellington)";
        }

        @Override
        public boolean isBusinessDay(Date date) {
            Weekday w = date.weekday();
            int d = date.dayOfMonth();
            int dd = date.dayOfYear();
            Month m = date.month();
            int y = date.year();
            return isCommonBusinessDay(d, dd, m, y, w) && !isWellingtonAnniversary(d, m, w);
        }
    }

    public static class AucklandImpl extends CommonImpl {
        @Override
        public String name() {
            return "New Zealand (Auckland)";
        }

        @Override
        public boolean isBusinessDay(Date date) {
            Weekday w = date.weekday();
            int d = date.dayOfMonth();
            int dd = date.dayOfYear();
            Month m = date.month();
            int y = date.year();
            return isCommonBusinessDay(d, dd, m, y, w) && !isAucklandAnniversary(d, m, w);
        }
    }

    public static class NZFMAImpl extends CommonImpl {
        @Override
        public String name() {
            return "New Zealand (NZFMA)";
        }

        @Override
        public boolean isBusinessDay(Date date) {
            Weekday w = date.weekday();
            int d = date.dayOfMonth();
            int dd = date.dayOfYear();
            Month m = date.month();
            int y = date.year();
            return isCommonBusinessDay(d, dd, m, y, w)
                    && (y >= 2026
                    || !(isWellingtonAnniversary(d, m, w) || isAucklandAnniversary(d, m, w)));
        }
    }
}
