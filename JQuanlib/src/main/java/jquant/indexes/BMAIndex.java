package jquant.indexes;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.math.Rounding;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.ActualActual;

import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Bond Market Association index
/*! The BMA index is the short-term tax-exempt reference index of
    the Bond Market Association.  It has tenor one week, is fixed
    weekly on Wednesdays and is applied with a one-day's fixing
    gap from Thursdays on for one week.  It is the tax-exempt
    correspondent of the 1M USD-Libor.

    QuantLib: ql/indexes/bmaindex.hpp
*/
public class BMAIndex extends InterestRateIndex {
    private final Handle<YieldTermStructure> termStructure_;

    public BMAIndex() {
        this(null);
    }

    public BMAIndex(Handle<YieldTermStructure> h) {
        super("BMA",
                new Period(1, TimeUnit.WEEKS),
                1,
                usd(),
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                new ActualActual(ActualActual.Convention.ISDA));
        termStructure_ = h;
        if (h != null && !h.empty())
            registerWith(h.currentLink());
    }

    @Override
    public String name() {
        // QuantLib joins the short tenor and the day-counter name: "BMA1W Actual/Actual (ISDA)".
        return "BMA1W Actual/Actual (ISDA)";
    }

    /*! BMA is fixed weekly on Wednesday. If that Wednesday is a holiday,
        a later date is valid only when every day from the Wednesday up to
        but not including it is also a holiday, and the date itself is a
        business day.
    */
    @Override
    public boolean isValidFixingDate(final Date date) {
        Calendar cal = fixingCalendar();
        for (Date d = previousWednesday(date); TimeUtils.less(d, date); d = d.add(1)) {
            if (cal.isBusinessDay(d))
                return false;
        }
        return cal.isBusinessDay(date);
    }

    public Handle<YieldTermStructure> forwardingTermStructure() {
        return termStructure_;
    }

    @Override
    public Date maturityDate(final Date valueDate) {
        Calendar cal = fixingCalendar();
        Date fixingDate = cal.advance(valueDate, -1, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        Date nextWednesday = previousWednesday(fixingDate.add(7));
        return cal.advance(nextWednesday, 1, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
    }

    //! fixing dates between start and end, weekly on the BMA convention
    public Schedule fixingSchedule(final Date start, final Date end) {
        return new MakeSchedule().from(previousWednesday(start))
                .to(nextWednesday(end))
                .withFrequency(Frequency.WEEKLY)
                .withCalendar(fixingCalendar())
                .withConvention(BusinessDayConvention.FOLLOWING)
                .forwards()
                .toSchedule();
    }

    @Override
    public double forecastFixing(final Date fixingDate) {
        QL_REQUIRE(termStructure_ != null && !termStructure_.empty(),
                "null term structure set to this instance of " + name());
        Date start = fixingCalendar().advance(fixingDate, 1, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        Date end = maturityDate(start);
        return termStructure_.currentLink().forwardRate(start, end, dayCounter_,
                Compounding.Simple, Frequency.ANNUAL, false).rate();
    }

    //! Wednesday on or before the given date. QuantLib weekday Wednesday == 4.
    static Date previousWednesday(final Date date) {
        int weekday = date.weekday().getValue();
        if (weekday >= 4)
            return date.add(-(weekday - 4));
        return date.add(4 - weekday - 7);
    }

    static Date nextWednesday(final Date date) {
        return previousWednesday(date.add(7));
    }

    private static Currency usd() {
        return new Currency("U.S. dollar", "USD", 840, "$", "\u00A2", 100,
                new Rounding(), new Currency(), Set.of());
    }
}
