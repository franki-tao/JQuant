package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.Actual360Impl;

//! Actual/360 day count convention

/*! Actual/360 day count convention, also known as "Act/360", or "A/360".

    \ingroup daycounters
*/
public class Actual360 extends DayCounter {
    public Actual360(boolean includeLastDay) {
        super(new Actual360Impl(includeLastDay));
    }
}
