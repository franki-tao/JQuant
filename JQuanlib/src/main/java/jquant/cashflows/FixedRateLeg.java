package jquant.cashflows;

import jquant.CashFlow;
import jquant.InterestRate;
import jquant.Compounding;
import jquant.time.*;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! helper class building a sequence of fixed rate coupons
/*! https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/fixedratecoupon.hpp */
public class FixedRateLeg {
    private final Schedule schedule_;
    private List<Double> notionals_ = new ArrayList<>();
    private List<InterestRate> couponRates_ = new ArrayList<>();
    private DayCounter firstPeriodDC_ = new DayCounter();
    private DayCounter lastPeriodDC_ = new DayCounter();
    private Calendar paymentCalendar_;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.FOLLOWING;
    private int paymentLag_ = 0;
    private Period exCouponPeriod_ = new Period();
    private Calendar exCouponCalendar_ = new NullCalendar();
    private BusinessDayConvention exCouponAdjustment_ = BusinessDayConvention.FOLLOWING;
    private boolean exCouponEndOfMonth_ = false;

    public FixedRateLeg(Schedule schedule) {
        schedule_ = schedule;
        paymentCalendar_ = schedule.calendar();
    }

    public FixedRateLeg withNotionals(double notional) {
        notionals_ = new ArrayList<>(List.of(notional));
        return this;
    }

    public FixedRateLeg withNotionals(final List<Double> notionals) {
        notionals_ = notionals;
        return this;
    }

    public FixedRateLeg withCouponRates(double rate,
                                        final DayCounter paymentDayCounter,
                                        Compounding comp,
                                        Frequency freq) {
        couponRates_ = new ArrayList<>(List.of(new InterestRate(rate, paymentDayCounter, comp, freq)));
        return this;
    }

    public FixedRateLeg withCouponRates(double rate,
                                        final DayCounter paymentDayCounter) {
        return withCouponRates(rate, paymentDayCounter, Compounding.Simple, Frequency.ANNUAL);
    }

    public FixedRateLeg withCouponRates(final List<Double> rates,
                                        final DayCounter paymentDayCounter,
                                        Compounding comp,
                                        Frequency freq) {
        couponRates_ = new ArrayList<>(rates.size());
        for (double rate : rates)
            couponRates_.add(new InterestRate(rate, paymentDayCounter, comp, freq));
        return this;
    }

    public FixedRateLeg withCouponRates(final List<Double> rates,
                                        final DayCounter paymentDayCounter) {
        return withCouponRates(rates, paymentDayCounter, Compounding.Simple, Frequency.ANNUAL);
    }

    public FixedRateLeg withCouponRates(final InterestRate i) {
        couponRates_ = new ArrayList<>(List.of(i));
        return this;
    }

    public FixedRateLeg withCouponRates(final List<InterestRate> interestRates) {
        couponRates_ = interestRates;
        return this;
    }

    public FixedRateLeg withPaymentAdjustment(BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public FixedRateLeg withFirstPeriodDayCounter(final DayCounter dayCounter) {
        firstPeriodDC_ = dayCounter;
        return this;
    }

    public FixedRateLeg withLastPeriodDayCounter(final DayCounter dayCounter) {
        lastPeriodDC_ = dayCounter;
        return this;
    }

    public FixedRateLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public FixedRateLeg withPaymentLag(Integer lag) {
        paymentLag_ = lag;
        return this;
    }

    public FixedRateLeg withExCouponPeriod(final Period period,
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
        QL_REQUIRE(!couponRates_.isEmpty(), "no coupon rates given");
        QL_REQUIRE(!notionals_.isEmpty(), "no notional given");

        List<CashFlow> leg = new ArrayList<>(schedule_.size() - 1);

        // first period might be short or long
        Date start = schedule_.date(0), end = schedule_.date(1);
        Date paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.DAYS,
                paymentAdjustment_, false);
        Date exCouponDate = new Date();
        InterestRate rate = couponRates_.get(0);
        double nominal = notionals_.get(0);

        if (TimeUtils.neq(exCouponPeriod_, new Period())) {
            exCouponDate = exCouponCalendar_.advance(paymentDate,
                    TimeUtils.multiply(-1, exCouponPeriod_),
                    exCouponAdjustment_, exCouponEndOfMonth_);
        }
        Date ref = schedule_.hasTenor()
                && schedule_.hasIsRegular() && !schedule_.isRegular(1)
                ? schedule_.calendar().advance(end,
                TimeUtils.multiply(-1, schedule_.tenor()),
                schedule_.businessDayConvention(),
                schedule_.endOfMonth())
                : start;
        InterestRate r = new InterestRate(rate.rate(),
                firstPeriodDC_.empty() ? rate.dayCounter()
                        : firstPeriodDC_,
                rate.compounding(), rate.frequency());
        leg.add(new FixedRateCoupon(paymentDate, nominal, r,
                start, end, ref, end, exCouponDate));
        // regular periods
        for (int i = 2; i < schedule_.size() - 1; ++i) {
            start = end;
            end = schedule_.date(i);
            paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.DAYS,
                    paymentAdjustment_, false);
            if (TimeUtils.neq(exCouponPeriod_, new Period())) {
                exCouponDate = exCouponCalendar_.advance(paymentDate,
                        TimeUtils.multiply(-1, exCouponPeriod_),
                        exCouponAdjustment_, exCouponEndOfMonth_);
            }
            if ((i - 1) < couponRates_.size())
                rate = couponRates_.get(i - 1);
            else
                rate = couponRates_.get(couponRates_.size() - 1);
            if ((i - 1) < notionals_.size())
                nominal = notionals_.get(i - 1);
            else
                nominal = notionals_.get(notionals_.size() - 1);
            leg.add(new FixedRateCoupon(paymentDate, nominal, rate,
                    start, end, start, end, exCouponDate));
        }
        if (schedule_.size() > 2) {
            // last period might be short or long
            int N = schedule_.size();
            start = end;
            end = schedule_.date(N - 1);
            paymentDate = paymentCalendar_.advance(end, paymentLag_, TimeUnit.DAYS,
                    paymentAdjustment_, false);
            if (TimeUtils.neq(exCouponPeriod_, new Period())) {
                exCouponDate = exCouponCalendar_.advance(paymentDate,
                        TimeUtils.multiply(-1, exCouponPeriod_),
                        exCouponAdjustment_, exCouponEndOfMonth_);
            }
            if ((N - 2) < couponRates_.size())
                rate = couponRates_.get(N - 2);
            else
                rate = couponRates_.get(couponRates_.size() - 1);
            if ((N - 2) < notionals_.size())
                nominal = notionals_.get(N - 2);
            else
                nominal = notionals_.get(notionals_.size() - 1);
            InterestRate rLast = new InterestRate(rate.rate(),
                    lastPeriodDC_.empty() ? rate.dayCounter() : lastPeriodDC_,
                    rate.compounding(), rate.frequency());
            if ((schedule_.hasIsRegular() && schedule_.isRegular(N - 1))
                    || !schedule_.hasTenor()) {
                leg.add(new FixedRateCoupon(paymentDate, nominal, rLast,
                        start, end, start, end, exCouponDate));
            } else {
                Date refLast = schedule_.calendar().advance(
                        start,
                        schedule_.tenor(),
                        schedule_.businessDayConvention(),
                        schedule_.endOfMonth());
                leg.add(new FixedRateCoupon(paymentDate, nominal, rLast,
                        start, end, start, refLast, exCouponDate));
            }
        }
        return leg;
    }
}
