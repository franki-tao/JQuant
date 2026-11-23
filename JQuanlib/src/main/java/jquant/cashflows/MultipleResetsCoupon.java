package jquant.cashflows;

import jquant.indexes.IborIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

//! multiple-reset coupon
/*! Coupon paying a rate calculated by compounding or averaging
    multiple fixings during its accrual period.

    Port of QuantLib's {@code MultipleResetsCoupon} in
    {@code ql/cashflows/multipleresetscoupon.hpp} and
    {@code ql/cashflows/multipleresetscoupon.cpp}.
*/
public class MultipleResetsCoupon extends FloatingRateCoupon {
    private final List<Date> valueDates_;
    private final List<Date> fixingDates_;
    private final int n_;
    private final List<Double> dt_;
    private final double rateSpread_;

    /*! \param resetSchedule the schedule for the multiple resets. The first and last
                              dates are also the start and end dates of the coupon.
                              Each period specified by the schedule is the underlying
                              period for one fixing; the corresponding fixing date is
                              the passed number of fixing days before the start of
                              the period.
        \param couponSpread  an optional spread added to the final coupon rate.
        \param rateSpread    an optional spread added to each of the underlying fixings.
        \param gearing       an optional multiplier for the final coupon rate.
    */
    public MultipleResetsCoupon(final Date paymentDate,
                                double nominal,
                                final Schedule resetSchedule,
                                int fixingDays,
                                final IborIndex index,
                                double gearing,
                                double couponSpread,
                                double rateSpread,
                                final Date refPeriodStart,
                                final Date refPeriodEnd,
                                final DayCounter dayCounter,
                                final Date exCouponDate) {
        super(paymentDate, nominal,
                resetSchedule.front(), resetSchedule.back(),
                fixingDays, index, gearing, couponSpread,
                refPeriodStart, refPeriodEnd, dayCounter,
                false, exCouponDate);
        rateSpread_ = rateSpread;

        valueDates_ = new ArrayList<>(resetSchedule.dates());

        // fixing dates
        n_ = valueDates_.size() - 1;
        fixingDates_ = new ArrayList<>(n_);
        if (fixingDays_ == 0) {
            for (int i = 0; i < n_; ++i)
                fixingDates_.add(valueDates_.get(i));
        } else {
            for (int i = 0; i < n_; ++i)
                fixingDates_.add(fixingDate(valueDates_.get(i)));
        }

        // accrual times of sub-periods
        dt_ = new ArrayList<>(n_);
        final DayCounter dc = index.dayCounter();
        for (int i = 0; i < n_; ++i)
            dt_.add(dc.yearFraction(valueDates_.get(i), valueDates_.get(i + 1)));
    }

    //! \name Inspectors
    //! fixing dates for the rates to be compounded
    public List<Date> fixingDates() {
        return fixingDates_;
    }

    //! accrual (compounding) periods
    public List<Double> dt() {
        return dt_;
    }

    //! value dates for the rates to be compounded
    public List<Date> valueDates() {
        return valueDates_;
    }

    //! rate spread
    public double rateSpread() {
        return rateSpread_;
    }

    //! \name FloatingRateCoupon interface
    //! the date when the coupon is fully determined
    @Override
    public Date fixingDate() {
        return fixingDates_.get(fixingDates_.size() - 1);
    }

    //! \name Visitability
    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<MultipleResetsCoupon> vv = (Visitor<MultipleResetsCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                super.accept(v);
            }
        }
    }

    private Date fixingDate(final Date valueDate) {
        return index_.fixingCalendar().advance(valueDate,
                -fixingDays_, TimeUnit.DAYS);
    }
}
