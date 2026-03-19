package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! base class for Inter-Bank-Offered-Rate indexes (e.g. %Libor, etc.)
public class IborIndex extends InterestRateIndex {
    protected BusinessDayConvention convention_;
    protected Handle<YieldTermStructure> termStructure_;
    protected boolean endOfMonth_;

    public IborIndex(final String familyName,
                     final Period tenor,
                     int settlementDays,
                     final Currency currency,
                     final Calendar fixingCalendar,
                     BusinessDayConvention convention,
                     boolean endOfMonth,
                     final DayCounter dayCounter,
                     Handle<YieldTermStructure> h) {
        super(familyName, tenor, settlementDays, currency, fixingCalendar, dayCounter);
        convention_ = convention;
        termStructure_ = h;
        endOfMonth_ = endOfMonth;
        registerWith(termStructure_.currentLink());
    }

    @Override
    public Date maturityDate(final Date valueDate) {
        return fixingCalendar().advance(valueDate,
                tenor_,
                convention_,
                endOfMonth_);
    }

    @Override
    public double forecastFixing(final Date fixingDate) {
        Date d1 = valueDate(fixingDate);
        Date d2 = maturityDate(d1);
        double t = dayCounter_.yearFraction(d1, d2, new Date(), new Date());
        QL_REQUIRE(t > 0.0,
                "\n cannot calculate forward rate between " +
                        d1 + " and " + d2 +
                        ":\n non positive time (" + t +
                        ") using " + dayCounter_.name() + " daycounter");
        return forecastFixing(d1, d2, t);
    }

    public BusinessDayConvention businessDayConvention() {
        return convention_;
    }

    public boolean endOfMonth() {
        return endOfMonth_;
    }

    //! the curve used to forecast fixings
    public Handle<YieldTermStructure> forwardingTermStructure() {
        return termStructure_;
    }

    //! returns a copy of itself linked to a different forwarding curve
    public IborIndex clone(final Handle<YieldTermStructure> h) {
        return new IborIndex(familyName(),
                tenor(),
                fixingDays(),
                currency(),
                fixingCalendar(),
                businessDayConvention(),
                endOfMonth(),
                dayCounter(),
                h);
    }

    // overload to avoid date/time (re)calculation
    /* This can be called with cached coupon dates (and it does
       give quite a performance boost to coupon calculations) but
       is potentially misleading: by passing the wrong dates, one
       can ask a 6-months index for a 1-year fixing.

       For that reason, we're leaving this method private and
       we're declaring the IborCoupon class (which uses it) as a
       friend.  Should the need arise, we might promote it to
       public, but before doing that I'd think hard whether we
       have any other way to get the same results.
    */
    public double forecastFixing(final Date d1,
                                  final Date d2,
                                  double t) {
        QL_REQUIRE(!termStructure_.empty(),
                "null term structure set to this instance of " + name());
        double disc1 = termStructure_.currentLink().discount(d1, false);
        double disc2 = termStructure_.currentLink().discount(d2, false);
        return (disc1 / disc2 - 1.0) / t;
    }
}
