package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.BrazilExchangeImpl;
import jquant.time.calendars.impl.BrazilSettlementImpl;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Brazilian calendar.
 *
 * <p>Two markets, mirrored from {@code ql/time/calendars/brazil.hpp}: the
 * generic settlement calendar (the default, named "Brazil") and the BOVESPA
 * exchange calendar (named "BOVESPA").
 */
public class Brazil extends Calendar {
    public enum Market { Settlement,     //!< generic settlement calendar
        Exchange             //!< BOVESPA calendar
    }

    public Brazil(Market market) {
        switch (market) {
            case Settlement:
                impl_ = new BrazilSettlementImpl();
                break;
            case Exchange:
                impl_ = new BrazilExchangeImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }

    public Brazil() {
        this(Market.Settlement);
    }
}
