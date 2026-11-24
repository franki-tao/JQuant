package jquant.instruments;

import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Floating-rate bond paying an Ibor index, possibly capped and/or floored.
 *
 * <p>The cash flows are built with the official {@link IborLeg} using the same
 * builder calls as {@code FloatingRateBond} in official QuantLib: notionals,
 * payment day counter, payment adjustment, fixing days, gearings, spreads,
 * caps, floors, in-arrears flag, ex-coupon period and fixing convention. The
 * redemption is then appended by {@link Bond#addRedemptionsToCashflows}.
 *
 * <p>An {@code Integer} {@code null} fixing days value means the official
 * {@code Null<Natural>()}: the leg then uses the index default. Likewise an
 * empty (or {@code null}) ex-coupon calendar means the official empty
 * {@code Calendar()}, which the leg resolves to the schedule calendar.
 *
 * <p>The bond registers with the Ibor index, as official does, so that fixing
 * notifications and forecast-curve relinks reach the bond (and its observers).
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/floatingratebond.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/floatingratebond.hpp
 */
public class FloatingRateBond extends Bond {

    /** Official default empty {@code Calendar()}, used only as a placeholder. */
    private static final Calendar EMPTY_CALENDAR = new Calendar() {
    };

    /** Official defaults: Following payment, index fixing days, at par. */
    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                BusinessDayConvention.FOLLOWING);
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                paymentConvention, null);
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention,
                            Integer fixingDays) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                paymentConvention, fixingDays,
                List.of(), List.of(), List.of(), List.of(),
                false, 100.0, new Date());
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention,
                            Integer fixingDays,
                            List<Double> gearings,
                            List<Double> spreads,
                            List<Double> caps,
                            List<Double> floors,
                            boolean inArrears,
                            double redemption,
                            Date issueDate) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                paymentConvention, fixingDays, gearings, spreads, caps, floors,
                inArrears, redemption, issueDate, new Period());
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention,
                            Integer fixingDays,
                            List<Double> gearings,
                            List<Double> spreads,
                            List<Double> caps,
                            List<Double> floors,
                            boolean inArrears,
                            double redemption,
                            Date issueDate,
                            Period exCouponPeriod) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                paymentConvention, fixingDays, gearings, spreads, caps, floors,
                inArrears, redemption, issueDate, exCouponPeriod,
                EMPTY_CALENDAR, BusinessDayConvention.UNADJUSTED, false);
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention,
                            Integer fixingDays,
                            List<Double> gearings,
                            List<Double> spreads,
                            List<Double> caps,
                            List<Double> floors,
                            boolean inArrears,
                            double redemption,
                            Date issueDate,
                            Period exCouponPeriod,
                            Calendar exCouponCalendar,
                            BusinessDayConvention exCouponConvention,
                            boolean exCouponEndOfMonth) {
        this(settlementDays, faceAmount, schedule, iborIndex, paymentDayCounter,
                paymentConvention, fixingDays, gearings, spreads, caps, floors,
                inArrears, redemption, issueDate, exCouponPeriod, exCouponCalendar,
                exCouponConvention, exCouponEndOfMonth, BusinessDayConvention.PRECEDING);
    }

    public FloatingRateBond(int settlementDays,
                            double faceAmount,
                            Schedule schedule,
                            IborIndex iborIndex,
                            DayCounter paymentDayCounter,
                            BusinessDayConvention paymentConvention,
                            Integer fixingDays,
                            List<Double> gearings,
                            List<Double> spreads,
                            List<Double> caps,
                            List<Double> floors,
                            boolean inArrears,
                            double redemption,
                            Date issueDate,
                            Period exCouponPeriod,
                            Calendar exCouponCalendar,
                            BusinessDayConvention exCouponConvention,
                            boolean exCouponEndOfMonth,
                            BusinessDayConvention fixingConvention) {
        super(settlementDays, schedule.calendar(), issueDate);

        maturityDate_ = schedule.endDate().copy();

        if (exCouponPeriod == null)
            exCouponPeriod = new Period();
        if (exCouponCalendar == null || exCouponCalendar.empty())
            exCouponCalendar = EMPTY_CALENDAR;

        cashflows_ = new ArrayList<>(new IborLeg(schedule, iborIndex)
                .withNotionals(faceAmount)
                .withPaymentDayCounter(paymentDayCounter)
                .withPaymentAdjustment(paymentConvention)
                .withFixingDays(fixingDays)
                .withGearings(gearings)
                .withSpreads(spreads)
                .withCaps(caps)
                .withFloors(floors)
                .inArrears(inArrears)
                .withExCouponPeriod(exCouponPeriod, exCouponCalendar,
                        exCouponConvention, exCouponEndOfMonth)
                .withFixingConvention(fixingConvention)
                .leg());

        addRedemptionsToCashflows(new double[] {redemption});

        QL_REQUIRE(!cashflows().isEmpty(), "bond with no cashflows!");
        QL_REQUIRE(redemptions_.size() == 1, "multiple redemptions created");

        registerWith(iborIndex);
    }
}
