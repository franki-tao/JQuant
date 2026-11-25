package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * Brazilian settlement calendar, copied verbatim from
 * {@code ql/time/calendars/brazil.cpp} {@code SettlementImpl}.
 *
 * <p>Black Awareness Day (20 November) is a holiday only from 2024 onwards,
 * exactly as official.
 */
public class BrazilSettlementImpl extends WesternImpl {
    @Override
    public String name() {
        return "Brazil";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        Month m = date.month();
        int y = date.year();
        int dd = date.dayOfYear();
        int em = easterMonday(y);

        if (isWeekend(w)
                // New Year's Day
                || (d == 1 && m == Month.JANUARY)
                // Tiradentes Day
                || (d == 21 && m == Month.APRIL)
                // Labor Day
                || (d == 1 && m == Month.MAY)
                // Independence Day
                || (d == 7 && m == Month.SEPTEMBER)
                // Nossa Sra. Aparecida Day
                || (d == 12 && m == Month.OCTOBER)
                // All Souls Day
                || (d == 2 && m == Month.NOVEMBER)
                // Republic Day
                || (d == 15 && m == Month.NOVEMBER)
                // Black Awareness Day
                || (d == 20 && m == Month.NOVEMBER && y >= 2024)
                // Christmas
                || (d == 25 && m == Month.DECEMBER)
                // Passion of Christ
                || (dd == em - 3)
                // Carnival
                || (dd == em - 49 || dd == em - 48)
                // Corpus Christi
                || (dd == em + 59))
            return false;
        return true;
    }
}
