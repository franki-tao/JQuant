package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.UnitedKingdomImpl;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * United Kingdom calendars.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/time/calendars/unitedkingdom.hpp
 */
public class UnitedKingdom extends Calendar {
    public enum Market { Settlement, Exchange, Metals }

    public UnitedKingdom() {
        this(Market.Settlement);
    }

    public UnitedKingdom(Market market) {
        switch (market) {
            case Settlement:
                impl_ = new UnitedKingdomImpl("UK settlement");
                break;
            case Exchange:
                impl_ = new UnitedKingdomImpl("London stock exchange");
                break;
            case Metals:
                impl_ = new UnitedKingdomImpl("London metals exchange");
                break;
            default:
                QL_FAIL("unknown market");
        }
    }
}
