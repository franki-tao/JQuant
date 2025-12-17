package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Month;

public class Actual365FixedNLImpl extends DayCounterImpl {
    private static final int[] MonthOffset = {
            0, 31, 59, 90, 120, 151,  // Jan - Jun
            181, 212, 243, 273, 304, 334   // Jun - Dec
    };

    @Override
    public String name() {
        return "Actual/365 (No Leap)";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        int s1 = d1.dayOfMonth()
                + MonthOffset[d1.month().getValue() - 1] + (d1.year() * 365);
        int s2 = d2.dayOfMonth()
                + MonthOffset[d2.month().getValue() - 1] + (d2.year() * 365);

        if (d1.month() == Month.FEBRUARY && d1.dayOfMonth() == 29) {
            --s1;
        }

        if (d2.month() == Month.FEBRUARY && d2.dayOfMonth() == 29) {
            --s2;
        }

        return s2 - s1;
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return dayCount(d1, d2)/365.0;
    }
}
