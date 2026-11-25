package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

/**
 * BOVESPA calendar, copied verbatim from
 * {@code ql/time/calendars/brazil.cpp} {@code ExchangeImpl}.
 *
 * <p>Sao Paulo City Day and Revolution Day apply only up to 2021; Black
 * Consciousness Day applies from 2007 except in 2022 and 2023; the last
 * business day of the year is also a holiday, exactly as official.
 */
public class BrazilExchangeImpl extends WesternImpl {
    @Override
    public String name() {
        return "BOVESPA";
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
                // Sao Paulo City Day
                || (d == 25 && m == Month.JANUARY && y < 2022)
                // Tiradentes Day
                || (d == 21 && m == Month.APRIL)
                // Labor Day
                || (d == 1 && m == Month.MAY)
                // Revolution Day
                || (d == 9 && m == Month.JULY && y < 2022)
                // Independence Day
                || (d == 7 && m == Month.SEPTEMBER)
                // Nossa Sra. Aparecida Day
                || (d == 12 && m == Month.OCTOBER)
                // All Souls Day
                || (d == 2 && m == Month.NOVEMBER)
                // Republic Day
                || (d == 15 && m == Month.NOVEMBER)
                // Black Consciousness Day
                || (d == 20 && m == Month.NOVEMBER && y >= 2007 && y != 2022 && y != 2023)
                // Christmas Eve
                || (d == 24 && m == Month.DECEMBER)
                // Christmas
                || (d == 25 && m == Month.DECEMBER)
                // Passion of Christ
                || (dd == em - 3)
                // Carnival
                || (dd == em - 49 || dd == em - 48)
                // Corpus Christi
                || (dd == em + 59)
                // last business day of the year
                || (m == Month.DECEMBER && (d == 31 || (d >= 29 && w == Weekday.FRIDAY))))
            return false;
        return true;
    }
}
