package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUtils;

import static jquant.time.TimeUtils.daysBetween;

public class ActualActualISDAImpl extends DayCounterImpl {
    @Override
    public String name() {
        return "Actual/Actual (ISDA)";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        if (TimeUtils.equals(d1, d2))
            return 0.0;

        if (TimeUtils.greater(d1, d2))
            return -yearFraction(d2, d1, new Date(), new Date());

        int y1 = d1.year(), y2 = d2.year();
        double dib1 = (Date.isLeap(y1) ? 366.0 : 365.0),
                dib2 = (Date.isLeap(y2) ? 366.0 : 365.0);

        double sum = y2 - y1 - 1;
        sum += daysBetween(d1, new Date(1, Month.JANUARY, y1 + 1)) / dib1;
        sum += daysBetween(new Date(1, Month.JANUARY, y2), d2) / dib2;
        return sum;
    }
}
