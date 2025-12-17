package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.time.TimeUtils.daysBetween;

public class Actual360Impl extends DayCounterImpl {
    private boolean includeLastDay_;

    public Actual360Impl(boolean includeLastDay) {
        super();
        includeLastDay_ = includeLastDay;
    }

    @Override
    public String name() {
        return includeLastDay_ ? "Actual/360 (inc)" : "Actual/360";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        return TimeUtils.substract(d2, d1) + (includeLastDay_ ? 1 : 0);
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return (daysBetween(d1, d2)
                + (includeLastDay_ ? 1.0 : 0.0)) / 360.0;
    }
}
