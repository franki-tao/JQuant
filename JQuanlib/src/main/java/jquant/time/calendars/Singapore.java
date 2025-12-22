package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.SgxImpl;
import jquant.time.impl.CalendarImpl;

import static jquant.math.CommonUtil.QL_FAIL;

//! %Singapore calendars
/*! Holidays for the Singapore exchange
    (data from
     <http://www.sgx.com/wps/portal/sgxweb/home/trading/securities/trading_hours_calendar>):
    <ul>
    <li>Saturdays</li>
    <li>Sundays</li>
    <li>New Year's day, January 1st</li>
    <li>Good Friday</li>
    <li>Labour Day, May 1st</li>
    <li>National Day, August 9th</li>
    <li>Christmas, December 25th </li>
    </ul>

    Other holidays for which no rule is given
    (data available for 2004-2010, 2012-2014, 2019-2024 only:)
    <ul>
    <li>Chinese New Year</li>
    <li>Hari Raya Haji</li>
    <li>Vesak Poya Day</li>
    <li>Deepavali</li>
    <li>Diwali</li>
    <li>Hari Raya Puasa</li>
    </ul>

    \ingroup calendars
*/
public class Singapore extends Calendar {
    public enum Market { SGX    //!< Singapore exchange
    }
    public Singapore(Market m) {
        CalendarImpl impl = new SgxImpl();
        if (m == Market.SGX) {
            impl_ = impl;
        } else {
            QL_FAIL("unknown market");
        }
    }
    public Singapore() {
        this(Market.SGX);
    }
}
