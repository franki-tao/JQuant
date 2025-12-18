package jquant.time.impl;

import jquant.time.Date;

public class Thirty365Impl extends DayCounterImpl {
    @Override
    public String name() {
        return "30/365";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        int dd1 = d1.dayOfMonth(), dd2 = d2.dayOfMonth();
        int mm1 = d1.month().getValue(), mm2 = d2.month().getValue();
        int yy1 = d1.year(), yy2 = d2.year();

        return 360 * (yy2 - yy1) + 30 * (mm2 - mm1) + (dd2 - dd1);
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return dayCount(d1, d2) / 365.0;
    }
}
