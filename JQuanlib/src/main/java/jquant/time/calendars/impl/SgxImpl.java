package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

public class SgxImpl extends WesternImpl {
    @Override
    public String name() {
        return "Singapore exchange";
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
                || ((d == 1 || (d == 2 && w == Weekday.MONDAY)) && m == Month.JANUARY)
                // Good Friday
                || (dd == em - 3)
                // Labor Day
                || (d == 1 && m == Month.MAY)
                // National Day
                || ((d == 9 || (d == 10 && w == Weekday.MONDAY)) && m == Month.AUGUST)
                // Christmas Day
                || (d == 25 && m == Month.DECEMBER)

                // Chinese New Year
                || ((d == 22 || d == 23) && m == Month.JANUARY && y == 2004)
                || ((d == 9 || d == 10) && m == Month.FEBRUARY && y == 2005)
                || ((d == 30 || d == 31) && m == Month.JANUARY && y == 2006)
                || ((d == 19 || d == 20) && m == Month.FEBRUARY && y == 2007)
                || ((d == 7 || d == 8) && m == Month.FEBRUARY && y == 2008)
                || ((d == 26 || d == 27) && m == Month.JANUARY && y == 2009)
                || ((d == 15 || d == 16) && m == Month.JANUARY && y == 2010)
                || ((d == 23 || d == 24) && m == Month.JANUARY && y == 2012)
                || ((d == 11 || d == 12) && m == Month.FEBRUARY && y == 2013)
                || (d == 31 && m == Month.JANUARY && y == 2014)
                || (d == 1 && m == Month.FEBRUARY && y == 2014)

                // Hari Raya Haji
                || ((d == 1 || d == 2) && m == Month.FEBRUARY && y == 2004)
                || (d == 21 && m == Month.JANUARY && y == 2005)
                || (d == 10 && m == Month.JANUARY && y == 2006)
                || (d == 2 && m == Month.JANUARY && y == 2007)
                || (d == 20 && m == Month.DECEMBER && y == 2007)
                || (d == 8 && m == Month.DECEMBER && y == 2008)
                || (d == 27 && m == Month.NOVEMBER && y == 2009)
                || (d == 17 && m == Month.NOVEMBER && y == 2010)
                || (d == 26 && m == Month.OCTOBER && y == 2012)
                || (d == 15 && m == Month.OCTOBER && y == 2013)
                || (d == 6 && m == Month.OCTOBER && y == 2014)

                // Vesak Poya Day
                || (d == 2 && m == Month.JUNE && y == 2004)
                || (d == 22 && m == Month.MAY && y == 2005)
                || (d == 12 && m == Month.MAY && y == 2006)
                || (d == 31 && m == Month.MAY && y == 2007)
                || (d == 18 && m == Month.MAY && y == 2008)
                || (d == 9 && m == Month.MAY && y == 2009)
                || (d == 28 && m == Month.MAY && y == 2010)
                || (d == 5 && m == Month.MAY && y == 2012)
                || (d == 24 && m == Month.MAY && y == 2013)
                || (d == 13 && m == Month.MAY && y == 2014)

                // Deepavali
                || (d == 11 && m == Month.NOVEMBER && y == 2004)
                || (d == 8 && m == Month.NOVEMBER && y == 2007)
                || (d == 28 && m == Month.OCTOBER && y == 2008)
                || (d == 16 && m == Month.NOVEMBER && y == 2009)
                || (d == 5 && m == Month.NOVEMBER && y == 2010)
                || (d == 13 && m == Month.NOVEMBER && y == 2012)
                || (d == 2 && m == Month.NOVEMBER && y == 2013)
                || (d == 23 && m == Month.OCTOBER && y == 2014)

                // Diwali
                || (d == 1 && m == Month.NOVEMBER && y == 2005)

                // Hari Raya Puasa
                || ((d == 14 || d == 15) && m == Month.NOVEMBER && y == 2004)
                || (d == 3 && m == Month.NOVEMBER && y == 2005)
                || (d == 24 && m == Month.OCTOBER && y == 2006)
                || (d == 13 && m == Month.OCTOBER && y == 2007)
                || (d == 1 && m == Month.OCTOBER && y == 2008)
                || (d == 21 && m == Month.SEPTEMBER && y == 2009)
                || (d == 10 && m == Month.SEPTEMBER && y == 2010)
                || (d == 20 && m == Month.AUGUST && y == 2012)
                || (d == 8 && m == Month.AUGUST && y == 2013)
                || (d == 28 && m == Month.JULY && y == 2014)
        )
            return false; // NOLINT(readability-simplify-boolean-expr)

        // https://api2.sgx.com/sites/default/files/2019-01/2019%20DT%20Calendar.pdf
        if (y == 2019) {
            if ( // Chinese New Year
                    ((d == 5 || d == 6) && m == Month.FEBRUARY)
                            // Vesak Poya Day
                            || (d == 20 && m == Month.MAY)
                            // Hari Raya Puasa
                            || (d == 5 && m == Month.JUNE)
                            // Hari Raya Haji
                            || (d == 12 && m == Month.AUGUST)
                            // Deepavali
                            || (d == 28 && m == Month.OCTOBER)
            )
                return false;
        }

        // https://api2.sgx.com/sites/default/files/2020-11/SGX%20Derivatives%20Trading%20Calendar%202020_Dec%20Update_D3.pdf
        if (y == 2020) {
            if ( // Chinese New Year
                    (d == 27 && m == Month.JANUARY)
                            // Vesak Poya Day
                            || (d == 7 && m == Month.MAY)
                            // Hari Raya Puasa
                            || (d == 25 && m == Month.MAY)
                            // Hari Raya Haji
                            || (d == 31 && m == Month.JULY)
                            // Deepavali
                            || (d == 14 && m == Month.NOVEMBER)
            )
                return false;
        }

        // https://api2.sgx.com/sites/default/files/2021-07/SGX_Derivatives%20Trading%20Calendar%202021%20%28Final%20-%20Jul%29.pdf
        if (y == 2021) {
            if ( // Chinese New Year
                    (d == 12 && m == Month.FEBRUARY)
                            // Hari Raya Puasa
                            || (d == 13 && m == Month.MAY)
                            // Vesak Poya Day
                            || (d == 26 && m == Month.MAY)
                            // Hari Raya Haji
                            || (d == 20 && m == Month.JULY)
                            // Deepavali
                            || (d == 4 && m == Month.NOVEMBER)
            )
                return false;
        }

        // https://api2.sgx.com/sites/default/files/2022-06/DT%20Trading%20Calendar%202022%20%28Final%29.pdf
        if (y == 2022) {
            if (// Chinese New Year
                    ((d == 1 || d == 2) && m == Month.FEBRUARY)
                            // Labour Day
                            || (d == 2 && m == Month.MAY)
                            // Hari Raya Puasa
                            || (d == 3 && m == Month.MAY)
                            // Vesak Poya Day
                            || (d == 16 && m == Month.MAY)
                            // Hari Raya Haji
                            || (d == 11 && m == Month.JULY)
                            // Deepavali
                            || (d == 24 && m == Month.OCTOBER)
                            // Christmas Day
                            || (d == 26 && m == Month.DECEMBER)
            )
                return false;
        }

        // https://api2.sgx.com/sites/default/files/2023-01/SGX%20Calendar%202023_0.pdf
        if (y == 2023) {
            if (// Chinese New Year
                    ((d == 23 || d == 24) && m == Month.JANUARY)
                            // Hari Raya Puasa
                            || (d == 22 && m == Month.APRIL)
                            // Vesak Poya Day
                            || (d == 2 && m == Month.JUNE)
                            // Hari Raya Haji
                            || (d == 29 && m == Month.JUNE)
                            // Public holiday on polling day
                            || (d == 1 && m == Month.SEPTEMBER)
                            // Deepavali
                            || (d == 13 && m == Month.NOVEMBER))
                return false;
        }
        // https://api2.sgx.com/sites/default/files/2024-01/SGX%20Calendar%202024_2.pdf
        if (y == 2024) {
            if (// Chinese New Year
                    (d == 12 && m == Month.FEBRUARY)
                            // Hari Raya Puasa
                            || (d == 10 && m == Month.APRIL)
                            // Vesak Poya Day
                            || (d == 22 && m == Month.MAY)
                            // Hari Raya Haji
                            || (d == 17 && m == Month.JUNE)
                            // Deepavali
                            || (d == 31 && m == Month.OCTOBER))
                return false;
        }
        return true;
    }
}
