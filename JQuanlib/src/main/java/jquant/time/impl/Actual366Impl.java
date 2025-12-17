package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.time.TimeUtils.daysBetween;

public class Actual366Impl extends DayCounterImpl {
    private boolean includeLastDay_;

    public Actual366Impl(boolean includeLastDay) {
        this.includeLastDay_ = includeLastDay;
    }

    @Override
    public String name() {
        return includeLastDay_ ? "Actual/366 (inc)" : "Actual/366";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        return TimeUtils.substract(d2, d1) + (includeLastDay_ ? 1 : 0);
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return (daysBetween(d1, d2)
                + (includeLastDay_ ? 1.0 : 0.0)) / 366.0;
    }
}
