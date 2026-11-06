package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.TargetImpl;

/**
 * TARGET calendar.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/time/calendars/target.cpp
 */
public class Target extends Calendar {
    public Target() {
        impl_ = new TargetImpl();
    }
}
