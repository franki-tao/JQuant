package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.OneDayCounterImpl;

//! 1/1 day count convention
/*! \ingroup daycounters */
public class OneDayCounter extends DayCounter {
    public OneDayCounter() {
        super(new OneDayCounterImpl());
    }
}
