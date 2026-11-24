package jquant.instruments;

import jquant.CashFlow;
import jquant.PricingEngine;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.MultipleResetsLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Swap with a fixed leg and a multiple-resets floating leg. The floating leg
 * contains coupons whose rate is determined by compounding or averaging
 * {@code resetsPerCoupon} consecutive Ibor fixings during each accrual period.
 *
 * <p>Port of QuantLib's {@code MultipleResetsSwap} in
 * {@code ql/instruments/multipleresetsswap.{hpp,cpp}}. The common
 * fixed-vs-floating behaviour of {@code FixedVsFloatingSwap} is taken from the
 * official base class: QuantLib's {@code FixedVsFloatingSwap} is a class, while
 * the Java port exposes it as an interface implemented by
 * {@link OvernightIndexedSwap} and {@link VanillaSwap}, so the parts needed
 * here are ported into this class without changing those existing swaps.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/multipleresetsswap.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/multipleresetsswap.cpp
 */
public class MultipleResetsSwap extends Swap implements FixedVsFloatingSwap {
    private final Swap.Type type_;
    private final double nominal_;
    private final List<Double> fixedNominals_;
    private final Schedule fixedSchedule_;
    private final double fixedRate_;
    private final DayCounter fixedDayCount_;
    private final List<Double> floatingNominals_;
    private final Schedule floatingSchedule_;
    private final IborIndex iborIndex_;
    private final double spread_;
    private final DayCounter floatingDayCount_;
    private final BusinessDayConvention paymentConvention_;
    private final int paymentLag_;
    private final Calendar paymentCalendar_;
    private final Schedule fullResetSchedule_;
    private final int resetsPerCoupon_;
    private final RateAveraging averagingMethod_;
    private double fairRate_ = Double.NaN;

    public MultipleResetsSwap(Swap.Type type,
                              double nominal,
                              Schedule fixedSchedule,
                              double fixedRate,
                              DayCounter fixedDayCount,
                              Schedule fullResetSchedule,
                              IborIndex iborIndex,
                              int resetsPerCoupon) {
        this(type, nominal, fixedSchedule, fixedRate, fixedDayCount, fullResetSchedule,
                iborIndex, resetsPerCoupon, 0.0, RateAveraging.Compound,
                Optional.empty(), 0, null);
    }

    /**
     * Official constructor. A missing payment convention means the convention
     * of the floating schedule (the coupon schedule built from
     * {@code fullResetSchedule}); a missing payment calendar means the
     * respective leg schedule's calendar.
     */
    public MultipleResetsSwap(Swap.Type type,
                              double nominal,
                              Schedule fixedSchedule,
                              double fixedRate,
                              DayCounter fixedDayCount,
                              Schedule fullResetSchedule,
                              IborIndex iborIndex,
                              int resetsPerCoupon,
                              double spread,
                              RateAveraging averagingMethod,
                              Optional<BusinessDayConvention> paymentConvention,
                              int paymentLag,
                              Calendar paymentCalendar) {
        super(2);

        QL_REQUIRE(iborIndex != null, "null floating index provided");

        type_ = type;
        nominal_ = nominal;
        fixedSchedule_ = fixedSchedule;
        fixedRate_ = fixedRate;
        fixedDayCount_ = (fixedDayCount == null || fixedDayCount.empty())
                ? iborIndex.dayCounter() : fixedDayCount;
        fullResetSchedule_ = fullResetSchedule;
        resetsPerCoupon_ = resetsPerCoupon;
        iborIndex_ = iborIndex;
        spread_ = spread;
        averagingMethod_ = averagingMethod;
        paymentLag_ = paymentLag;
        paymentCalendar_ = paymentCalendar;
        floatingDayCount_ = iborIndex.dayCounter();

        fixedNominals_ = new ArrayList<>(
                Collections.<Double>nCopies(fixedSchedule.size() - 1, nominal));
        floatingNominals_ = new ArrayList<>(
                Collections.<Double>nCopies(
                        (fullResetSchedule.size() - 1) / resetsPerCoupon, nominal));

        floatingSchedule_ = buildCouponSchedule(fullResetSchedule, resetsPerCoupon);

        if (paymentConvention != null && paymentConvention.isPresent())
            paymentConvention_ = paymentConvention.get();
        else
            paymentConvention_ = floatingSchedule_.businessDayConvention();

        Calendar fixedPaymentCalendar = (paymentCalendar == null || paymentCalendar.empty())
                ? fixedSchedule_.calendar() : paymentCalendar;
        legs_.set(0, new FixedRateLeg(fixedSchedule_)
                .withNotionals(fixedNominals_)
                .withCouponRates(fixedRate_, fixedDayCount_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentLag(paymentLag_)
                .withPaymentCalendar(fixedPaymentCalendar)
                .leg());

        QL_REQUIRE((fullResetSchedule_.size() - 1) % resetsPerCoupon_ == 0,
                "number of reset periods (" + (fullResetSchedule_.size() - 1)
                        + ") is not a multiple of resetsPerCoupon ("
                        + resetsPerCoupon_ + ")");

        Calendar floatingPaymentCalendar = (paymentCalendar == null || paymentCalendar.empty())
                ? fullResetSchedule_.calendar() : paymentCalendar;
        legs_.set(1, new MultipleResetsLeg(fullResetSchedule_, iborIndex_, resetsPerCoupon_)
                .withNotionals(floatingNominals_)
                .withRateSpreads(spread_)
                .withAveragingMethod(averagingMethod_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentLag(paymentLag_)
                .withPaymentCalendar(floatingPaymentCalendar)
                .leg());

        switch (type_) {
            case Payer:
                payer_[0] = -1.0;
                payer_[1] = +1.0;
                break;
            case Receiver:
                payer_[0] = +1.0;
                payer_[1] = -1.0;
                break;
            default:
                QL_FAIL("Unknown vanilla-swap type");
        }

        registerWith(iborIndex_);
        for (CashFlow cashflow : legs_.get(0))
            registerWith(cashflow);
        for (CashFlow cashflow : legs_.get(1))
            registerWith(cashflow);
    }

    /** Official {@code buildCouponSchedule}: every {@code resetsPerCoupon}-th reset date. */
    private static Schedule buildCouponSchedule(final Schedule fullResetSchedule,
                                                int resetsPerCoupon) {
        int n = (fullResetSchedule.size() - 1) / resetsPerCoupon;
        List<Date> dates = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; ++i)
            dates.add(fullResetSchedule.date(i * resetsPerCoupon).copy());
        return new Schedule(dates);
    }

    //! \name Inspectors
    public Swap.Type type() {
        return type_;
    }

    @Override
    public Swap.Type swapType() {
        return type_;
    }

    @Override
    public double nominal() {
        return nominal_;
    }

    public List<Double> fixedNominals() {
        return List.copyOf(fixedNominals_);
    }

    @Override
    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    @Override
    public double fixedRate() {
        return fixedRate_;
    }

    @Override
    public DayCounter fixedDayCount() {
        return fixedDayCount_;
    }

    public List<Double> floatingNominals() {
        return List.copyOf(floatingNominals_);
    }

    @Override
    public Schedule floatingSchedule() {
        return floatingSchedule_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    @Override
    public double spread() {
        return spread_;
    }

    public DayCounter floatingDayCount() {
        return floatingDayCount_;
    }

    public BusinessDayConvention paymentConvention() {
        return paymentConvention_;
    }

    public int paymentLag() {
        return paymentLag_;
    }

    public Calendar paymentCalendar() {
        return paymentCalendar_;
    }

    @Override
    public List<CashFlow> fixedLeg() {
        return List.copyOf(legs_.get(0));
    }

    @Override
    public List<CashFlow> floatingLeg() {
        return List.copyOf(legs_.get(1));
    }

    public Schedule fullResetSchedule() {
        return fullResetSchedule_;
    }

    public int resetsPerCoupon() {
        return resetsPerCoupon_;
    }

    public RateAveraging averagingMethod() {
        return averagingMethod_;
    }

    //! \name Results
    @Override
    public double fairRate() {
        calculate();
        QL_REQUIRE(!Double.isNaN(fairRate_), "result not available");
        return fairRate_;
    }

    public double fixedLegNPV() {
        calculate();
        return legNPV(0);
    }

    public double floatingLegNPV() {
        calculate();
        return legNPV(1);
    }

    @Override
    public double fixedLegBPS() {
        calculate();
        return legBPS(0);
    }

    @Override
    public double floatingLegBPS() {
        calculate();
        return legBPS(1);
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        fairRate_ = Double.NaN;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        // Official FixedVsFloatingSwap::fetchResults: when the engine does not
        // report a fair rate, calculate it from the first leg's BPS.
        fairRate_ = Double.NaN;
        if (r instanceof DiscountingMultiLegSwapEngine.results results
                && results.legBPS != null && results.legBPS.length > 0) {
            double legBps = results.legBPS[0];
            if (!Double.isNaN(legBps) && legBps != 0.0)
                fairRate_ = fixedRate_ - NPV_ / (legBps / 1.0e-4);
        }
    }
}
