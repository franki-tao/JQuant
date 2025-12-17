package jquant.time;

import jquant.time.impl.DayCounterImpl;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! day counter class
/*! This class provides methods for determining the length of a time
    period according to given market convention, both as a number
    of days and as a year fraction.

    The Bridge pattern is used to provide the base behavior of the
    day counter.

    \ingroup datetime
*/
public class DayCounter {
    protected DayCounterImpl impl_;

    /*! This constructor can be invoked by derived classes which
        define a given implementation.
    */
    protected DayCounter(DayCounterImpl impl) {
        impl_ = impl;
    }

    /*! The default constructor returns a day counter with a null
        implementation, which is therefore unusable except as a
        placeholder.
    */
    public DayCounter() {
    }

    //! \name DayCounter interface
    //@{
    //!  Returns whether or not the day counter is initialized
    public boolean empty() {
        return impl_ == null;
    }
    //! Returns the name of the day counter.
    /*! \warning This method is used for output and comparison between
            day counters. It is <b>not</b> meant to be used for writing
            switch-on-type code.
    */
    public String name() {
        QL_REQUIRE(impl_ != null, "no day counter implementation provided");
        return impl_.name();
    }
    //! Returns the number of days between two dates.
    public int dayCount(Date d1,Date d2) {
        QL_REQUIRE(impl_ != null, "no day counter implementation provided");
        return impl_.dayCount(d1,d2);
    }
    //! Returns the period between two dates as a fraction of year.
    public double yearFraction(final Date d1, final Date d2, final Date refPeriodStart,  final Date refPeriodEnd) {
        QL_REQUIRE(impl_ != null, "no day counter implementation provided");
        return impl_.yearFraction(d1,d2,refPeriodStart,refPeriodEnd);
    }
    //@}

    @Override
    public String toString() {
        return name();
    }
}
