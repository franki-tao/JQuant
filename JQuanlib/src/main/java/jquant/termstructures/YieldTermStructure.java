package jquant.termstructures;

import jquant.*;
import jquant.math.CommonUtil;
import jquant.time.*;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Interest-rate term structure
/*! This abstract class defines the interface of concrete
    interest rate structures which will be derived from this one.

    \ingroup yieldtermstructures

    \test observability against evaluation date changes is checked.
*/
public abstract class YieldTermStructure extends TermStructure {
    // time interval used in finite differences
    private final double dt = 0.0001;
    private List<Handle<Quote>> jumps_;
    private List<Date> jumpDates_;
    private List<Double> jumpTimes_;
    private int nJumps_ = 0;
    private Date latestReference_;

    /*! \name Constructors
        See the TermStructure documentation for issues regarding
        constructors.
    */
    //@{
    public YieldTermStructure(final DayCounter dc) {
        super(dc);
    }

    public YieldTermStructure(final Date referenceDate,
                              final Calendar cal,
                              final DayCounter dc,
                              List<Handle<Quote>> jumps,
                              final List<Date> jumpDates) {
        super(referenceDate, cal, dc);
        jumps_ = jumps;
        jumpDates_ = jumpDates;
        jumpTimes_ = CommonUtil.ArrayInit(jumpDates.size());
        nJumps_ = jumps_.size();
        setJumps(referenceDate());
        for (int i = 0; i < nJumps_; ++i)
            registerWith(jumps_.get(i).get());
    }

    public YieldTermStructure(int settlementDays,
                              final Calendar cal,
                              final DayCounter dc,
                              List<Handle<Quote>> jumps,
                              final List<Date> jumpDates) {
        super(settlementDays, cal, dc);
        jumps_ = jumps;
        jumpDates_ = jumpDates;
        jumpTimes_ = CommonUtil.ArrayInit(jumpDates.size());
        nJumps_ = jumps_.size();
        setJumps(referenceDate());
        for (int i = 0; i < nJumps_; ++i)
            registerWith(jumps_.get(i).get());
    }

    //@}

    /*! \name Discount factors

        These methods return the discount factor from a given date or time
        to the reference date.  In the latter case, the time is calculated
        as a fraction of year from the reference date.
    */
    //@{
    // extrapolate = false
    public double discount(final Date d, boolean extrapolate) {
        return discount(timeFromReference(d), extrapolate);
    }

    /*! The same day-counting rule used by the term structure
        should be used for calculating the passed time t.
    */
    public double discount(double t, boolean extrapolate) {
        checkRange(t, extrapolate);

        if (jumps_.isEmpty())
            return discountImpl(t);

        double jumpEffect = 1.0;
        for (int i = 0; i < nJumps_; ++i) {
            if (jumpTimes_.get(i) > 0 && jumpTimes_.get(i) < t) {
                QL_REQUIRE(jumps_.get(i).getValue().isValid(),
                        "invalid " + (i + 1) + " jump quote");
                double thisJump = jumps_.get(i).getValue().value();
                QL_REQUIRE(thisJump > 0.0,
                        "invalid " + (i + 1) + " jump value: " +
                                thisJump);
                jumpEffect *= thisJump;
            }
        }
        return jumpEffect * discountImpl(t);
    }
    //@}

    /*! \name Zero-yield rates

        These methods return the implied zero-yield rate for a
        given date or time.  In the former case, the time is
        calculated as a fraction of year from the reference date.
    */
    //@{
    /*! The resulting interest rate has the required daycounting
        rule.
    */
    public InterestRate zeroRate(final Date d,
                                 final DayCounter dayCounter,
                                 Compounding comp,
                                 Frequency freq,
                                 boolean extrapolate) {
        double t = timeFromReference(d);
        if (t == 0) {
            double compound = 1.0 / discount(dt, extrapolate);
            // t has been calculated with a possibly different daycounter
            // but the difference should not matter for very small times
            return InterestRate.impliedRate(compound,
                    dayCounter, comp, freq,
                    dt);
        }
        double compound = 1.0 / discount(t, extrapolate);
        return InterestRate.impliedRate(compound,
                dayCounter, comp, freq,
                referenceDate(), d, new Date(), new Date());
    }

    /*! The resulting interest rate has the same day-counting rule
        used by the term structure. The same rule should be used
        for calculating the passed time t.
    */
    public InterestRate zeroRate(double t,
                                 Compounding comp,
                                 Frequency freq,
                                 boolean extrapolate) {
        if (t == 0.0) t = dt;
        double compound = 1.0 / discount(t, extrapolate);
        return InterestRate.impliedRate(compound,
                dayCounter(), comp, freq,
                t);
    }
    //@}

    /*! \name Forward rates

        These methods returns the forward interest rate between two dates
        or times.  In the former case, times are calculated as fractions
        of year from the reference date.

        If both dates (times) are equal the instantaneous forward rate is
        returned.
    */
    //@{
    /*! The resulting interest rate has the required day-counting
        rule.
    */
    public InterestRate forwardRate(final Date d1,
                                    final Date d2,
                                    final DayCounter dayCounter,
                                    Compounding comp,
                                    Frequency freq,
                                    boolean extrapolate) {
        if (TimeUtils.equals(d1, d2)) {
            checkRange(d1, extrapolate);
            double t1 = Math.max(timeFromReference(d1) - dt / 2.0, 0.0);
            double t2 = t1 + dt;
            double compound =
                    discount(t1, true) / discount(t2, true);
            // times have been calculated with a possibly different daycounter
            // but the difference should not matter for very small times
            return InterestRate.impliedRate(compound,
                    dayCounter, comp, freq,
                    dt);
        }
        QL_REQUIRE(TimeUtils.less(d1, d2), d1 + " later than " + d2);
        double compound = discount(d1, extrapolate) / discount(d2, extrapolate);
        return InterestRate.impliedRate(compound,
                dayCounter, comp, freq,
                d1, d2, new Date(), new Date());
    }

    /*! The resulting interest rate has the required day-counting
        rule.
        \warning dates are not adjusted for holidays
    */
    public InterestRate forwardRate(final Date d,
                                    final Period p,
                                    final DayCounter dayCounter,
                                    Compounding comp,
                                    Frequency freq,
                                    boolean extrapolate) {
        return forwardRate(d, d.add(p), dayCounter, comp, freq, extrapolate);
    }

    /*! The resulting interest rate has the same day-counting rule
        used by the term structure. The same rule should be used
        for calculating the passed times t1 and t2.
    */
    // freq = ANNUAL extrapolate = false
    public InterestRate forwardRate(double t1,
                                    double t2,
                                    Compounding comp,
                                    Frequency freq,
                                    boolean extrapolate) {
        double compound;
        if (t2 == t1) {
            checkRange(t1, extrapolate);
            t1 = Math.max(t1 - dt / 2.0, 0.0);
            t2 = t1 + dt;
            compound = discount(t1, true) / discount(t2, true);
        } else {
            QL_REQUIRE(t2 > t1, "t2 (" + t2 + ") < t1 (" + t2 + ")");
            compound = discount(t1, extrapolate) / discount(t2, extrapolate);
        }
        return InterestRate.impliedRate(compound,
                dayCounter(), comp, freq,
                t2 - t1);
    }

    //@}

    //! \name Observer interface
    //@{
    @Override
    public void update() {
        if (moving_)
            updated_ = false;
        notifyObservers();
        Date newReference = new Date();
        try {
            newReference = referenceDate();
            if (TimeUtils.neq(newReference, latestReference_))
                setJumps(newReference);
        } catch (Exception e) {
            if (TimeUtils.equals(newReference, new Date())) {
                // the curve couldn't calculate the reference
                // date. Most of the times, this is because some
                // underlying handle wasn't set, so we can just absorb
                // the exception and continue; the jumps will be set
                // correctly when a valid underlying is set.
                return;
            } else {
                // something else happened during the call to
                // setJumps(), so we let the exception bubble up.
                throw new RuntimeException();
            }
        }
    }
    //@}

    /*! \name Calculations

        This method must be implemented in derived classes to
        perform the actual calculations. When it is called,
        range check has already been performed; therefore, it
        must assume that extrapolation is required.
    */
    //@{
    //! discount factor calculation
    protected abstract double discountImpl(double t);

    private void setJumps(final Date referenceDate) {
        if (jumpDates_.isEmpty() && !jumps_.isEmpty()) { // turn of year dates
            jumpDates_ = CommonUtil.ArrayInit(nJumps_);
            jumpTimes_ = CommonUtil.ArrayInit(nJumps_);
            int y = referenceDate.year();
            for (int i = 0; i < nJumps_; ++i)
                jumpDates_.set(i, new Date(31, Month.DECEMBER, y + i));
        } else { // fixed dates
            QL_REQUIRE(jumpDates_.size() == nJumps_,
                    "mismatch between number of jumps (" + nJumps_ +
                            ") and jump dates (" + jumpDates_.size() + ")");
        }
        for (int i = 0; i < nJumps_; ++i)
            jumpTimes_.set(i, timeFromReference(jumpDates_.get(i)));
        latestReference_ = referenceDate;
    }
}
