package jquant.time;

import jquant.time.impl.CalendarImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.TimeUtils.daysBetweenImpl;

//! %calendar class
/*! This class provides methods for determining whether a date is a
    business day or a holiday for a given market, and for
    incrementing/decrementing a date of a given number of business days.

    The Bridge pattern is used to provide the base behavior of the
    calendar, namely, to determine whether a date is a business day.

    A calendar should be defined for specific exchange holiday schedule
    or for general country holiday schedule. Legacy city holiday schedule
    calendars will be moved to the exchange/country convention.

    \ingroup datetime

    \test the methods for adding and removing holidays are tested
          by inspecting the calendar before and after their
          invocation.
*/
public abstract class Calendar {
    protected CalendarImpl impl_;

    /*! The default constructor returns a calendar with a null
        implementation, which is therefore unusable except as a
        placeholder.
    */
    public Calendar() {
    }

    //! \name Calendar interface
    //@{
    //!  Returns whether or not the calendar is initialized
    public boolean empty() {
        return impl_ == null;
    }

    //! Returns the name of the calendar.
    /*! \warning This method is used for output and comparison between
            calendars. It is <b>not</b> meant to be used for writing
            switch-on-type code.
    */
    public String name() {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        return impl_.name();
    }
    /*! Returns <tt>true</tt> iff the date is a business day for the
        given market.
    */

    /*! Returns the set of added holidays for the given calendar */
    public final Set<Date> addedHolidays() {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        return impl_.addedHolidays;
    }

    /*! Returns the set of removed holidays for the given calendar */
    public final Set<Date> removedHolidays() {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        return impl_.removedHolidays;
    }

    /*! Clear the set of added and removed holidays */
    public void resetAddedAndRemovedHolidays() {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        impl_.addedHolidays.clear();
        impl_.removedHolidays.clear();
    }

    public boolean isBusinessDay(final Date d) {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        if (!impl_.addedHolidays.isEmpty() &&
                impl_.addedHolidays.contains(d))
            return false;

        if (!impl_.removedHolidays.isEmpty() &&
                impl_.removedHolidays.contains(d))
            return true;

        return impl_.isBusinessDay(d);
    }

    /*! Returns <tt>true</tt> iff the date is a holiday for the given
        market.
    */
    public boolean isHoliday(final Date d) {
        return !isBusinessDay(d);
    }

    /*! Returns <tt>true</tt> iff the weekday is part of the
        weekend for the given market.
    */
    public boolean isWeekend(Weekday w) {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        return impl_.isWeekend(w);
    }

    /*! Returns <tt>true</tt> iff in the given market, the date is on
        or before the first business day for that month.
    */
    public boolean isStartOfMonth(final Date d) {
        return TimeUtils.leq(d, startOfMonth(d));
    }

    //! first business day of the month to which the given date belongs
    public Date startOfMonth(final Date d) {
        return adjust(Date.startOfMonth(d), BusinessDayConvention.FOLLOWING);
    }

    /*! Returns <tt>true</tt> iff in the given market, the date is on
        or after the last business day for that month.
    */
    public boolean isEndOfMonth(final Date d) {
        return TimeUtils.geq(d, endOfMonth(d));
    }

    //! last business day of the month to which the given date belongs
    public Date endOfMonth(final Date d) {
        return adjust(Date.endOfMonth(d), BusinessDayConvention.PRECEDING);
    }

    /*! Adds a date to the set of holidays for the given calendar. */
    public void addHoliday(final Date d) {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        // if d was a genuine holiday previously removed, revert the change
        impl_.removedHolidays.remove(d);
        // if it's already a holiday, leave the calendar alone.
        // Otherwise, add it.
        if (impl_.isBusinessDay(d))
            impl_.addedHolidays.add(d);
    }

    /*! Removes a date from the set of holidays for the given calendar. */
    public void removeHoliday(final Date d) {
        QL_REQUIRE(impl_ != null, "no calendar implementation provided");
        // if d was an artificially-added holiday, revert the change
        impl_.addedHolidays.remove(d);
        // if it's already a business day, leave the calendar alone.
        // Otherwise, add it.
        if (!impl_.isBusinessDay(d))
            impl_.removedHolidays.add(d);
    }

    /*! Returns the holidays between two dates. */
    public List<Date> holidayList(final Date from, final Date to, boolean includeWeekEnds) {
        QL_REQUIRE(TimeUtils.geq(to, from), "'from' date ("
                + from + ") must be equal to or earlier than 'to' date ("
                + to + ")");
        List<Date> result = new ArrayList<>();
        for (Date d = from.copy(); TimeUtils.leq(d, to); d = d.add(1)) {
            if (isHoliday(d) && (includeWeekEnds || !isWeekend(d.weekday())))
                result.add(d);
        }
        return result;
    }

    /*! Returns the business days between two dates. */
    public List<Date> businessDayList(final Date from, final Date to) {
        QL_REQUIRE(TimeUtils.geq(to, from), "'from' date ("
                + from + ") must be equal to or earlier than 'to' date ("
                + to + ")");
        List<Date> result = new ArrayList<>();
        for (Date d = from.copy(); TimeUtils.leq(d, to); d = d.add(1)) {
            if (isBusinessDay(d))
                result.add(d);
        }
        return result;
    }

    /*! Adjusts a non-business day to the appropriate near business day
        with respect to the given convention.
    */
    public Date adjust(final Date d, BusinessDayConvention c) {
        QL_REQUIRE(d != null, "null date");

        if (c == BusinessDayConvention.UNADJUSTED)
            return d;

        Date d1 = d.copy();
        if (c == BusinessDayConvention.FOLLOWING || c == BusinessDayConvention.MODIFIED_FOLLOWING
                || c == BusinessDayConvention.HALF_MONTH_MODIFIED_FOLLOWING) {
            while (isHoliday(d1))
                d1 = d1.add(1);
            if (c == BusinessDayConvention.MODIFIED_FOLLOWING
                    || c == BusinessDayConvention.HALF_MONTH_MODIFIED_FOLLOWING) {
                if (d1.month() != d.month()) {
                    return adjust(d, BusinessDayConvention.PRECEDING);
                }
                if (c == BusinessDayConvention.HALF_MONTH_MODIFIED_FOLLOWING) {
                    if (d.dayOfMonth() <= 15 && d1.dayOfMonth() > 15) {
                        return adjust(d, BusinessDayConvention.PRECEDING);
                    }
                }
            }
        } else if (c == BusinessDayConvention.PRECEDING || c == BusinessDayConvention.MODIFIED_PRECEDING) {
            while (isHoliday(d1))
                d1 = d1.substract(1);
            if (c == BusinessDayConvention.MODIFIED_PRECEDING && d1.month() != d.month()) {
                return adjust(d, BusinessDayConvention.FOLLOWING);
            }
        } else if (c == BusinessDayConvention.NEAREST) {
            Date d2 = d.copy();
            while (isHoliday(d1) && isHoliday(d2)) {
                d1 = d1.add(1);
                d2 = d2.substract(1);
            }
            if (isHoliday(d1))
                return d2;
            else
                return d1;
        } else {
            QL_FAIL("unknown business-day convention");
        }
        return d1;
    }

    /*! Advances the given date of the given number of business days and
        returns the result.
        \note The input date is not modified.
    */
    public Date advance(final Date d, int n, TimeUnit unit, BusinessDayConvention c, boolean endOfMonth) {
        QL_REQUIRE(d != null, "null date");
        if (n == 0) {
            return adjust(d, c);
        } else if (unit == TimeUnit.DAYS) {
            Date d1 = d.copy();
            if (n > 0) {
                while (n > 0) {
                    d1 = d1.add(1);
                    while (isHoliday(d1))
                        d1 = d1.add(1);
                    --n;
                }
            } else {
                while (n < 0) {
                    d1 = d1.substract(1);
                    while (isHoliday(d1))
                        d1 = d1.substract(1);
                    ++n;
                }
            }
            return d1;
        } else if (unit == TimeUnit.WEEKS) {
            Date d1 = d.add(TimeUtils.multiply(n, unit));
            return adjust(d1, c);
        } else {
            Date d1 = d.add(TimeUtils.multiply(n, unit));

            // we are sure the unit is Months or Years
            if (endOfMonth) {
                if (c == BusinessDayConvention.UNADJUSTED) {
                    // move to the last calendar day if d is the last calendar day
                    if (Date.isEndOfMonth(d)) return Date.endOfMonth(d1);
                } else {
                    // move to the last business day if d is the last business day
                    if (isEndOfMonth(d)) return endOfMonth(d1);
                }
            }
            return adjust(d1, c);
        }
    }

    /*! Advances the given date as specified by the given period and
        returns the result.
        \note The input date is not modified.
    */
    public Date advance(final Date d, final Period period, BusinessDayConvention c, boolean endOfMonth) {
        return advance(d, period.length(), period.units(), c, endOfMonth);
    }

    /*! Calculates the number of business days between two given
        dates and returns the result.
    */
    public int businessDaysBetween(final Date from,
                                   final Date to,
                                   boolean includeFirst, //true
                                   boolean includeLast //false
    ) {
        return (TimeUtils.less(from, to)) ? daysBetweenImpl(this, from, to, includeFirst, includeLast) :
                (TimeUtils.greater(from, to)) ? -daysBetweenImpl(this, to, from, includeLast, includeFirst) :
                        ((includeFirst && includeLast && isBusinessDay(from)) ? 1 : 0);
    }

    public int businessDaysBetween(final Date from,
                                   final Date to) {
        return businessDaysBetween(from, to, true, false);
    }

    @Override
    public String toString() {
        return name();
    }
}
