package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.SwapIndex;
import jquant.math.CommonUtil;
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

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Helper class building a sequence of capped/floored CMS-rate coupons.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/cmscoupon.{hpp,cpp}}.
 * Official {@code CmsLeg::operator Leg()} delegates to
 * {@code FloatingLeg<SwapIndex, CmsCoupon, CappedFlooredCmsCoupon>}. The Java
 * {@link CashflowUtil#FloatingLeg} is hard-coded to the Ibor coupon types, so
 * the same loop is implemented here for CMS without changing the Ibor path.
 */
public class CmsLeg {

    private final Schedule schedule_;
    private final SwapIndex swapIndex_;

    private List<Double> notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_ =
            BusinessDayConvention.FOLLOWING;
    private List<Integer> fixingDays_ = new ArrayList<>();
    private List<Double> gearings_ = new ArrayList<>();
    private List<Double> spreads_ = new ArrayList<>();
    private List<Double> caps_ = new ArrayList<>();
    private List<Double> floors_ = new ArrayList<>();
    private boolean inArrears_ = false;
    private boolean zeroPayments_ = false;
    private BusinessDayConvention fixingConvention_ =
            BusinessDayConvention.PRECEDING;
    private Period exCouponPeriod_ = new Period();
    private Calendar exCouponCalendar_;
    private BusinessDayConvention exCouponAdjustment_ =
            BusinessDayConvention.FOLLOWING;
    private boolean exCouponEndOfMonth_ = false;

    public CmsLeg(Schedule schedule, SwapIndex swapIndex) {
        QL_REQUIRE(swapIndex != null, "no index provided");
        schedule_ = schedule;
        swapIndex_ = swapIndex;
        // Like the Java IborLeg, default the payment day counter to the
        // index day counter; an empty day counter would also fall back to it
        // in FloatingRateCoupon, but a fixed (zero-gearing) coupon would not.
        paymentDayCounter_ = swapIndex.dayCounter();
        exCouponCalendar_ = schedule.calendar();
    }

    public CmsLeg withNotionals(double notional) {
        this.notionals_ = CommonUtil.ArrayInit(1, notional);
        return this;
    }

    public CmsLeg withNotionals(final List<Double> notionals) {
        this.notionals_ = notionals;
        return this;
    }

    public CmsLeg withPaymentDayCounter(final DayCounter dayCounter) {
        this.paymentDayCounter_ = dayCounter;
        return this;
    }

    public CmsLeg withPaymentAdjustment(BusinessDayConvention convention) {
        this.paymentAdjustment_ = convention;
        return this;
    }

    public CmsLeg withFixingDays(int fixingDays) {
        this.fixingDays_ = CommonUtil.ArrayInit(1, fixingDays);
        return this;
    }

    public CmsLeg withFixingDays(Integer fixingDays) {
        this.fixingDays_ = CommonUtil.ArrayInit(1, fixingDays);
        return this;
    }

    public CmsLeg withFixingDays(final List<Integer> fixingDays) {
        this.fixingDays_ = fixingDays;
        return this;
    }

    public CmsLeg withGearings(double gearing) {
        this.gearings_ = CommonUtil.ArrayInit(1, gearing);
        return this;
    }

    public CmsLeg withGearings(final List<Double> gearings) {
        this.gearings_ = gearings;
        return this;
    }

    public CmsLeg withSpreads(double spread) {
        this.spreads_ = CommonUtil.ArrayInit(1, spread);
        return this;
    }

    public CmsLeg withSpreads(final List<Double> spreads) {
        this.spreads_ = spreads;
        return this;
    }

    public CmsLeg withCaps(double cap) {
        this.caps_ = CommonUtil.ArrayInit(1, cap);
        return this;
    }

    public CmsLeg withCaps(final List<Double> caps) {
        this.caps_ = caps;
        return this;
    }

    public CmsLeg withFloors(double floor) {
        this.floors_ = CommonUtil.ArrayInit(1, floor);
        return this;
    }

    public CmsLeg withFloors(final List<Double> floors) {
        this.floors_ = floors;
        return this;
    }

    public CmsLeg inArrears(boolean flag) {
        this.inArrears_ = flag;
        return this;
    }

    public CmsLeg inArrears() {
        return inArrears(true);
    }

    public CmsLeg withZeroPayments(boolean flag) {
        this.zeroPayments_ = flag;
        return this;
    }

    public CmsLeg withZeroPayments() {
        return withZeroPayments(true);
    }

    public CmsLeg withFixingConvention(BusinessDayConvention convention) {
        this.fixingConvention_ = convention;
        return this;
    }

    public CmsLeg withExCouponPeriod(final Period period,
                                     final Calendar cal,
                                     BusinessDayConvention convention,
                                     boolean endOfMonth) {
        exCouponPeriod_ = period;
        exCouponCalendar_ = cal;
        exCouponAdjustment_ = convention;
        exCouponEndOfMonth_ = endOfMonth;
        return this;
    }

    public List<CashFlow> leg() {
        int n = schedule_.size() - 1;
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");
        QL_REQUIRE(notionals_.size() <= n,
                "too many nominals (" + notionals_.size()
                        + "), only " + n + " required");
        QL_REQUIRE(gearings_.size() <= n,
                "too many gearings (" + gearings_.size()
                        + "), only " + n + " required");
        QL_REQUIRE(spreads_.size() <= n,
                "too many spreads (" + spreads_.size()
                        + "), only " + n + " required");
        QL_REQUIRE(caps_.size() <= n,
                "too many caps (" + caps_.size()
                        + "), only " + n + " required");
        QL_REQUIRE(floors_.size() <= n,
                "too many floors (" + floors_.size()
                        + "), only " + n + " required");
        QL_REQUIRE(!zeroPayments_ || !inArrears_,
                "in-arrears and zero features are not compatible");

        List<CashFlow> leg = new ArrayList<>(n);

        // As official FloatingLeg: an empty payment calendar means the
        // schedule calendar, and CmsLeg exposes no payment calendar.
        final Calendar calendar = schedule_.calendar();
        final Calendar paymentCalendar = calendar;
        final Calendar exCouponCalendar =
                exCouponCalendar_.empty() ? calendar : exCouponCalendar_;

        Date refStart, start, refEnd, end;
        Date exCouponDate;
        Date lastPaymentDate = paymentCalendar.advance(
                schedule_.date(n), 0, TimeUnit.DAYS, paymentAdjustment_,
                false);

        for (int i = 0; i < n; ++i) {
            refStart = start = schedule_.date(i);
            refEnd = end = schedule_.date(i + 1);
            Date paymentDate = zeroPayments_
                    ? lastPaymentDate
                    : paymentCalendar.advance(end, 0, TimeUnit.DAYS,
                            paymentAdjustment_, false);
            if (i == 0 && schedule_.hasIsRegular() && schedule_.hasTenor()
                    && !schedule_.isRegular(i + 1)) {
                BusinessDayConvention bdc =
                        schedule_.businessDayConvention();
                refStart = calendar.adjust(
                        end.substract(schedule_.tenor()), bdc);
            }
            if (i == n - 1 && schedule_.hasIsRegular() && schedule_.hasTenor()
                    && !schedule_.isRegular(i + 1)) {
                BusinessDayConvention bdc =
                        schedule_.businessDayConvention();
                refEnd = calendar.adjust(
                        start.add(schedule_.tenor()), bdc);
            }
            exCouponDate = new Date();
            if (TimeUtils.neq(exCouponPeriod_, new Period())) {
                exCouponDate = exCouponCalendar.advance(paymentDate,
                        TimeUtils.multiply(-1, exCouponPeriod_),
                        exCouponAdjustment_, exCouponEndOfMonth_);
            }

            if (CommonUtil.get(gearings_, i, 1.0) == 0.0) {
                // fixed coupon, as official FloatingLeg
                leg.add(new FixedRateCoupon(paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        CommonUtil.effectiveFixedRate(spreads_, caps_,
                                floors_, i),
                        paymentDayCounter_,
                        start, end, refStart, refEnd, exCouponDate));
            } else if (CommonUtil.noOption(caps_, floors_, i)) {
                leg.add(new CmsCoupon(
                        paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        start, end,
                        CommonUtil.get(fixingDays_, i,
                                swapIndex_.fixingDays()),
                        swapIndex_,
                        CommonUtil.get(gearings_, i, 1.0),
                        CommonUtil.get(spreads_, i, 0.0),
                        refStart, refEnd,
                        paymentDayCounter_, inArrears_, exCouponDate,
                        fixingConvention_));
            } else {
                leg.add(new CappedFlooredCmsCoupon(
                        paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        start, end,
                        CommonUtil.get(fixingDays_, i,
                                swapIndex_.fixingDays()),
                        swapIndex_,
                        CommonUtil.get(gearings_, i, 1.0),
                        CommonUtil.get(spreads_, i, 0.0),
                        CommonUtil.get(caps_, i, Double.NaN),
                        CommonUtil.get(floors_, i, Double.NaN),
                        refStart, refEnd,
                        paymentDayCounter_, inArrears_, exCouponDate,
                        fixingConvention_));
            }
        }
        return leg;
    }
}
