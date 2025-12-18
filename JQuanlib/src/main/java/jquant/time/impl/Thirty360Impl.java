package jquant.time.impl;

import jquant.time.Date;

public abstract class Thirty360Impl extends DayCounterImpl{
    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return dayCount(d1,d2)/360.0;
    }
}
