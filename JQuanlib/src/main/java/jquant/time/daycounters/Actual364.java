package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.Actual364Impl;

//! Actual/364 day count convention
/*! \ingroup daycounters */
public class Actual364 extends DayCounter {
    public Actual364() {
        super(new Actual364Impl());
    }
}
