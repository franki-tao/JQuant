package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUtils;

import static jquant.time.TimeUtils.isLastOfFebruary;

public class Thirty360ISDAImpl extends Thirty360Impl {
    private Date terminationDate_;
    public Thirty360ISDAImpl(Date terminationDate) {
        this.terminationDate_ = terminationDate;
    }

    @Override
    public String name() {
        return "30E/360 (ISDA)";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        int dd1 = d1.dayOfMonth(), dd2 = d2.dayOfMonth();
        Month mm1 = d1.month(), mm2 = d2.month();
        int yy1 = d1.year(), yy2 = d2.year();

        if (dd1 == 31) { dd1 = 30; }
        if (dd2 == 31) { dd2 = 30; }

        if (isLastOfFebruary(dd1, mm1, yy1)) { dd1 = 30; }

        if (TimeUtils.neq(d2, terminationDate_) && isLastOfFebruary(dd2, mm2, yy2)) { dd2 = 30; }

        return 360*(yy2-yy1) + 30*(mm2.getValue()-mm1.getValue()) + (dd2-dd1);
    }
}
