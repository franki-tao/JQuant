package jquant.time.impl;

import jquant.time.Date;

public class Thirty360NASDImpl extends Thirty360Impl {
    @Override
    public String name() {
        return "30/360 (NASD)";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        int dd1 = d1.dayOfMonth(), dd2 = d2.dayOfMonth();
        int mm1 = d1.month().getValue(), mm2 = d2.month().getValue();
        int yy1 = d1.year(), yy2 = d2.year();

        if (dd1 == 31) {
            dd1 = 30;
        }
        if (dd2 == 31 && dd1 >= 30) {
            dd2 = 30;
        }
        if (dd2 == 31 && dd1 < 30) {
            dd2 = 1;
            mm2++;
        }

        return 360 * (yy2 - yy1) + 30 * (mm2 - mm1) + (dd2 - dd1);
    }
}
