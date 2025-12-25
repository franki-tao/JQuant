package time.impl;

import jquant.time.Date;
import jquant.time.impl.WesternImpl;

public class WeekendsOnlyImpl extends WesternImpl {
    @Override
    public String name() {
        return "weekends only";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        return !isWeekend(date.weekday());
    }
}
