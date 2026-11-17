package jquant;

import jquant.patterns.LazyObject;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.Optional;

/**
 * Base class for cash flows
 * This class is purely virtual and acts as a base class for the actual cash flow implementations.
 */
public abstract class CashFlow extends LazyObject implements Event {
    //! \name Event interface
    //@{
    //! \note This is inherited from the event class
    public abstract Date date();
    /*
     * returns true if an event has already occurred before a date
     * overloads Event::hasOccurred in order to take Settings::includeTodaysCashflows in account
     */
    @Override
    public boolean hasOccurred(final Date refDate, Optional<Boolean> includeRefDate) {
        return hasOccurred(date(), refDate, includeRefDate);
    }
    //@}
    //! \name LazyObject interface
    //@{
    public abstract void performCalculations();
    //@}
    //! \name CashFlow interface
    //@{
    /*
     * returns the amount of the cash flow
     * Remarks:
     * The amount is not discounted, i.e., it is the actual amount paid at the cash flow date.
     */
    public abstract double amount();
    /*returns the date that the cash flow trades exCoupon*/
    public abstract Date exCouponDate();
    /*returns true if the cashflow is trading ex-coupon on the refDate*/
    public boolean tradingExCoupon(final Date refDate) {
        return tradingExCoupon(exCouponDate(), refDate);
    }

    /**
     * A cash flow on {@code refDate} has occurred unless reference-date events are included.
     * QuantLib {@code CashFlow::hasOccurred}.
     */
    public static boolean hasOccurred(Date eventDate, Date refDate, Optional<Boolean> includeRefDate) {
        // easy and quick handling of most cases
        if (!isNullDate(refDate)) {
            if (TimeUtils.less(refDate, eventDate))
                return false;
            if (TimeUtils.less(eventDate, refDate))
                return true;
        }

        Date evaluationDate = Settings.instance.evaluationDate().Date();
        if (isNullDate(refDate) || TimeUtils.equals(refDate, evaluationDate)) {
            // today's date; we override the bool with the one
            // specified in the settings (if any)
            Optional<Boolean> includeToday = Settings.instance.includeTodaysCashFlows();
            if (includeToday != null && includeToday.isPresent())
                includeRefDate = includeToday;
        }

        Date ref = isNullDate(refDate) ? evaluationDate : refDate;
        boolean include = includeRefDate != null && includeRefDate.isPresent()
                ? includeRefDate.get()
                : Settings.instance.includeReferenceDateEvents();
        if (include)
            return TimeUtils.less(eventDate, ref);
        return !TimeUtils.greater(eventDate, ref);
    }

    /**
     * QuantLib {@code CashFlow::tradingExCoupon}.  The reference date
     * defaults to the evaluation date.
     */
    public static boolean tradingExCoupon(Date exCouponDate, Date refDate) {
        if (isNullDate(exCouponDate))
            return false;
        Date ref = isNullDate(refDate)
                ? Settings.instance.evaluationDate().Date() : refDate;
        return TimeUtils.leq(exCouponDate, ref);
    }

    private static boolean isNullDate(Date d) {
        return d == null || d.serialNumber() == 0;
    }
}
