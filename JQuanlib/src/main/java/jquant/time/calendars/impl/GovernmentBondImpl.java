package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

import static jquant.time.calendars.impl.US.isWashingtonBirthday;

public class GovernmentBondImpl extends WesternImpl {
    @Override
    public String name() {
        return "US government bond market";
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
                // Martin Luther King's birthday (third Monday in January)
                || ((d >= 15 && d <= 21) && w == Weekday.MONDAY && m == Month.JANUARY
                && y >= 1983)
                // Washington's birthday (third Monday in February)
                || isWashingtonBirthday(d, m, y, w)
                // Good Friday. Since 1996 it's an early close and not a full market
                // close when it coincides with the NFP release date, which is the
                // first Friday of the month(*).
                // See <https://www.sifma.org/resources/general/holiday-schedule/>
                //
                // (*) The full rule is "the third Friday after the conclusion of the
                // week which includes the 12th of the month". This is usually the
                // first Friday of the next month, but can be the second Friday if the
                // month has fewer than 31 days. Since Good Friday is always between
                // March 20th and April 23rd, it can only coincide with the April NFP,
                // which is always on the first Friday, because March has 31 days.
                || (dd == em-3 && (y < 1996 || d > 7))
                // Memorial Day (last Monday in May)
                || US.isMemorialDay(d, m, y, w)
                // Juneteenth (Monday if Sunday or Friday if Saturday)
                || US.isJuneteenth(d, m, y, w, true)
                // Independence Day (Monday if Sunday or Friday if Saturday)
                || ((d == 4 || (d == 5 && w == Weekday.MONDAY) ||
                (d == 3 && w == Weekday.FRIDAY)) && m == Month.JULY)
                // Labor Day (first Monday in September)
                || US.isLaborDay(d, m, y, w)
                // Columbus Day (second Monday in October)
                || US.isColumbusDay(d, m, y, w)
                // Veteran's Day (Monday if Sunday)
                || US.isVeteransDayNoSaturday(d, m, y, w)
                // Thanksgiving Day (fourth Thursday in November)
                || ((d >= 22 && d <= 28) && w == Weekday.THURSDAY && m == Month.NOVEMBER)
                // Christmas (Monday if Sunday or Friday if Saturday)
                || ((d == 25 || (d == 26 && w == Weekday.MONDAY) ||
                (d == 24 && w == Weekday.FRIDAY)) && m == Month.DECEMBER))
            return false;

        // Special closings
        // President Bush's Funeral
        return (y != 2018 || m != Month.DECEMBER || d != 5)
                // Hurricane Sandy
                && (y != 2012 || m != Month.OCTOBER || d != 30)
                // President Reagan's funeral
                && (y != 2004 || m != Month.JUNE || d != 11);
    }
}
