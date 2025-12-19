package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.CalendarImpl;

public class SseImpl extends CalendarImpl {
    @Override
    public String name() {
        return "Shanghai stock exchange";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        Month m = date.month();
        int y = date.year();

        return !isWeekend(w)
                // New Year's Day
                && (d != 1 || m != Month.JANUARY)
                && (y != 2005 || d != 3 || m != Month.JANUARY)
                && (y != 2006 || (d != 2 && d != 3) || m != Month.JANUARY)
                && (y != 2007 || d > 3 || m != Month.JANUARY)
                && (y != 2007 || d != 31 || m != Month.DECEMBER)
                && (y != 2009 || d != 2 || m != Month.JANUARY)
                && (y != 2011 || d != 3 || m != Month.JANUARY)
                && (y != 2012 || (d != 2 && d != 3) || m != Month.JANUARY)
                && (y != 2013 || d > 3 || m != Month.JANUARY)
                && (y != 2014 || d != 1 || m != Month.JANUARY)
                && (y != 2015 || d > 3 || m != Month.JANUARY)
                && (y != 2017 || d != 2 || m != Month.JANUARY)
                && (y != 2018 || d != 1 || m != Month.JANUARY)
                && (y != 2018 || d != 31 || m != Month.DECEMBER)
                && (y != 2019 || d != 1 || m != Month.JANUARY)
                && (y != 2020 || d != 1 || m != Month.JANUARY)
                && (y != 2021 || d != 1 || m != Month.JANUARY)
                && (y != 2022 || d != 3 || m != Month.JANUARY)
                && (y != 2023 || d != 2 || m != Month.JANUARY)
                // Chinese New Year
                && (y != 2004 || d < 19 || d > 28 || m != Month.JANUARY)
                && (y != 2005 || d < 7 || d > 15 || m != Month.FEBRUARY)
                && (y != 2006 || ((d < 26 || m != Month.JANUARY) &&
                (d > 3 || m != Month.FEBRUARY)))
                && (y != 2007 || d < 17 || d > 25 || m != Month.FEBRUARY)
                && (y != 2008 || d < 6 || d > 12 || m != Month.FEBRUARY)
                && (y != 2009 || d < 26 || d > 30 || m != Month.JANUARY)
                && (y != 2010 || d < 15 || d > 19 || m != Month.FEBRUARY)
                && (y != 2011 || d < 2 || d > 8 || m != Month.FEBRUARY)
                && (y != 2012 || d < 23 || d > 28 || m != Month.JANUARY)
                && (y != 2013 || d < 11 || d > 15 || m != Month.FEBRUARY)
                && (y != 2014 || d < 31 || m != Month.JANUARY)
                && (y != 2014 || d > 6 || m != Month.FEBRUARY)
                && (y != 2015 || d < 18 || d > 24 || m != Month.FEBRUARY)
                && (y != 2016 || d < 8 || d > 12 || m != Month.FEBRUARY)
                && (y != 2017 || ((d < 27 || m != Month.JANUARY) &&
                (d > 2 || m != Month.FEBRUARY)))
                && (y != 2018 || (d < 15 || d > 21 || m != Month.FEBRUARY))
                && (y != 2019 || d < 4 || d > 8 || m != Month.FEBRUARY)
                && (y != 2020 || (d != 24 && (d < 27 || d > 31)) || m != Month.JANUARY)
                && (y != 2021 || (d != 11 && d != 12 && d != 15 && d != 16 && d != 17) || m != Month.FEBRUARY)
                && (y != 2022 || ((d != 31 || m != Month.JANUARY) && (d > 4 || m != Month.FEBRUARY)))
                && (y != 2023 || d < 23 || d > 27 || m != Month.JANUARY)
                && (y != 2024 || (d != 9 && (d < 12 || d > 16)) || m != Month.FEBRUARY)
                && (y != 2025 || ((d < 28 || d > 31 || m != Month.JANUARY) && (d < 3 || d > 4 || m != Month.FEBRUARY)))
                // Ching Ming Festival
                && (y > 2008 || d != 4 || m != Month.APRIL)
                && (y != 2009 || d != 6 || m != Month.APRIL)
                && (y != 2010 || d != 5 || m != Month.APRIL)
                && (y != 2011 || d < 3 || d > 5 || m != Month.APRIL)
                && (y != 2012 || d < 2 || d > 4 || m != Month.APRIL)
                && (y != 2013 || d < 4 || d > 5 || m != Month.APRIL)
                && (y != 2014 || d != 7 || m != Month.APRIL)
                && (y != 2015 || d < 5 || d > 6 || m != Month.APRIL)
                && (y != 2016 || d != 4 || m != Month.APRIL)
                && (y != 2017 || d < 3 || d > 4 || m != Month.APRIL)
                && (y != 2018 || d < 5 || d > 6 || m != Month.APRIL)
                && (y != 2019 || d != 5 || m != Month.APRIL)
                && (y != 2020 || d != 6 || m != Month.APRIL)
                && (y != 2021 || d != 5 || m != Month.APRIL)
                && (y != 2022 || d < 4 || d > 5 || m != Month.APRIL)
                && (y != 2023 || d != 5 || m != Month.APRIL)
                && (y != 2024 || d < 4 || d > 5 || m != Month.APRIL)
                && (y != 2025 || d != 4 || m != Month.APRIL)
                // Labor Day
                && (y > 2007 || d < 1 || d > 7 || m != Month.MAY)
                && (y != 2008 || d < 1 || d > 2 || m != Month.MAY)
                && (y != 2009 || d != 1 || m != Month.MAY)
                && (y != 2010 || d != 3 || m != Month.MAY)
                && (y != 2011 || d != 2 || m != Month.MAY)
                && (y != 2012 || ((d != 30 || m != Month.APRIL) &&
                (d != 1 || m != Month.MAY)))
                && (y != 2013 || ((d < 29 || m != Month.APRIL) &&
                (d != 1 || m != Month.MAY)))
                && (y != 2014 || d < 1 || d > 3 || m != Month.MAY)
                && (y != 2015 || d != 1 || m != Month.MAY)
                && (y != 2016 || d < 1 || d > 2 || m != Month.MAY)
                && (y != 2017 || d != 1 || m != Month.MAY)
                && (y != 2018 || ((d != 30 || m != Month.APRIL) && (d != 1 || m != Month.MAY)))
                && (y != 2019 || d < 1 || d > 3 || m != Month.MAY)
                && (y != 2020 || (d != 1 && d != 4 && d != 5) || m != Month.MAY)
                && (y != 2021 || (d != 3 && d != 4 && d != 5) || m != Month.MAY)
                && (y != 2022 || d < 2 || d > 4 || m != Month.MAY)
                && (y != 2023 || d < 1 || d > 3 || m != Month.MAY)
                && (y != 2024 || d < 1 || d > 3 || m != Month.MAY)
                && (y != 2025 || (d != 1 && d != 2 && d != 5) || m != Month.MAY)
                // Tuen Ng Festival
                && (y > 2008 || d != 9 || m != Month.JUNE)
                && (y != 2009 || (d != 28 && d != 29) || m != Month.MAY)
                && (y != 2010 || d < 14 || d > 16 || m != Month.JUNE)
                && (y != 2011 || d < 4 || d > 6 || m != Month.JUNE)
                && (y != 2012 || d < 22 || d > 24 || m != Month.JUNE)
                && (y != 2013 || d < 10 || d > 12 || m != Month.JUNE)
                && (y != 2014 || d != 2 || m != Month.JUNE)
                && (y != 2015 || d != 22 || m != Month.JUNE)
                && (y != 2016 || d < 9 || d > 10 || m != Month.JUNE)
                && (y != 2017 || d < 29 || d > 30 || m != Month.MAY)
                && (y != 2018 || d != 18 || m != Month.JUNE)
                && (y != 2019 || d != 7 || m != Month.JUNE)
                && (y != 2020 || d < 25 || d > 26 || m != Month.JUNE)
                && (y != 2021 || d != 14 || m != Month.JUNE)
                && (y != 2022 || d != 3 || m != Month.JUNE)
                && (y != 2023 || d < 22 || d > 23 || m != Month.JUNE)
                && (y != 2024 || d != 10 || m != Month.JUNE)
                && (y != 2025 || d != 2 || m != Month.JUNE)
                // Mid-Autumn Festival
                && (y > 2008 || d != 15 || m != Month.SEPTEMBER)
                && (y != 2010 || d < 22 || d > 24 || m != Month.SEPTEMBER)
                && (y != 2011 || d < 10 || d > 12 || m != Month.SEPTEMBER)
                && (y != 2012 || d != 30 || m != Month.SEPTEMBER)
                && (y != 2013 || d < 19 || d > 20 || m != Month.SEPTEMBER)
                && (y != 2014 || d != 8 || m != Month.SEPTEMBER)
                && (y != 2015 || d != 27 || m != Month.SEPTEMBER)
                && (y != 2016 || d < 15 || d > 16 || m != Month.SEPTEMBER)
                && (y != 2018 || d != 24 || m != Month.SEPTEMBER)
                && (y != 2019 || d != 13 || m != Month.SEPTEMBER)
                && (y != 2021 || (d != 20 && d != 21) || m != Month.SEPTEMBER)
                && (y != 2022 || d != 12 || m != Month.SEPTEMBER)
                && (y != 2023 || d != 29 || m != Month.SEPTEMBER)
                && (y != 2024 || d < 16 || d > 17 || m != Month.SEPTEMBER)
                // National Day
                && (y > 2007 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2008 || ((d < 29 || m != Month.SEPTEMBER) &&
                (d > 3 || m != Month.OCTOBER)))
                && (y != 2009 || d < 1 || d > 8 || m != Month.OCTOBER)
                && (y != 2010 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2011 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2012 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2013 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2014 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2015 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2016 || d < 3 || d > 7 || m != Month.OCTOBER)
                && (y != 2017 || d < 2 || d > 6 || m != Month.OCTOBER)
                && (y != 2018 || d < 1 || d > 5 || m != Month.OCTOBER)
                && (y != 2019 || d < 1 || d > 7 || m != Month.OCTOBER)
                && (y != 2020 || d < 1 || d > 2 || m != Month.OCTOBER)
                && (y != 2020 || d < 5 || d > 8 || m != Month.OCTOBER)
                && (y != 2021 || (d != 1 && d != 4 && d != 5 && d != 6 && d != 7) || m != Month.OCTOBER)
                && (y != 2022 || d < 3 || d > 7 || m != Month.OCTOBER)
                && (y != 2023 || d < 2 || d > 6 || m != Month.OCTOBER)
                && (y != 2024 || ((d < 1 || d > 4) && d != 7) || m != Month.OCTOBER)
                && (y != 2025 || ((d < 1 || d > 3) && (d < 6 || d > 8)) || m != Month.OCTOBER)
                // 70th anniversary of the victory of anti-Japaneses war
                && (y != 2015 || d < 3 || d > 4 || m != Month.SEPTEMBER); // NOLINT(readability-simplify-boolean-expr)
    }

    @Override
    public boolean isWeekend(Weekday w) {
        return w == Weekday.SATURDAY || w == Weekday.SUNDAY;
    }
}
