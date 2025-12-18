package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUtils;

public class OneDayCounterImpl extends DayCounterImpl {
    @Override
    public String name() {
        return "1/1";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        return TimeUtils.geq(d2, d1) ? 1 : -1;
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return dayCount(d1, d2);
    }
}
