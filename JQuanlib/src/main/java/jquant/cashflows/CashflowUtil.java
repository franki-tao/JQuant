package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.IborIndex;
import jquant.math.CommonUtil;
import jquant.time.*;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class CashflowUtil {
    public static List<CashFlow> FloatingLeg(
            final Schedule schedule,
            final List<Double> nominals,
            final IborIndex index,
            final DayCounter paymentDayCounter,
            BusinessDayConvention paymentAdj,
            final List<Integer> fixingDays,
            final List<Double> gearings,
            final List<Double> spreads,
            final List<Double> caps,
            final List<Double> floors,
            boolean isInArrears,
            boolean isZero,
            Integer paymentLag, // = 0,
            Calendar paymentCalendar, // = Calendar(),
            Period exCouponPeriod, // = Period(),
            Calendar exCouponCalendar, // = Calendar(),
            BusinessDayConvention exCouponAdjustment, // = Unadjusted,
            boolean exCouponEndOfMonth //= false
    ) {
        int n = schedule.size() - 1;
        QL_REQUIRE(!nominals.isEmpty(), "no notional given");
        QL_REQUIRE(nominals.size() <= n,
                "too many nominals (" + nominals.size() +
                        "), only " + n + " required");
        QL_REQUIRE(gearings.size() <= n,
                "too many gearings (" + gearings.size() +
                        "), only " + n + " required");
        QL_REQUIRE(spreads.size() <= n,
                "too many spreads (" + spreads.size() +
                        "), only " + n + " required");
        QL_REQUIRE(caps.size() <= n,
                "too many caps (" + caps.size() +
                        "), only " + n + " required");
        QL_REQUIRE(floors.size() <= n,
                "too many floors (" + floors.size() +
                        "), only " + n + " required");
        QL_REQUIRE(!isZero || !isInArrears,
                "in-arrears and zero features are not compatible");

        List<CashFlow> leg = CommonUtil.ArrayInit(n);

        // the following is not always correct
        final Calendar calendar = schedule.calendar();

        if (paymentCalendar.empty()) {
            paymentCalendar = calendar;
        }
        Date refStart, start, refEnd, end;
        Date exCouponDate = new Date();
        Date lastPaymentDate = paymentCalendar.advance(schedule.date(n), paymentLag, TimeUnit.DAYS, paymentAdj, false);

        for (int i = 0; i < n; ++i) {
            refStart = start = schedule.date(i);
            refEnd = end = schedule.date(i + 1);
            Date paymentDate =
                    isZero ? lastPaymentDate : paymentCalendar.advance(end, paymentLag, TimeUnit.DAYS, paymentAdj, false);
            if (i == 0 && (schedule.hasIsRegular() && schedule.hasTenor() && !schedule.isRegular(i + 1))) {
                BusinessDayConvention bdc = schedule.businessDayConvention();
                refStart = calendar.adjust(end.substract(schedule.tenor()), bdc);
            }
            if (i == n - 1 && (schedule.hasIsRegular() && schedule.hasTenor() && !schedule.isRegular(i + 1))) {
                BusinessDayConvention bdc = schedule.businessDayConvention();
                refEnd = calendar.adjust(start.add(schedule.tenor()), bdc);
            }
            if (TimeUtils.neq(exCouponPeriod, new Period())) {
                if (exCouponCalendar.empty()) {
                    exCouponCalendar = calendar;
                }
                exCouponDate = exCouponCalendar.advance(paymentDate, TimeUtils.multiply(-1, exCouponPeriod),
                        exCouponAdjustment, exCouponEndOfMonth);
            }
            if (CommonUtil.get(gearings, i, 1.0) == 0.0) { // fixed coupon
                leg.add(new FixedRateCoupon(paymentDate,
                        CommonUtil.get(nominals, i, 1.0),
                        CommonUtil.effectiveFixedRate(spreads, caps,
                                floors, i),
                        paymentDayCounter,
                        start, end, refStart, refEnd,
                        exCouponDate));
            } else { // floating coupon
                if (CommonUtil.noOption(caps, floors, i))
                    leg.add((new IborCoupon(
                            paymentDate,
                            CommonUtil.get(nominals, i, 1.0),
                            start, end,
                            CommonUtil.get(fixingDays, i, index.fixingDays()),
                            index,
                            CommonUtil.get(gearings, i, 1.0),
                            CommonUtil.get(spreads, i, 0.0),
                            refStart, refEnd,
                            paymentDayCounter, isInArrears, exCouponDate)));
                else {
                    leg.add((new
                            CappedFlooredIborCoupon(
                            paymentDate,
                            CommonUtil.get(nominals, i, 1.0),
                            start, end,
                            CommonUtil.get(fixingDays, i, index.fixingDays()),
                            index,
                            CommonUtil.get(gearings, i, 1.0),
                            CommonUtil.get(spreads, i, 0.0),
                            CommonUtil.get(caps, i, Double.NaN),
                            CommonUtil.get(floors, i, Double.NaN),
                            refStart, refEnd,
                            paymentDayCounter,
                            isInArrears, exCouponDate)));
                }
            }
        }
        return leg;
    }

    public static void setCouponPricer(final List<CashFlow> leg, final FloatingRateCouponPricer pricer) {
        // PricerSetter setter(pricer);
        for (CashFlow i : leg) {
            i.accept(pricer);
        }
    }
}
