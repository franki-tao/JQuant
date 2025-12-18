package jquant.time.daycounters;

import jquant.time.Calendar;
import jquant.time.DayCounter;
import jquant.time.impl.Business252Impl;

//! Business/252 day count convention
/*! \ingroup daycounters */
public class Business252 extends DayCounter {
    public Business252(Calendar c) {
        super(new Business252Impl(c));
    }
}
