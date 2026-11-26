package jquant.instruments;

import jquant.cashflows.CmsLeg;
import jquant.indexes.SwapIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * CMS-rate bond paying a swap index, possibly geared, spread and/or capped
 * and floored.
 *
 * <p>The cash flows are built with the official {@link CmsLeg} using the same
 * builder calls as {@code CmsRateBond} in official QuantLib: notionals,
 * payment day counter, payment adjustment, fixing days, gearings, spreads,
 * caps, floors and the in-arrears flag. The redemption is then appended by
 * {@link Bond#addRedemptionsToCashflows}.
 *
 * <p>An {@code Integer} {@code null} fixing days value means the official
 * {@code Null<Natural>()}: the leg then uses the index default.
 *
 * <p>The bond registers with the swap index, as official does, so that fixing
 * notifications reach the bond.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/cmsratebond.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/cmsratebond.hpp
 */
public class CmsRateBond extends Bond {

    /** Official defaults: Following payment, index fixing days, at par. */
    public CmsRateBond(int settlementDays,
                       double faceAmount,
                       Schedule schedule,
                       SwapIndex index,
                       DayCounter paymentDayCounter) {
        this(settlementDays, faceAmount, schedule, index, paymentDayCounter,
                BusinessDayConvention.FOLLOWING);
    }

    public CmsRateBond(int settlementDays,
                       double faceAmount,
                       Schedule schedule,
                       SwapIndex index,
                       DayCounter paymentDayCounter,
                       BusinessDayConvention paymentConvention) {
        this(settlementDays, faceAmount, schedule, index, paymentDayCounter,
                paymentConvention, null);
    }

    public CmsRateBond(int settlementDays,
                       double faceAmount,
                       Schedule schedule,
                       SwapIndex index,
                       DayCounter paymentDayCounter,
                       BusinessDayConvention paymentConvention,
                       Integer fixingDays) {
        this(settlementDays, faceAmount, schedule, index, paymentDayCounter,
                paymentConvention, fixingDays,
                List.of(1.0), List.of(0.0), List.of(), List.of(),
                false, 100.0, new Date());
    }

    public CmsRateBond(int settlementDays,
                       double faceAmount,
                       Schedule schedule,
                       SwapIndex index,
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
        super(settlementDays, schedule.calendar(),
                issueDate == null ? new Date() : issueDate.copy());

        maturityDate_ = schedule.endDate();

        cashflows_ = new ArrayList<>(new CmsLeg(schedule, index)
                .withNotionals(faceAmount)
                .withPaymentDayCounter(paymentDayCounter)
                .withPaymentAdjustment(paymentConvention)
                .withFixingDays(fixingDays)
                .withGearings(gearings)
                .withSpreads(spreads)
                .withCaps(caps)
                .withFloors(floors)
                .inArrears(inArrears)
                .leg());

        addRedemptionsToCashflows(new double[] {redemption});

        QL_REQUIRE(!cashflows().isEmpty(), "bond with no cashflows!");
        QL_REQUIRE(redemptions_.size() == 1, "multiple redemptions created");

        registerWith(index);
    }
}
