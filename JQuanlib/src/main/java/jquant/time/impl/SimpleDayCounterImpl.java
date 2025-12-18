package jquant.time.impl;

import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.daycounters.Thirty360;

public class SimpleDayCounterImpl extends DayCounterImpl {
    private static final DayCounter fallback = new Thirty360(Thirty360.Convention.BondBasis);

    @Override
    public String name() {
        return "Simple";
    }

    @Override
    public int dayCount(Date d1, Date d2) {
        return fallback.dayCount(d1, d2);
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        int dm1 = d1.dayOfMonth(),
                dm2 = d2.dayOfMonth();

        if (dm1 == dm2 ||
                // e.g., Aug 30 -> Feb 28 ?
                (dm1 > dm2 && Date.isEndOfMonth(d2)) ||
                // e.g., Feb 28 -> Aug 30 ?
                (dm1 < dm2 && Date.isEndOfMonth(d1))) {

            return (d2.year() - d1.year()) +
                    ((d2.month().getValue()) - (d1.month().getValue())) / 12.0;

        } else {
            return fallback.yearFraction(d1, d2, refPeriodStart, refPeriodEnd);
        }
    }
}
