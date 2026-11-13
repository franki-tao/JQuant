package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.MexicoBmvImpl;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Mexican calendars.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/time/calendars/mexico.hpp
 */
public class Mexico extends Calendar {
    public enum Market { BMV }

    public Mexico() {
        this(Market.BMV);
    }

    public Mexico(Market market) {
        switch (market) {
            case BMV:
                impl_ = new MexicoBmvImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }
}
