package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.AustraliaAsxImpl;
import jquant.time.calendars.impl.AustraliaSettlementImpl;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Australian calendar.
 *
 * <p>Two markets, mirrored from {@code ql/time/calendars/australia.hpp}:
 * the generic settlement calendar (the default) and the ASX exchange
 * calendar.
 */
public class Australia extends Calendar {
    public enum Market { Settlement,     //!< generic settlement calendar
        ASX              //!< Australia ASX calendar
    }

    public Australia(Market market) {
        switch (market) {
            case Settlement:
                impl_ = new AustraliaSettlementImpl();
                break;
            case ASX:
                impl_ = new AustraliaAsxImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }

    public Australia() {
        this(Market.Settlement);
    }
}
