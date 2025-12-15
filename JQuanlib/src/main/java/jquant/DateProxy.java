package jquant;

import jquant.time.Date;
import jquant.time.TimeUtils;
import jquant.utilities.ObservableValue;

public class DateProxy extends ObservableValue<Date> {
    public DateProxy() {
        super(new Date());
    }

    public DateProxy equal(Date d) {
        if (TimeUtils.neq(super.getValue(), d)) {
            super.setValue(d);
        }
        return this;
    }

    public Date Date() {
        if (TimeUtils.equals(super.getValue(),new Date())) {
            return Date.todaysDate();
        }
        return super.getValue();
    }
}
