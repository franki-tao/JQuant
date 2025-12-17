package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.Actual36525Impl;

//! Actual/365.25 day count convention

/*! Actual/365.25 day count convention, also known as "Act/365.25", or "A/365.25".
    \ingroup daycounters
*/
public class Actual36525 extends DayCounter {
    public  Actual36525(boolean includeLastDay) {
        super(new Actual36525Impl(includeLastDay));
    }
    public Actual36525() {
        this(false);
    }
}
