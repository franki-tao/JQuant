package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.IborIndex;
import jquant.math.CommonUtil;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! helper building a sequence of Ibor coupons
public class IborLeg {
    private final Schedule schedule_;
    private final IborIndex index_;
    private List<Double> notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_;
    private List<Integer> fixingDays_ = new ArrayList<>();
    private List<Double> gearings_ = new ArrayList<>();
    private List<Double> spreads_ = new ArrayList<>();

    public IborLeg(Schedule schedule, IborIndex index) {
        schedule_ = schedule;
        index_ = index;
        paymentAdjustment_ = schedule.businessDayConvention();
        paymentDayCounter_ = index.dayCounter();
    }

    public IborLeg withNotionals(double notional) {
        notionals_ = List.of(notional);
        return this;
    }

    public IborLeg withPaymentDayCounter(DayCounter dayCounter) {
        paymentDayCounter_ = dayCounter;
        return this;
    }

    public IborLeg withPaymentAdjustment(BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public IborLeg withFixingDays(int fixingDays) {
        fixingDays_ = List.of(fixingDays);
        return this;
    }

    public IborLeg withGearings(double gearing) {
        gearings_ = List.of(gearing);
        return this;
    }

    public IborLeg withSpreads(double spread) {
        spreads_ = List.of(spread);
        return this;
    }

    public List<CashFlow> leg() {
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");
        int n = schedule_.size() - 1;
        Calendar calendar = schedule_.calendar();
        List<CashFlow> cashflows = new ArrayList<>();
        PlainIborCouponPricer pricer = new PlainIborCouponPricer();
        for (int i = 0; i < n; i++) {
            Date start = schedule_.date(i);
            Date end = schedule_.date(i + 1);
            Date refStart = start;
            Date refEnd = end;
            Date paymentDate = calendar.adjust(end, paymentAdjustment_);
            if (i == 0 && schedule_.hasIsRegular() && schedule_.hasTenor() && !schedule_.isRegular(i + 1))
                refStart = calendar.adjust(end.substract(schedule_.tenor()), paymentAdjustment_);
            if (i == n - 1 && schedule_.hasIsRegular() && schedule_.hasTenor() && !schedule_.isRegular(i + 1))
                refEnd = calendar.adjust(start.add(schedule_.tenor()), paymentAdjustment_);
            IborCoupon coupon = new IborCoupon(paymentDate,
                    CommonUtil.get(notionals_, i, notionals_.get(notionals_.size() - 1)),
                    start, end,
                    CommonUtil.get(fixingDays_, i, index_.fixingDays()),
                    index_,
                    CommonUtil.get(gearings_, i, 1.0),
                    CommonUtil.get(spreads_, i, 0.0),
                    refStart, refEnd,
                    paymentDayCounter_, false, new Date());
            coupon.setPricer(pricer);
            cashflows.add(coupon);
        }
        return cashflows;
    }
}
