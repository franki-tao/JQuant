package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;

public class LiborImpactImpl extends SettlementImpl {
    @Override
    public String name() {
        return "US with Libor impact";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        // Since 2015 Independence Day only impacts Libor if it falls
        // on a weekday
        Weekday w = date.weekday();
        int d = date.dayOfMonth();
        Month m = date.month();
        int y = date.year();
        if (((d == 5 && w == Weekday.MONDAY) ||
                (d == 3 && w == Weekday.FRIDAY)) && m == Month.JULY && y >= 2015)
            return true;
        return super.isBusinessDay(date);
    }
}
