package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.List;

//! Zero-yield term structure
/*! This abstract class acts as an adapter to YieldTermStructure
    allowing the programmer to implement only the
    <tt>zeroYieldImpl(Time)</tt> method in derived classes.

    Discount and forward are calculated from zero yields.

    Zero rates are assumed to be annual continuous compounding.

    \ingroup yieldtermstructures
*/
public abstract class ZeroYieldStructure extends YieldTermStructure {
    /*! \name Constructors
        See the TermStructure documentation for issues regarding
        constructors.
    */
    //@{
    public ZeroYieldStructure(final DayCounter dc) {
        super(dc);
    }

    public ZeroYieldStructure(final Date refDate,
                              final Calendar cal,
                              final DayCounter dc,
                              final List<Handle<Quote>> jumps,
                              final List<Date> jumpDates) {
        super(refDate, cal, dc, jumps, jumpDates);
    }

    public ZeroYieldStructure(
            int settlementDays,
            final Calendar cal,
            final DayCounter dc,
            final List<Handle<Quote>> jumps,
            final List<Date> jumpDates) {
        super(settlementDays, cal, dc, jumps, jumpDates);
    }

    //@}
    //! zero-yield calculation
    protected abstract double zeroYieldImpl(double time);

    /*! Returns the discount factor for the given date calculating it
        from the zero yield.
    */
    protected double discountImpl(double t) {
        if (t == 0.0)     // this acts as a safe guard in cases where
            return 1.0;   // zeroYieldImpl(0.0) would throw.

        double r = zeroYieldImpl(t);
        return Math.exp(-r * t);
    }
}
