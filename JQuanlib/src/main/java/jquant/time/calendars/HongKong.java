package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.HkexImpl;
import jquant.time.impl.CalendarImpl;

import java.util.Objects;

import static jquant.math.CommonUtil.QL_FAIL;

///! Hong Kong calendars
/**! Holidays:
    <ul>
    <li>Saturdays</li>
    <li>Sundays</li>
    <li>New Year's Day, January 1st (possibly moved to Monday)</li>
    <li>Good Friday</li>
    <li>Easter Monday</li>
    <li>Labor Day, May 1st (possibly moved to Monday)</li>
    <li>SAR Establishment Day, July 1st (possibly moved to Monday)</li>
    <li>National Day, October 1st (possibly moved to Monday)</li>
    <li>Christmas, December 25th</li>
    <li>Boxing Day, December 26th</li>
    </ul>

    Other holidays for which no rule is given
    (data available for 2004-2015 only:)
    <ul>
    <li>Lunar New Year</li>
    <li>Chinese New Year</li>
    <li>Ching Ming Festival</li>
    <li>Buddha's birthday</li>
    <li>Tuen NG Festival</li>
    <li>Mid-autumn Festival</li>
    <li>Chung Yeung Festival</li>
    </ul>

    Data from <http://www.hkex.com.hk>

    \ingroup calendars
*/
public class HongKong extends Calendar {
    public enum Market { HKEx    //!< Hong Kong stock exchange
    }
    public HongKong(Market m) {
        CalendarImpl impl = new HkexImpl();
        if (Objects.requireNonNull(m) == Market.HKEx) {
            impl_ = impl;
        } else {
            QL_FAIL("unknown market");
        }
    }

    public HongKong() {
        this(Market.HKEx);
    }
}
