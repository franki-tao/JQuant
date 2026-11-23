package jquant.cashflows;

import jquant.CashFlow;
import jquant.indexes.OvernightIndex;
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
 * Helper class building a sequence of overnight-indexed coupons.
 *
 * <p>Port of QuantLib's {@code OvernightLeg} in
 * {@code ql/cashflows/overnightindexedcoupon.cpp}. Only the subset used by
 * the basis-swap helpers is provided: lookback, lockout, observation shift,
 * caps/floors, explicit payment dates and in-advance fixing are omitted.
 * The default in-arrears behaviour is the only one supported.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcoupon.cpp
 */
public class OvernightLeg {
    private final Schedule schedule_;
    private final OvernightIndex overnightIndex_;
    private final List<Double> notionals_ = new ArrayList<>();
    private DayCounter paymentDayCounter_ = new DayCounter();
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.FOLLOWING;
    private int paymentLag_ = 0;
    private List<Double> gearings_ = new ArrayList<>();
    private List<Double> spreads_ = new ArrayList<>();
    private boolean telescopicValueDates_ = false;
    private RateAveraging averagingMethod_ = RateAveraging.Compound;
    private Integer lookbackDays_ = null;
    private int lockoutDays_ = 0;
    private boolean applyObservationShift_ = false;

    public OvernightLeg(final Schedule schedule, final OvernightIndex overnightIndex) {
        schedule_ = schedule;
        overnightIndex_ = overnightIndex;
        paymentCalendar_ = schedule.calendar();
        QL_REQUIRE(overnightIndex_ != null, "no index provided");
    }

    public OvernightLeg withNotionals(double notional) {
        notionals_.clear();
        notionals_.add(notional);
        return this;
    }

    public OvernightLeg withNotionals(final List<Double> notionals) {
        notionals_.clear();
        notionals_.addAll(notionals);
        return this;
    }

    public OvernightLeg withPaymentDayCounter(final DayCounter dc) {
        paymentDayCounter_ = dc;
        return this;
    }

    public OvernightLeg withPaymentAdjustment(final BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public OvernightLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public OvernightLeg withPaymentLag(int lag) {
        paymentLag_ = lag;
        return this;
    }

    public OvernightLeg withGearings(double gearing) {
        gearings_.clear();
        gearings_.add(gearing);
        return this;
    }

    public OvernightLeg withGearings(final List<Double> gearings) {
        gearings_.clear();
        gearings_.addAll(gearings);
        return this;
    }

    public OvernightLeg withSpreads(double spread) {
        spreads_.clear();
        spreads_.add(spread);
        return this;
    }

    public OvernightLeg withSpreads(final List<Double> spreads) {
        spreads_.clear();
        spreads_.addAll(spreads);
        return this;
    }

    public OvernightLeg withTelescopicValueDates(boolean telescopicValueDates) {
        telescopicValueDates_ = telescopicValueDates;
        return this;
    }

    public OvernightLeg withAveragingMethod(final RateAveraging averagingMethod) {
        averagingMethod_ = averagingMethod;
        return this;
    }

    public OvernightLeg withLookbackDays(Integer lookbackDays) {
        lookbackDays_ = lookbackDays;
        return this;
    }

    public OvernightLeg withLockoutDays(int lockoutDays) {
        lockoutDays_ = lockoutDays;
        return this;
    }

    public OvernightLeg withObservationShift(boolean applyObservationShift) {
        applyObservationShift_ = applyObservationShift;
        return this;
    }

    private static double get(final List<Double> v, int i, double defaultValue) {
        return v.isEmpty() ? defaultValue : v.get(Math.min(i, v.size() - 1));
    }

    public List<CashFlow> build() {
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");

        final List<CashFlow> cashflows = new ArrayList<>();

        Calendar calendar = schedule_.calendar();
        Calendar paymentCalendar = paymentCalendar_;

        QL_REQUIRE(!calendar.empty() || !paymentCalendar.empty(),
                "no calendar provided");
        if (calendar.empty())
            calendar = paymentCalendar;
        if (paymentCalendar.empty())
            paymentCalendar = calendar;

        final int n = schedule_.size() - 1;

        for (int i = 0; i < n; ++i) {
            Date refStart = schedule_.date(i);
            Date start = refStart;
            Date refEnd = schedule_.date(i + 1);
            Date end = refEnd;

            final Date paymentDate = paymentCalendar.advance(end, paymentLag_,
                    TimeUnit.DAYS, paymentAdjustment_, false);

            // determine refStart and refEnd
            if (i == 0 && schedule_.hasIsRegular() && !schedule_.isRegular(i + 1))
                refStart = calendar.adjust(end.substract(schedule_.tenor()),
                        paymentAdjustment_);
            if (i == n - 1 && schedule_.hasIsRegular() && !schedule_.isRegular(i + 1))
                refEnd = calendar.adjust(start.add(schedule_.tenor()),
                        paymentAdjustment_);

            OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(
                    paymentDate,
                    get(notionals_, i, 1.0),
                    start,
                    end,
                    overnightIndex_,
                    get(gearings_, i, 1.0),
                    get(spreads_, i, 0.0),
                    refStart,
                    refEnd,
                    paymentDayCounter_,
                    telescopicValueDates_,
                    averagingMethod_,
                    lookbackDays_,
                    lockoutDays_,
                    applyObservationShift_);
            cashflows.add(coupon);
        }
        return cashflows;
    }
}
