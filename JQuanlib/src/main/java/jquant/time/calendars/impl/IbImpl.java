package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.CalendarImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IbImpl extends CalendarImpl {
    private CalendarImpl sseImpl_;
    private static final Date[] workingWeekends = {// 2005
            new Date(5, Month.FEBRUARY, 2005),
            new Date(6, Month.FEBRUARY, 2005),
            new Date(30, Month.APRIL, 2005),
            new Date(8, Month.MAY, 2005),
            new Date(8, Month.OCTOBER, 2005),
            new Date(9, Month.OCTOBER, 2005),
            new Date(31, Month.DECEMBER, 2005),
            //2006
            new Date(28, Month.JANUARY, 2006),
            new Date(29, Month.APRIL, 2006),
            new Date(30, Month.APRIL, 2006),
            new Date(30, Month.SEPTEMBER, 2006),
            new Date(30, Month.DECEMBER, 2006),
            new Date(31, Month.DECEMBER, 2006),
            // 2007
            new Date(17, Month.FEBRUARY, 2007),
            new Date(25, Month.FEBRUARY, 2007),
            new Date(28, Month.APRIL, 2007),
            new Date(29, Month.APRIL, 2007),
            new Date(29, Month.SEPTEMBER, 2007),
            new Date(30, Month.SEPTEMBER, 2007),
            new Date(29, Month.DECEMBER, 2007),
            // 2008
            new Date(2, Month.FEBRUARY, 2008),
            new Date(3, Month.FEBRUARY, 2008),
            new Date(4, Month.MAY, 2008),
            new Date(27, Month.SEPTEMBER, 2008),
            new Date(28, Month.SEPTEMBER, 2008),
            // 2009
            new Date(4, Month.JANUARY, 2009),
            new Date(24, Month.JANUARY, 2009),
            new Date(1, Month.FEBRUARY, 2009),
            new Date(31, Month.MAY, 2009),
            new Date(27, Month.SEPTEMBER, 2009),
            new Date(10, Month.OCTOBER, 2009),
            // 2010
            new Date(20, Month.FEBRUARY, 2010),
            new Date(21, Month.FEBRUARY, 2010),
            new Date(12, Month.JUNE, 2010),
            new Date(13, Month.JUNE, 2010),
            new Date(19, Month.SEPTEMBER, 2010),
            new Date(25, Month.SEPTEMBER, 2010),
            new Date(26, Month.SEPTEMBER, 2010),
            new Date(9, Month.OCTOBER, 2010),
            // 2011
            new Date(30, Month.JANUARY, 2011),
            new Date(12, Month.FEBRUARY, 2011),
            new Date(2, Month.APRIL, 2011),
            new Date(8, Month.OCTOBER, 2011),
            new Date(9, Month.OCTOBER, 2011),
            new Date(31, Month.DECEMBER, 2011),
            // 2012
            new Date(21, Month.JANUARY, 2012),
            new Date(29, Month.JANUARY, 2012),
            new Date(31, Month.MARCH, 2012),
            new Date(1, Month.APRIL, 2012),
            new Date(28, Month.APRIL, 2012),
            new Date(29, Month.SEPTEMBER, 2012),
            // 2013
            new Date(5, Month.JANUARY, 2013),
            new Date(6, Month.JANUARY, 2013),
            new Date(16, Month.FEBRUARY, 2013),
            new Date(17, Month.FEBRUARY, 2013),
            new Date(7, Month.APRIL, 2013),
            new Date(27, Month.APRIL, 2013),
            new Date(28, Month.APRIL, 2013),
            new Date(8, Month.JUNE, 2013),
            new Date(9, Month.JUNE, 2013),
            new Date(22, Month.SEPTEMBER, 2013),
            new Date(29, Month.SEPTEMBER, 2013),
            new Date(12, Month.OCTOBER, 2013),
            // 2014
            new Date(26, Month.JANUARY, 2014),
            new Date(8, Month.FEBRUARY, 2014),
            new Date(4, Month.MAY, 2014),
            new Date(28, Month.SEPTEMBER, 2014),
            new Date(11, Month.OCTOBER, 2014),
            // 2015
            new Date(4, Month.JANUARY, 2015),
            new Date(15, Month.FEBRUARY, 2015),
            new Date(28, Month.FEBRUARY, 2015),
            new Date(6, Month.SEPTEMBER, 2015),
            new Date(10, Month.OCTOBER, 2015),
            // 2016
            new Date(6, Month.FEBRUARY, 2016),
            new Date(14, Month.FEBRUARY, 2016),
            new Date(12, Month.JUNE, 2016),
            new Date(18, Month.SEPTEMBER, 2016),
            new Date(8, Month.OCTOBER, 2016),
            new Date(9, Month.OCTOBER, 2016),
            // 2017
            new Date(22, Month.JANUARY, 2017),
            new Date(4, Month.FEBRUARY, 2017),
            new Date(1, Month.APRIL, 2017),
            new Date(27, Month.MAY, 2017),
            new Date(30, Month.SEPTEMBER, 2017),
            // 2018
            new Date(11, Month.FEBRUARY, 2018),
            new Date(24, Month.FEBRUARY, 2018),
            new Date(8, Month.APRIL, 2018),
            new Date(28, Month.APRIL, 2018),
            new Date(29, Month.SEPTEMBER, 2018),
            new Date(30, Month.SEPTEMBER, 2018),
            new Date(29, Month.DECEMBER, 2018),
            // 2019
            new Date(2, Month.FEBRUARY, 2019),
            new Date(3, Month.FEBRUARY, 2019),
            new Date(28, Month.APRIL, 2019),
            new Date(5, Month.MAY, 2019),
            new Date(29, Month.SEPTEMBER, 2019),
            new Date(12, Month.OCTOBER, 2019),
            // 2020
            new Date(19, Month.JANUARY, 2020),
            new Date(26, Month.APRIL, 2020),
            new Date(9, Month.MAY, 2020),
            new Date(28, Month.JUNE, 2020),
            new Date(27, Month.SEPTEMBER, 2020),
            new Date(10, Month.OCTOBER, 2020),
            // 2021
            new Date(7, Month.FEBRUARY, 2021),
            new Date(20, Month.FEBRUARY, 2021),
            new Date(25, Month.APRIL, 2021),
            new Date(8, Month.MAY, 2021),
            new Date(18, Month.SEPTEMBER, 2021),
            new Date(26, Month.SEPTEMBER, 2021),
            new Date(9, Month.OCTOBER, 2021),
            // 2022
            new Date(29, Month.JANUARY, 2022),
            new Date(30, Month.JANUARY, 2022),
            new Date(2, Month.APRIL, 2022),
            new Date(24, Month.APRIL, 2022),
            new Date(7, Month.MAY, 2022),
            new Date(8, Month.OCTOBER, 2022),
            new Date(9, Month.OCTOBER, 2022),
            // 2023
            new Date(28, Month.JANUARY, 2023),
            new Date(29, Month.JANUARY, 2023),
            new Date(23, Month.APRIL, 2023),
            new Date(6, Month.MAY, 2023),
            new Date(25, Month.JUNE, 2023),
            new Date(7, Month.OCTOBER, 2023),
            new Date(8, Month.OCTOBER, 2023),
            // 2024
            new Date(4, Month.FEBRUARY, 2024),
            new Date(9, Month.FEBRUARY, 2024),
            new Date(18, Month.FEBRUARY, 2024),
            new Date(7, Month.APRIL, 2024),
            new Date(28, Month.APRIL, 2024),
            new Date(11, Month.MAY, 2024),
            new Date(14, Month.SEPTEMBER, 2024),
            new Date(29, Month.SEPTEMBER, 2024),
            new Date(12, Month.OCTOBER, 2024),
            // 2025
            new Date(26, Month.JANUARY, 2025),
            new Date(8, Month.FEBRUARY, 2025),
            new Date(27, Month.APRIL, 2025),
            new Date(28, Month.SEPTEMBER, 2025),
            new Date(11, Month.OCTOBER, 2025)};

    public static final Set<Date> workWeekends = new HashSet<Date>();
    static {
        workWeekends.addAll(Arrays.asList(workingWeekends));
    }

    public IbImpl() {
        sseImpl_ = new SseImpl();
    }

    @Override
    public String name() {
        return "China inter bank market";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        // If it is already a SSE business day, it must be a IB business day
        return sseImpl_.isBusinessDay(date) || workWeekends.contains(date);
    }

    @Override
    public boolean isWeekend(Weekday w) {
        return w == Weekday.SATURDAY || w == Weekday.SUNDAY;
    }
}
