package jquant;

import jquant.patterns.LazyObject;
import jquant.patterns.Visitor;
import jquant.time.Date;

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
    public abstract boolean hasOccurred(final Date refDate, Optional<Boolean> includeRefDate);
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
    public abstract boolean tradingExCoupon(final Date refDate);
}
