package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.BMAIndex;
import jquant.indexes.InterestRateIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! coupon paying a calendar-day weighted average of BMA fixings
/*! QuantLib: ql/cashflows/averagebmacoupon.hpp */
public class AverageBMACoupon extends FloatingRateCoupon {
    private final Schedule fixingSchedule_;

    public AverageBMACoupon(Date paymentDate,
                            double nominal,
                            Date startDate,
                            Date endDate,
                            BMAIndex index,
                            double gearing,
                            double spread,
                            Date refPeriodStart,
                            Date refPeriodEnd,
                            DayCounter dayCounter) {
        super(paymentDate, nominal, startDate, endDate,
                index.fixingDays(), index, gearing, spread,
                refPeriodStart, refPeriodEnd, dayCounter, false, new Date());
        int cutoffDays = 0;
        Calendar cal = index.fixingCalendar();
        int fixingDays = index.fixingDays() + cutoffDays;
        Date fixingStart = cal.advance(startDate, -fixingDays, TimeUnit.DAYS,
                BusinessDayConvention.PRECEDING, false);
        fixingStart = previousValidFixingDate(fixingStart, index);
        while (TimeUtils.greater(index.valueDate(fixingStart), startDate)
                && TimeUtils.greater(fixingStart, Date.minDate()))
            fixingStart = previousValidFixingDate(fixingStart.add(-1), index);
        fixingSchedule_ = index.fixingSchedule(fixingStart, endDate);
        setPricer(new AverageBMACouponPricer());
    }

    @Override
    public Date fixingDate() {
        QL_FAIL("no single fixing date for average-BMA coupon");
        return new Date();
    }

    public List<Date> fixingDates() {
        return new ArrayList<>(fixingSchedule_.dates());
    }

    @Override
    public double indexFixing() {
        QL_FAIL("no single fixing for average-BMA coupon");
        return 0.0;
    }

    public List<Double> indexFixings() {
        List<Double> fixings = new ArrayList<>();
        for (int i = 0; i < fixingSchedule_.size(); i++)
            fixings.add(index_.fixing(fixingSchedule_.date(i), false));
        return fixings;
    }

    @Override
    public double convexityAdjustment() {
        QL_FAIL("not defined for average-BMA coupon");
        return 0.0;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return CashFlow.hasOccurred(date(), refDate, includeRefDate);
    }

    @Override
    public boolean tradingExCoupon(Date refDate) {
        return false;
    }

    private static Date previousValidFixingDate(Date date, BMAIndex index) {
        Date d = date;
        while (!index.isValidFixingDate(d) && TimeUtils.greater(d, Date.minDate()))
            d = d.add(-1);
        return d;
    }
}

class AverageBMACouponPricer extends FloatingRateCouponPricer {
    private AverageBMACoupon coupon_;

    @Override
    public void initialize(FloatingRateCoupon coupon) {
        QL_REQUIRE(coupon instanceof AverageBMACoupon, "wrong coupon type");
        coupon_ = (AverageBMACoupon) coupon;
    }

    @Override
    public double swapletRate() {
        List<Date> fixingDates = coupon_.fixingDates();
        InterestRateIndex index = coupon_.index();
        Date startDate = coupon_.accrualStartDate();
        Date endDate = coupon_.accrualEndDate();
        QL_REQUIRE(!fixingDates.isEmpty(), "fixing date list empty");
        QL_REQUIRE(TimeUtils.leq(index.valueDate(fixingDates.get(0)), startDate),
                "first fixing date valid after period start");
        QL_REQUIRE(TimeUtils.geq(index.valueDate(fixingDates.get(fixingDates.size() - 1)), endDate),
                "last fixing date valid before period end");

        double average = 0.0;
        int days = 0;
        Date d1 = startDate;
        for (int i = 0; i < fixingDates.size() - 1; i++) {
            Date valueDate = index.valueDate(fixingDates.get(i));
            Date nextValueDate = index.valueDate(fixingDates.get(i + 1));
            if (TimeUtils.geq(fixingDates.get(i), endDate) || TimeUtils.geq(valueDate, endDate))
                break;
            if (TimeUtils.less(fixingDates.get(i + 1), startDate) || TimeUtils.leq(nextValueDate, startDate))
                continue;
            Date d2 = TimeUtils.min(nextValueDate, endDate);
            int span = TimeUtils.substract(d2, d1);
            average += index.fixing(fixingDates.get(i), false) * span;
            days += span;
            d1 = d2;
        }
        int period = TimeUtils.substract(endDate, startDate);
        average /= period;
        QL_REQUIRE(days == period,
                "averaging days " + days + " differ from interest days " + period);
        return coupon_.gearing() * average + coupon_.spread();
    }

    @Override
    public double swapletPrice() {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double capletPrice(double effectiveCap) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double capletRate(double effectiveCap) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        QL_FAIL("not available");
        return 0.0;
    }
}
