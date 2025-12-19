package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.IbImpl;
import jquant.time.calendars.impl.SseImpl;

import static jquant.math.CommonUtil.QL_FAIL;

//! Chinese calendar
/*! Holidays:
    <ul>
    <li>Saturdays</li>
    <li>Sundays</li>
    <li>New Year's day, January 1st (possibly followed by one or
        two more holidays)</li>
    <li>Labour Day, first week in May</li>
    <li>National Day, one week from October 1st</li>
    </ul>

    Other holidays for which no rule is given (data available for
    2004-2019 only):
    <ul>
    <li>Chinese New Year</li>
    <li>Ching Ming Festival</li>
    <li>Tuen Ng Festival</li>
    <li>Mid-Autumn Festival</li>
    <li>70th anniversary of the victory of anti-Japaneses war</li>
    </ul>

    SSE data from <http://www.sse.com.cn/>
    IB data from <http://www.chinamoney.com.cn/>

    \ingroup calendars
*/
public class China extends Calendar {
    public enum Market { SSE,    //!< Shanghai stock exchange
        IB      //!< Interbank calendar
    }
    public China(Market m) {
        switch (m) {
            case SSE:
                impl_ = new SseImpl();
                break;
            case IB:
                impl_ = new IbImpl();
                break;
            default:
                QL_FAIL("unknown market");
        }
    }
}
