package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.SwapSpreadIndex;
import jquant.math.CommonUtil;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUnit;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Helper class building a sequence of capped/floored CMS-spread-rate coupons.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/coupons/cmsspreadcoupon.{hpp,cpp}}. Official
 * {@code CmsSpreadLeg::operator Leg()} delegates to
 * {@code FloatingLeg<SwapSpreadIndex, CmsSpreadCoupon,
 * CappedFlooredCmsSpreadCoupon>}; the same loop is implemented here following
 * {@link CmsLeg}.
 */
public class CmsSpreadLeg {

    private final Schedule schedule_;
    private final SwapSpreadIndex swapSpreadIndex_;

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

    public CmsSpreadLeg(Schedule schedule, SwapSpreadIndex swapSpreadIndex) {
        QL_REQUIRE(swapSpreadIndex != null, "no index provided");
        schedule_ = schedule;
        swapSpreadIndex_ = swapSpreadIndex;
        // Like the Java CmsLeg, default the payment day counter to the index
        // day counter; an empty day counter would also fall back to it in
        // FloatingRateCoupon, but a fixed (zero-gearing) coupon would not.
        paymentDayCounter_ = swapSpreadIndex.dayCounter();
    }

    public CmsSpreadLeg withNotionals(double notional) {
        this.notionals_ = CommonUtil.ArrayInit(1, notional);
        return this;
    }

    public CmsSpreadLeg withNotionals(final List<Double> notionals) {
        this.notionals_ = notionals;
        return this;
    }

    public CmsSpreadLeg withPaymentDayCounter(final DayCounter dayCounter) {
        this.paymentDayCounter_ = dayCounter;
        return this;
    }

    public CmsSpreadLeg withPaymentAdjustment(BusinessDayConvention convention) {
        this.paymentAdjustment_ = convention;
        return this;
    }

    public CmsSpreadLeg withFixingDays(int fixingDays) {
        this.fixingDays_ = CommonUtil.ArrayInit(1, fixingDays);
        return this;
    }

    public CmsSpreadLeg withFixingDays(Integer fixingDays) {
        this.fixingDays_ = CommonUtil.ArrayInit(1, fixingDays);
        return this;
    }

    public CmsSpreadLeg withFixingDays(final List<Integer> fixingDays) {
        this.fixingDays_ = fixingDays;
        return this;
    }

    public CmsSpreadLeg withGearings(double gearing) {
        this.gearings_ = CommonUtil.ArrayInit(1, gearing);
        return this;
    }

    public CmsSpreadLeg withGearings(final List<Double> gearings) {
        this.gearings_ = gearings;
        return this;
    }

    public CmsSpreadLeg withSpreads(double spread) {
        this.spreads_ = CommonUtil.ArrayInit(1, spread);
        return this;
    }

    public CmsSpreadLeg withSpreads(final List<Double> spreads) {
        this.spreads_ = spreads;
        return this;
    }

    public CmsSpreadLeg withCaps(double cap) {
        this.caps_ = CommonUtil.ArrayInit(1, cap);
        return this;
    }

    public CmsSpreadLeg withCaps(final List<Double> caps) {
        this.caps_ = caps;
        return this;
    }

    public CmsSpreadLeg withFloors(double floor) {
        this.floors_ = CommonUtil.ArrayInit(1, floor);
        return this;
    }

    public CmsSpreadLeg withFloors(final List<Double> floors) {
        this.floors_ = floors;
        return this;
    }

    public CmsSpreadLeg inArrears(boolean flag) {
        this.inArrears_ = flag;
        return this;
    }

    public CmsSpreadLeg inArrears() {
        return inArrears(true);
    }

    public CmsSpreadLeg withZeroPayments(boolean flag) {
        this.zeroPayments_ = flag;
        return this;
    }

    public CmsSpreadLeg withZeroPayments() {
        return withZeroPayments(true);
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
        // schedule calendar, and CmsSpreadLeg exposes no payment calendar.
        final Calendar calendar = schedule_.calendar();
        final Calendar paymentCalendar = calendar;

        Date refStart, start, refEnd, end;
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
            // Official CmsSpreadLeg exposes no ex-coupon period; FloatingLeg
            // then uses an empty ex-coupon date.
            Date exCouponDate = new Date();

            if (CommonUtil.get(gearings_, i, 1.0) == 0.0) {
                // fixed coupon, as official FloatingLeg
                leg.add(new FixedRateCoupon(paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        CommonUtil.effectiveFixedRate(spreads_, caps_,
                                floors_, i),
                        paymentDayCounter_,
                        start, end, refStart, refEnd, exCouponDate));
            } else if (CommonUtil.noOption(caps_, floors_, i)) {
                leg.add(new CmsSpreadCoupon(
                        paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        start, end,
                        CommonUtil.get(fixingDays_, i,
                                swapSpreadIndex_.fixingDays()),
                        swapSpreadIndex_,
                        CommonUtil.get(gearings_, i, 1.0),
                        CommonUtil.get(spreads_, i, 0.0),
                        refStart, refEnd,
                        paymentDayCounter_, inArrears_, exCouponDate,
                        BusinessDayConvention.PRECEDING));
            } else {
                leg.add(new CappedFlooredCmsSpreadCoupon(
                        paymentDate,
                        CommonUtil.get(notionals_, i, 1.0),
                        start, end,
                        CommonUtil.get(fixingDays_, i,
                                swapSpreadIndex_.fixingDays()),
                        swapSpreadIndex_,
                        CommonUtil.get(gearings_, i, 1.0),
                        CommonUtil.get(spreads_, i, 0.0),
                        CommonUtil.get(caps_, i, Double.NaN),
                        CommonUtil.get(floors_, i, Double.NaN),
                        refStart, refEnd,
                        paymentDayCounter_, inArrears_, exCouponDate,
                        BusinessDayConvention.PRECEDING));
            }
        }
        return leg;
    }
}
