package jquant.instruments;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.Coupon;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Spot fixed-vs-ibor vanilla swap. The floating coupon is
 * {@code nominal * (forecastFixing + spread) * yearFraction}. As in
 * {@code IborCoupon}, the fixing is forecast over the coupon's estimation
 * period: at-par coupons end at the accrual end, indexed coupons at the index
 * maturity. It is not the quoted fair rate.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/vanillaswap.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/swap.cpp
 */
public class VanillaSwap extends Instrument {
    public enum Type { Payer, Receiver }

    private final Type type_;
    private final double nominal_;
    private final Schedule fixedSchedule_;
    private final double fixedRate_;
    private final DayCounter fixedDayCount_;
    private final Schedule floatSchedule_;
    private final IborIndex iborIndex_;
    private final double spread_;
    private final DayCounter floatingDayCount_;
    private final BusinessDayConvention paymentConvention_;
    private final List<CashFlow> floatingLeg_;
    private double fairRate_ = Double.NaN;
    private double fixedLegNpv_ = Double.NaN;
    private double floatingLegNpv_ = Double.NaN;
    private double fixedLegBps_ = Double.NaN;
    private double floatingLegBps_ = Double.NaN;

    public VanillaSwap(Type type,
                       double nominal,
                       Schedule fixedSchedule,
                       double fixedRate,
                       DayCounter fixedDayCount,
                       Schedule floatSchedule,
                       IborIndex iborIndex,
                       double spread,
                       DayCounter floatingDayCount) {
        this(type, nominal, fixedSchedule, fixedRate, fixedDayCount, floatSchedule,
                iborIndex, spread, floatingDayCount, Optional.empty(), Optional.empty());
    }

    /**
     * Constructor matching QuantLib's {@code VanillaSwap} including the
     * optional payment convention and the per-swap indexed/at-par coupon
     * override. As in the official class, a missing payment convention means
     * the floating schedule's convention, and a missing coupon override means
     * the global {@link IborCoupon.Settings} value.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/vanillaswap.cpp
     */
    public VanillaSwap(Type type,
                       double nominal,
                       Schedule fixedSchedule,
                       double fixedRate,
                       DayCounter fixedDayCount,
                       Schedule floatSchedule,
                       IborIndex iborIndex,
                       double spread,
                       DayCounter floatingDayCount,
                       Optional<BusinessDayConvention> paymentConvention,
                       Optional<Boolean> useIndexedCoupons) {
        type_ = type;
        nominal_ = nominal;
        fixedSchedule_ = fixedSchedule;
        fixedRate_ = fixedRate;
        fixedDayCount_ = fixedDayCount;
        floatSchedule_ = floatSchedule;
        iborIndex_ = iborIndex;
        spread_ = spread;
        floatingDayCount_ = floatingDayCount;
        paymentConvention_ = paymentConvention != null && paymentConvention.isPresent()
                ? paymentConvention.get()
                : floatSchedule.businessDayConvention();
        // As in ql/instruments/vanillaswap.cpp, the floating leg is built by
        // IborLeg so that each coupon carries a pricer and so that the
        // useIndexedCoupons override can reach the coupon estimation.
        floatingLeg_ = new IborLeg(floatSchedule, iborIndex)
                .withNotionals(nominal)
                .withPaymentDayCounter(floatingDayCount)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentCalendar(floatSchedule.calendar())
                .withSpreads(spread)
                .withIndexedCoupons(useIndexedCoupons != null
                        ? useIndexedCoupons : Optional.empty())
                .leg();
        registerWith(iborIndex_);
        for (CashFlow cashflow : floatingLeg_)
            registerWith(cashflow);
    }

    public Type type() {
        return type_;
    }

    public double fixedRate() {
        return fixedRate_;
    }

    public double nominal() {
        return nominal_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    public Schedule floatingSchedule() {
        return floatSchedule_;
    }

    public Date startDate() {
        return fixedSchedule_.startDate();
    }

    public Date maturityDate() {
        return fixedSchedule_.endDate();
    }

    /** Floating leg coupons, as {@code VanillaSwap::floatingLeg()} in QuantLib. */
    public List<CashFlow> floatingLeg() {
        return List.copyOf(floatingLeg_);
    }

    /**
     * End of the estimation period of the last floating coupon, mirroring
     * {@code IborCoupon::fixingEndDate()}. This can fall after the fixed
     * maturity when the ibor date is rolled and the fixed leg is not.
     */
    public Date lastFloatingFixingEnd() {
        return ((IborCoupon) floatingLeg_.get(floatingLeg_.size() - 1)).fixingEndDate();
    }

    /**
     * Mirrors {@code Swap::deepUpdate()}: clears the cached amounts of the
     * floating coupons so that their forecast uses the current curve.
     */
    public void deepUpdate() {
        for (CashFlow cashflow : floatingLeg_)
            cashflow.update();
        calculated_ = false;
    }

    public double fairRate() {
        calculate();
        QL_REQUIRE(!Double.isNaN(fairRate_), "fair rate not provided");
        return fairRate_;
    }

    public double fixedLegNPV() {
        calculate();
        return fixedLegNpv_;
    }

    public double floatingLegNPV() {
        calculate();
        return floatingLegNpv_;
    }

    public double fixedLegBPS() {
        calculate();
        return fixedLegBps_;
    }

    public double floatingLegBPS() {
        calculate();
        return floatingLegBps_;
    }

    @Override
    public boolean isExpired() {
        Date ref = iborIndex_.forwardingTermStructure().empty()
                ? new Date()
                : iborIndex_.forwardingTermStructure().currentLink().referenceDate();
        return TimeUtils.leq(maturityDate(), ref);
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof DiscountingSwapEngine.arguments, "wrong argument type");
        DiscountingSwapEngine.arguments arguments = (DiscountingSwapEngine.arguments) args;
        arguments.fixedRate = fixedRate_;
        arguments.nominal = nominal_;
        arguments.payerSign = type_ == Type.Payer ? 1.0 : -1.0;
        arguments.fixedPayDates = new ArrayList<>();
        arguments.fixedYearFractions = new ArrayList<>();
        // As in FixedVsFloatingSwap, the fixed leg pays on the fixed calendar
        // using the swap payment convention (the floating schedule's
        // convention when none was passed).
        int fixedPeriods = fixedSchedule_.size() - 1;
        for (int i = 0; i < fixedPeriods; i++) {
            Date start = fixedSchedule_.date(i);
            Date end = fixedSchedule_.date(i + 1);
            arguments.fixedPayDates.add(fixedSchedule_.calendar().adjust(end, paymentConvention_));
            arguments.fixedYearFractions.add(fixedDayCount_.yearFraction(start, end, new Date(), new Date()));
        }
        arguments.floatingLeg = new ArrayList<>(floatingLeg_);
        arguments.floatingNominalAccruals = new ArrayList<>();
        // Make sure each coupon reads the current curve before it caches its
        // amount; the helper links its curve without observation so that
        // bootstrapping does not create a notification cycle.
        for (CashFlow cashflow : floatingLeg_) {
            cashflow.update();
            Coupon coupon = (Coupon) cashflow;
            arguments.floatingNominalAccruals.add(nominal_ * coupon.accrualPeriod());
        }
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        QL_REQUIRE(r instanceof DiscountingSwapEngine.results, "wrong result type");
        DiscountingSwapEngine.results results = (DiscountingSwapEngine.results) r;
        fairRate_ = results.fairRate;
        fixedLegNpv_ = results.fixedLegNpv;
        floatingLegNpv_ = results.floatingLegNpv;
        fixedLegBps_ = results.fixedLegBPS;
        floatingLegBps_ = results.floatingLegBPS;
    }
}
