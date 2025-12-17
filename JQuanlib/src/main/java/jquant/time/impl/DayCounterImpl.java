package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUtils;

//! abstract base class for day counter implementations
public abstract class DayCounterImpl {
    public abstract String name();
    //! to be overloaded by more complex day counters
    public int dayCount(final Date d1,  final Date d2) {
        return TimeUtils.substract(d2,d1);
    }
    public abstract double yearFraction(final Date d1, final Date d2, final Date refPeriodStart,  final Date refPeriodEnd);
}
