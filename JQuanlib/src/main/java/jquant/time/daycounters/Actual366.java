package jquant.time.daycounters;

//! Actual/366 day count convention

import jquant.time.DayCounter;
import jquant.time.impl.Actual366Impl;

/*! Actual/366 day count convention, also known as "Act/366".
    \ingroup daycounters
*/
public class Actual366 extends DayCounter {
    public Actual366(boolean includeLastDay) {
        super(new Actual366Impl(includeLastDay));
    }
    public Actual366() {
        this(false);
    }
}
