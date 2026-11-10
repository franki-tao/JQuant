package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.BMAIndex;
import jquant.math.CommonUtil;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! helper class building a sequence of average BMA coupons
public class AverageBMALeg {
    private final Schedule schedule_;
    private final BMAIndex index_;
    private List<Double> notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.FOLLOWING;
    private List<Double> gearings_ = new ArrayList<>();
    private List<Double> spreads_ = new ArrayList<>();

    public AverageBMALeg(Schedule schedule, BMAIndex index) {
        schedule_ = schedule;
        index_ = index;
        paymentDayCounter_ = index.dayCounter();
    }

    public AverageBMALeg withNotionals(double notional) {
        notionals_ = List.of(notional);
        return this;
    }

    public AverageBMALeg withPaymentDayCounter(DayCounter dayCounter) {
        paymentDayCounter_ = dayCounter;
        return this;
    }

    public AverageBMALeg withPaymentAdjustment(BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public List<CashFlow> leg() {
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");
        Calendar calendar = schedule_.calendar();
        int n = schedule_.size() - 1;
        List<CashFlow> cashflows = new ArrayList<>();
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
            cashflows.add(new AverageBMACoupon(paymentDate,
                    CommonUtil.get(notionals_, i, notionals_.get(notionals_.size() - 1)),
                    start, end, index_,
                    CommonUtil.get(gearings_, i, 1.0),
                    CommonUtil.get(spreads_, i, 0.0),
                    refStart, refEnd,
                    paymentDayCounter_));
        }
        return cashflows;
    }
}
