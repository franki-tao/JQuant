package jquant.instruments;

import jquant.cashflows.FixedRateLeg;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Fixed-coupon bond. Coupons accrue on the schedule dates and pay on the
 * adjusted date. The redemption is added on the last coupon payment date, so
 * the pillar and the latest relevant date are the same cash-flow date.
 *
 * <p>Cash flows are built with official {@link FixedRateLeg}, including the
 * irregular-period reference dates that Actual/Actual (ISMA) stubs need.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/fixedratebond.cpp
 */
public class FixedRateBond extends Bond {

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
        super(settlementDays, schedule.calendar(), issueDate);
        QL_REQUIRE(coupons != null && coupons.length > 0, "no coupon rates given");

        maturityDate_ = schedule.endDate().copy();

        List<Double> rates = new ArrayList<>(coupons.length);
        for (double coupon : coupons)
            rates.add(coupon);

        cashflows_ = new ArrayList<>(new FixedRateLeg(schedule)
                .withNotionals(faceAmount)
                .withCouponRates(rates, accrualDayCounter)
                .withPaymentCalendar(calendar_)
                .withPaymentAdjustment(paymentConvention)
                .leg());

        addRedemptionsToCashflows(new double[] {redemption});

        QL_REQUIRE(!cashflows().isEmpty(), "bond with no cashflows!");
        QL_REQUIRE(redemptions_.size() == 1, "multiple redemptions created");
    }
}
