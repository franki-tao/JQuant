package jquant.time.impl;

import jquant.time.Date;

import static jquant.time.TimeUtils.daysBetween;

public class Actual364Impl extends DayCounterImpl {
    @Override
    public String name() {
        return "Actual/364";
    }
    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return daysBetween(d1, d2) / 364.0;
    }
}
