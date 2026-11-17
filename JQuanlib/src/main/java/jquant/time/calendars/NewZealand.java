package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.NewZealandImpl;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * New Zealand calendars.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/time/calendars/newzealand.hpp
 */
public class NewZealand extends Calendar {
    public enum Market { Wellington, Auckland, NZFMA }

    public NewZealand() {
        this(Market.Wellington);
    }

    public NewZealand(Market market) {
        switch (market) {
            case Wellington:
                impl_ = new NewZealandImpl.WellingtonImpl();
                break;
            case Auckland:
                impl_ = new NewZealandImpl.AucklandImpl();
                break;
            case NZFMA:
                impl_ = new NewZealandImpl.NZFMAImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }
}
