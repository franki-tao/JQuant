package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.Thirty365Impl;

//! 30/365 day count convention
/*! \ingroup daycounters */
public class Thirty365 extends DayCounter {
    public Thirty365() {
        super(new Thirty365Impl());
    }
}
