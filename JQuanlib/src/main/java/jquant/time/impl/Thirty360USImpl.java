package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Month;

import static jquant.time.TimeUtils.isLastOfFebruary;

public class Thirty360USImpl extends Thirty360Impl {
    @Override
    public String name() {
        return "30/360 (US)";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        int dd1 = d1.dayOfMonth(), dd2 = d2.dayOfMonth();
        Month mm1 = d1.month(), mm2 = d2.month();
        int yy1 = d1.year(), yy2 = d2.year();

        // See https://en.wikipedia.org/wiki/Day_count_convention#30/360_US
        // NOTE: the order of checks is important
        if (isLastOfFebruary(dd1, mm1, yy1)) {
            if (isLastOfFebruary(dd2, mm2, yy2)) {
                dd2 = 30;
            }
            dd1 = 30;
        }
        if (dd2 == 31 && dd1 >= 30) {
            dd2 = 30;
        }
        if (dd1 == 31) {
            dd1 = 30;
        }

        return 360 * (yy2 - yy1) + 30 * (mm2.getValue() - mm1.getValue()) + (dd2 - dd1);
    }
}
