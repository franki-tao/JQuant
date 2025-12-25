package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.CalendarImpl;

public class JapanImpl extends CalendarImpl {
    @Override
    public String name() {
        return "Japan";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        Month m = date.month();
        int y = date.year();
        // equinox calculation
        final double exact_vernal_equinox_time = 20.69115;
        final double exact_autumnal_equinox_time = 23.09;
        final double diff_per_year = 0.242194;
        final double moving_amount = (y - 2000) * diff_per_year;
        int number_of_leap_years = (y - 2000) / 4 + (y - 2000) / 100 - (y - 2000) / 400;
        int ve =    // vernal equinox day
                (int) (exact_vernal_equinox_time + moving_amount - number_of_leap_years);
        int ae =    // autumnal equinox day
                (int) (exact_autumnal_equinox_time
                        + moving_amount - number_of_leap_years);
        // checks
        if (isWeekend(w)
                // New Year's Day
                || (d == 1 && m == Month.JANUARY)
                // Bank Holiday
                || (d == 2 && m == Month.JANUARY)
                // Bank Holiday
                || (d == 3 && m == Month.JANUARY)
                // Coming of Age Day (2nd Monday in January),
                // was January 15th until 2000
                || (w == Weekday.MONDAY && (d >= 8 && d <= 14) && m == Month.JANUARY
                && y >= 2000)
                || ((d == 15 || (d == 16 && w == Weekday.MONDAY)) && m == Month.JANUARY
                && y < 2000)
                // National Foundation Day
                || ((d == 11 || (d == 12 && w == Weekday.MONDAY)) && m == Month.FEBRUARY)
                // Emperor's Birthday (Emperor Naruhito)
                || ((d == 23 || (d == 24 && w == Weekday.MONDAY)) && m == Month.FEBRUARY
                && y >= 2020)
                // Emperor's Birthday (Emperor Akihito)
                || ((d == 23 || (d == 24 && w == Weekday.MONDAY)) && m == Month.DECEMBER
                && (y >= 1989 && y < 2019))
                // Vernal Equinox
                || ((d == ve || (d == ve + 1 && w == Weekday.MONDAY)) && m == Month.MARCH)
                // Greenery Day
                || ((d == 29 || (d == 30 && w == Weekday.MONDAY)) && m == Month.APRIL)
                // Constitution Memorial Day
                || (d == 3 && m == Month.MAY)
                // Holiday for a Nation
                || (d == 4 && m == Month.MAY)
                // Children's Day
                || (d == 5 && m == Month.MAY)
                // any of the three above observed later if on Saturday or Sunday
                || (d == 6 && m == Month.MAY
                && (w == Weekday.MONDAY || w == Weekday.TUESDAY || w == Weekday.WEDNESDAY))
                // Marine Day (3rd Monday in July),
                // was July 20th until 2003, not a holiday before 1996,
                // July 23rd in 2020 due to Olympics games
                // July 22nd in 2021 due to Olympics games
                || (w == Weekday.MONDAY && (d >= 15 && d <= 21) && m == Month.JULY
                && ((y >= 2003 && y < 2020) || y >= 2022))
                || ((d == 20 || (d == 21 && w == Weekday.MONDAY)) && m == Month.JULY
                && y >= 1996 && y < 2003)
                || (d == 23 && m == Month.JULY && y == 2020)
                || (d == 22 && m == Month.JULY && y == 2021)
                // Mountain Day
                // (moved in 2020 due to Olympics games)
                // (moved in 2021 due to Olympics games)
                || ((d == 11 || (d == 12 && w == Weekday.MONDAY)) && m == Month.AUGUST
                && ((y >= 2016 && y < 2020) || y >= 2022))
                || (d == 10 && m == Month.AUGUST && y == 2020)
                || (d == 9 && m == Month.AUGUST && y == 2021)
                // Respect for the Aged Day (3rd Monday in September),
                // was September 15th until 2003
                || (w == Weekday.MONDAY && (d >= 15 && d <= 21) && m == Month.SEPTEMBER
                && y >= 2003)
                || ((d == 15 || (d == 16 && w == Weekday.MONDAY)) && m == Month.SEPTEMBER
                && y < 2003)
                // If a single day falls between Respect for the Aged Day
                // and the Autumnal Equinox, it is holiday
                || (w == Weekday.TUESDAY && d + 1 == ae && d >= 16 && d <= 22
                && m == Month.SEPTEMBER && y >= 2003)
                // Autumnal Equinox
                || ((d == ae || (d == ae + 1 && w == Weekday.MONDAY)) && m == Month.SEPTEMBER)
                // Health and Sports Day (2nd Monday in October),
                // was October 10th until 2000,
                // July 24th in 2020 due to Olympics games
                // July 23rd in 2021 due to Olympics games
                || (w == Weekday.MONDAY && (d >= 8 && d <= 14) && m == Month.OCTOBER
                && ((y >= 2000 && y < 2020) || y >= 2022))
                || ((d == 10 || (d == 11 && w == Weekday.MONDAY)) && m == Month.OCTOBER
                && y < 2000)
                || (d == 24 && m == Month.JULY && y == 2020)
                || (d == 23 && m == Month.JULY && y == 2021)
                // National Culture Day
                || ((d == 3 || (d == 4 && w == Weekday.MONDAY)) && m == Month.NOVEMBER)
                // Labor Thanksgiving Day
                || ((d == 23 || (d == 24 && w == Weekday.MONDAY)) && m == Month.NOVEMBER)
                // Bank Holiday
                || (d == 31 && m == Month.DECEMBER)
                // one-shot holidays
                // Marriage of Prince Akihito
                || (d == 10 && m == Month.APRIL && y == 1959)
                // Rites of Imperial Funeral
                || (d == 24 && m == Month.FEBRUARY && y == 1989)
                // Enthronement Ceremony (Emperor Akihito)
                || (d == 12 && m == Month.NOVEMBER && y == 1990)
                // Marriage of Prince Naruhito
                || (d == 9 && m == Month.JUNE && y == 1993)
                // Special holiday based on Japanese public holidays law
                || (d == 30 && m == Month.APRIL && y == 2019)
                // Enthronement Day (Emperor Naruhito)
                || (d == 1 && m == Month.MAY && y == 2019)
                // Special holiday based on Japanese public holidays law
                || (d == 2 && m == Month.MAY && y == 2019)
                // Enthronement Ceremony (Emperor Naruhito)
                || (d == 22 && m == Month.OCTOBER && y == 2019))
            return false; // NOLINT(readability-simplify-boolean-expr)
        return true;
    }

    @Override
    public boolean isWeekend(Weekday w) {
        return w == Weekday.SATURDAY || w == Weekday.SUNDAY;
    }
}
