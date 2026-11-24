package jquant.instruments;

import jquant.cashflows.FixedRateLeg;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Fixed-coupon bond. Coupons accrue on the schedule dates and pay on the
 * adjusted date. The redemption is added on the last coupon payment date, so
 * the pillar and the latest relevant date are the same cash-flow date.
 *
 * <p>Cash flows are built with official {@link FixedRateLeg} using the same
 * builder order as {@code FixedRateBond} in official QuantLib: notionals,
 * coupon rates, first-period day counter, payment calendar, payment
 * adjustment and ex-coupon period. The leg already provides the irregular
 * reference dates that Actual/Actual (ISMA) stubs need.
 *
 * <p>An empty (or {@code null}) payment calendar is the official empty
 * {@code Calendar()} placeholder, not {@link jquant.time.calendars.NullCalendar}:
 * it resolves to the schedule calendar. An empty (or {@code null}) ex-coupon
 * calendar is likewise the official placeholder. {@code NullCalendar} is a
 * real no-holiday calendar and must be passed explicitly when that is the
 * intended ex-coupon convention.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/fixedratebond.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/fixedratebond.hpp
 */
public class FixedRateBond extends Bond {

    /** Official default empty {@code Calendar()}, used only as a placeholder. */
    private static final Calendar EMPTY_CALENDAR = new Calendar() {
    };

    protected Frequency frequency_;
    protected DayCounter dayCounter_;
    protected DayCounter firstPeriodDayCounter_;

    /** Official default issue date is the null date. */
    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter,
                paymentConvention, redemption, new Date());
    }

    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption,
                         Date issueDate) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter,
                paymentConvention, redemption, issueDate,
                EMPTY_CALENDAR, new Period(), EMPTY_CALENDAR);
    }

    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption,
                         Date issueDate,
                         Calendar paymentCalendar,
                         Period exCouponPeriod,
                         Calendar exCouponCalendar) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter,
                paymentConvention, redemption, issueDate, paymentCalendar,
                exCouponPeriod, exCouponCalendar,
                BusinessDayConvention.UNADJUSTED, false, new DayCounter());
    }

    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption,
                         Date issueDate,
                         Calendar paymentCalendar,
                         Period exCouponPeriod,
                         Calendar exCouponCalendar,
                         BusinessDayConvention exCouponConvention,
                         boolean exCouponEndOfMonth) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter,
                paymentConvention, redemption, issueDate, paymentCalendar,
                exCouponPeriod, exCouponCalendar, exCouponConvention,
                exCouponEndOfMonth, new DayCounter());
    }

    public FixedRateBond(int settlementDays,
                         double faceAmount,
                         Schedule schedule,
                         double[] coupons,
                         DayCounter accrualDayCounter,
                         BusinessDayConvention paymentConvention,
                         double redemption,
                         Date issueDate,
                         Calendar paymentCalendar,
                         Period exCouponPeriod,
                         Calendar exCouponCalendar,
                         BusinessDayConvention exCouponConvention,
                         boolean exCouponEndOfMonth,
                         DayCounter firstPeriodDayCounter) {
        super(settlementDays,
                paymentCalendar == null || paymentCalendar.empty()
                        ? schedule.calendar() : paymentCalendar,
                issueDate);
        QL_REQUIRE(coupons != null && coupons.length > 0, "no coupon rates given");

        frequency_ = schedule.hasTenor()
                ? schedule.tenor().frequency()
                : Frequency.NO_FREQUENCY;
        dayCounter_ = accrualDayCounter;
        firstPeriodDayCounter_ = firstPeriodDayCounter == null
                ? new DayCounter()
                : firstPeriodDayCounter;

        maturityDate_ = schedule.endDate().copy();

        if (exCouponPeriod == null)
            exCouponPeriod = new Period();
        if (exCouponCalendar == null || exCouponCalendar.empty())
            exCouponCalendar = EMPTY_CALENDAR;

        List<Double> rates = new ArrayList<>(coupons.length);
        for (double coupon : coupons)
            rates.add(coupon);

        cashflows_ = new ArrayList<>(new FixedRateLeg(schedule)
                .withNotionals(faceAmount)
                .withCouponRates(rates, accrualDayCounter)
                .withFirstPeriodDayCounter(firstPeriodDayCounter_)
                .withPaymentCalendar(calendar_)
                .withPaymentAdjustment(paymentConvention)
                .withExCouponPeriod(exCouponPeriod, exCouponCalendar,
                        exCouponConvention, exCouponEndOfMonth)
                .leg());

        addRedemptionsToCashflows(new double[] {redemption});

        QL_REQUIRE(!cashflows().isEmpty(), "bond with no cashflows!");
        QL_REQUIRE(redemptions_.size() == 1, "multiple redemptions created");
    }

    /** Official {@code frequency()}: the schedule tenor's frequency. */
    public Frequency frequency() {
        return frequency_;
    }

    /** Official {@code dayCounter()}: the coupon accrual day counter. */
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    /** Official {@code firstPeriodDayCounter()}. */
    public DayCounter firstPeriodDayCounter() {
        return firstPeriodDayCounter_;
    }
}
