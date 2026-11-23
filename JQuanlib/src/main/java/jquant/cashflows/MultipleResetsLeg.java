package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.IborIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.get;

//! helper class building a sequence of multiple-reset coupons
/*! Port of QuantLib's {@code MultipleResetsLeg} in
    {@code ql/cashflows/multipleresetscoupon.hpp} and
    {@code ql/cashflows/multipleresetscoupon.cpp}.
*/
public class MultipleResetsLeg {
    private final Schedule schedule_;
    private final IborIndex index_;
    private final int resetsPerCoupon_;
    private List<Double> notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_ = new DayCounter();
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.FOLLOWING;
    private int paymentLag_ = 0;
    private List<Integer> fixingDays_ = new ArrayList<>();
    private List<Double> gearings_ = new ArrayList<>();
    private List<Double> couponSpreads_ = new ArrayList<>();
    private List<Double> rateSpreads_ = new ArrayList<>();
    private RateAveraging averagingMethod_ = RateAveraging.Compound;
    private Period exCouponPeriod_ = new Period();
    // Official default is an empty Calendar(); Java Calendar is abstract, so null means empty().
    private Calendar exCouponCalendar_;
    private BusinessDayConvention exCouponAdjustment_ = BusinessDayConvention.UNADJUSTED;
    private boolean exCouponEndOfMonth_ = false;

    /*! \param fullResetSchedule the full schedule specifying reset periods for all coupons.
        \param index             the index whose fixings will be used; it should have the
                                 same tenor as the resets.
        \param resetsPerCoupon   the number of resets for each coupon; the number of periods
                                 in the schedule should be divided exactly by this number.
    */
    public MultipleResetsLeg(final Schedule fullResetSchedule,
                             final IborIndex index,
                             int resetsPerCoupon) {
        schedule_ = fullResetSchedule;
        index_ = index;
        resetsPerCoupon_ = resetsPerCoupon;
        paymentCalendar_ = schedule_.calendar();

        QL_REQUIRE(index_ != null, "no index provided");
        QL_REQUIRE(!schedule_.empty(), "empty schedule provided");
        QL_REQUIRE((schedule_.size() - 1) % resetsPerCoupon_ == 0,
                "number of resets per coupon does not divide exactly number of periods in schedule");
    }

    public MultipleResetsLeg withNotionals(double notional) {
        notionals_ = new ArrayList<>();
        notionals_.add(notional);
        return this;
    }

    public MultipleResetsLeg withNotionals(final List<Double> notionals) {
        notionals_ = notionals;
        return this;
    }

    public MultipleResetsLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public MultipleResetsLeg withPaymentAdjustment(BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public MultipleResetsLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public MultipleResetsLeg withPaymentLag(int lag) {
        paymentLag_ = lag;
        return this;
    }

    public MultipleResetsLeg withFixingDays(int fixingDays) {
        fixingDays_ = new ArrayList<>();
        fixingDays_.add(fixingDays);
        return this;
    }

    public MultipleResetsLeg withFixingDays(final List<Integer> fixingDays) {
        fixingDays_ = fixingDays;
        return this;
    }

    public MultipleResetsLeg withGearings(double gearing) {
        gearings_ = new ArrayList<>();
        gearings_.add(gearing);
        return this;
    }

    public MultipleResetsLeg withGearings(final List<Double> gearings) {
        gearings_ = gearings;
        return this;
    }

    public MultipleResetsLeg withCouponSpreads(double spread) {
        couponSpreads_ = new ArrayList<>();
        couponSpreads_.add(spread);
        return this;
    }

    public MultipleResetsLeg withCouponSpreads(final List<Double> spreads) {
        couponSpreads_ = spreads;
        return this;
    }

    public MultipleResetsLeg withRateSpreads(double spread) {
        rateSpreads_ = new ArrayList<>();
        rateSpreads_.add(spread);
        return this;
    }

    public MultipleResetsLeg withRateSpreads(final List<Double> spreads) {
        rateSpreads_ = spreads;
        return this;
    }

    public MultipleResetsLeg withAveragingMethod(RateAveraging averagingMethod) {
        averagingMethod_ = averagingMethod;
        return this;
    }

    public MultipleResetsLeg withExCouponPeriod(final Period period,
                                                final Calendar cal,
                                                BusinessDayConvention convention,
                                                boolean endOfMonth) {
        exCouponPeriod_ = period;
        exCouponCalendar_ = cal;
        exCouponAdjustment_ = convention;
        exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    // endOfMonth = false
    public MultipleResetsLeg withExCouponPeriod(final Period period,
                                                final Calendar cal,
                                                BusinessDayConvention convention) {
        return withExCouponPeriod(period, cal, convention, false);
    }

    public List<CashFlow> leg() {
        List<CashFlow> cashflows = new ArrayList<>();
        Calendar calendar = schedule_.calendar();

        int n = (schedule_.size() - 1) / resetsPerCoupon_;
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");
        QL_REQUIRE(notionals_.size() <= n,
                "too many nominals (" + notionals_.size() + "), only " + n + " required");
        QL_REQUIRE(gearings_.size() <= n,
                "too many gearings (" + gearings_.size() + "), only " + n + " required");
        QL_REQUIRE(couponSpreads_.size() <= n,
                "too many coupon spreads (" + couponSpreads_.size() + "), only " + n + " required");
        QL_REQUIRE(rateSpreads_.size() <= n,
                "too many rate spreads (" + rateSpreads_.size() + "), only " + n + " required");
        QL_REQUIRE(fixingDays_.size() <= n,
                "too many fixing days (" + fixingDays_.size() + "), only " + n + " required");

        for (int i = 0; i < n; ++i) {
            Date start = schedule_.date(i * resetsPerCoupon_);
            Date end = schedule_.date((i + 1) * resetsPerCoupon_);
            Schedule subSchedule = schedule_.after(start).until(end);
            Date paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.DAYS,
                    paymentAdjustment_, false);
            Date exCouponDate = new Date();
            if (TimeUtils.neq(exCouponPeriod_, new Period())) {
                if (exCouponCalendar_ == null || exCouponCalendar_.empty()) {
                    exCouponDate = calendar.advance(paymentDate,
                            TimeUtils.multiply(-1, exCouponPeriod_),
                            exCouponAdjustment_, exCouponEndOfMonth_);
                } else {
                    exCouponDate = exCouponCalendar_.advance(paymentDate,
                            TimeUtils.multiply(-1, exCouponPeriod_),
                            exCouponAdjustment_, exCouponEndOfMonth_);
                }
            }

            cashflows.add(new MultipleResetsCoupon(
                    paymentDate,
                    get(notionals_, i, notionals_.get(notionals_.size() - 1)),
                    subSchedule,
                    get(fixingDays_, i, index_.fixingDays()),
                    index_,
                    get(gearings_, i, 1.0),
                    get(couponSpreads_, i, 0.0),
                    get(rateSpreads_, i, 0.0),
                    start, end, paymentDayCounter_,
                    exCouponDate));
        }

        switch (averagingMethod_) {
            case Simple:
                setCouponPricer(cashflows, new AveragingMultipleResetsPricer());
                break;
            case Compound:
                setCouponPricer(cashflows, new CompoundingMultipleResetsPricer());
                break;
            default:
                QL_FAIL("unknown compounding convention (" + averagingMethod_.ordinal() + ")");
        }
        return cashflows;
    }
}
