package jquant.time.impl;

import jquant.time.Date;

import static jquant.time.TimeUtils.daysBetween;

public class Actual365FixedImpl extends DayCounterImpl{
    @Override
    public String name() {
        return "Actual/365 (Fixed)";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return daysBetween(d1,d2)/365.0;
    }
}
