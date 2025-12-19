package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

public class FederalReserveImpl extends WesternImpl {
    @Override
    public String name() {
        return "Federal Reserve Bankwire System";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        // see https://www.frbservices.org/about/holiday-schedules for details
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        Month m = date.month();
        int y = date.year();
        return !isWeekend(w)
                // New Year's Day (possibly moved to Monday if on Sunday)
                && ((d != 1 && (d != 2 || w != Weekday.MONDAY)) || m != Month.JANUARY)
                // Martin Luther King's birthday (third Monday in January)
                && ((d < 15 || d > 21) || w != Weekday.MONDAY || m != Month.JANUARY
                || y < 1983)
                // Washington's birthday (third Monday in February)
                && !US.isWashingtonBirthday(d, m, y, w)
                // Memorial Day (last Monday in May)
                && !US.isMemorialDay(d, m, y, w)
                // Juneteenth (Monday if Sunday)
                && !US.isJuneteenth(d, m, y, w, false)
                // Independence Day (Monday if Sunday)
                && ((d != 4 && (d != 5 || w != Weekday.MONDAY)) || m != Month.JULY)
                // Labor Day (first Monday in September)
                && !US.isLaborDay(d, m, y, w)
                // Columbus Day (second Monday in October)
                && !US.isColumbusDay(d, m, y, w)
                // Veteran's Day (Monday if Sunday)
                && !US.isVeteransDayNoSaturday(d, m, y, w)
                // Thanksgiving Day (fourth Thursday in November)
                && ((d < 22 || d > 28) || w != Weekday.THURSDAY || m != Month.NOVEMBER)
                // Christmas (Monday if Sunday)
                && ((d != 25 && (d != 26 || w != Weekday.MONDAY)) || m != Month.DECEMBER); // NOLINT(readability-simplify-boolean-expr)
    }
}
