package jquant.instruments;

import jquant.CashFlow;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.cashflows.Coupon;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.OvernightIndexedCoupon;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.OvernightIndex;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Overnight-indexed swap: fixed leg against a compounded overnight floating
 * leg.
 *
 * <p>Ported from QuantLib
 * {@code ql/instruments/overnightindexedswap.{hpp,cpp}} as far as
 * {@code test-suite/swaption.cpp} needs it. The official constructor also
 * accepts lookback/lockout/observation-shift/rounding options; those default
 * to the official zero/false values in the suite and are not exposed here
 * because the Java {@code OvernightLeg} does not implement them yet.
 */
public class OvernightIndexedSwap extends Instrument implements FixedVsFloatingSwap {
    private final Swap.Type type_;
    private final double nominal_;
    private final Schedule fixedSchedule_;
    private final double fixedRate_;
    private final DayCounter fixedDayCount_;
    private final Schedule overnightSchedule_;
    private final OvernightIndex overnightIndex_;
    private final double spread_;
    private final List<CashFlow> fixedLeg_;
    private final List<CashFlow> floatingLeg_;
    private double fairRate_ = Double.NaN;
    private double fixedLegNpv_ = Double.NaN;
    private double floatingLegNpv_ = Double.NaN;
    private double fixedLegBps_ = Double.NaN;
    private double floatingLegBps_ = Double.NaN;

    public OvernightIndexedSwap(Swap.Type type,
                                double nominal,
                                Schedule fixedSchedule,
                                double fixedRate,
                                DayCounter fixedDC,
                                Schedule overnightSchedule,
                                OvernightIndex overnightIndex,
                                double spread,
                                int paymentLag,
                                BusinessDayConvention paymentAdjustment,
                                Calendar paymentCalendar,
                                boolean telescopicValueDates,
                                RateAveraging averagingMethod) {
        type_ = type;
        nominal_ = nominal;
        fixedSchedule_ = fixedSchedule;
        fixedRate_ = fixedRate;
        fixedDayCount_ = fixedDC;
        overnightSchedule_ = overnightSchedule;
        overnightIndex_ = overnightIndex;
        spread_ = spread;

        Calendar fixedPaymentCalendar = (paymentCalendar == null || paymentCalendar.empty())
                ? fixedSchedule.calendar() : paymentCalendar;
        fixedLeg_ = new FixedRateLeg(fixedSchedule_)
                .withNotionals(nominal_)
                .withCouponRates(fixedRate_, fixedDayCount_)
                .withPaymentAdjustment(paymentAdjustment)
                .withPaymentLag(paymentLag)
                .withPaymentCalendar(fixedPaymentCalendar)
                .leg();

        Calendar floatingPaymentCalendar = (paymentCalendar == null || paymentCalendar.empty())
                ? overnightSchedule.calendar() : paymentCalendar;
        floatingLeg_ = new OvernightLeg(overnightSchedule_, overnightIndex_)
                .withNotionals(nominal_)
                .withSpreads(spread)
                .withTelescopicValueDates(telescopicValueDates)
                .withPaymentLag(paymentLag)
                .withPaymentAdjustment(paymentAdjustment)
                .withPaymentCalendar(floatingPaymentCalendar)
                .withAveragingMethod(averagingMethod)
                .build();

        registerWith(overnightIndex_);
        for (CashFlow cashflow : fixedLeg_)
            registerWith(cashflow);
        for (CashFlow cashflow : floatingLeg_)
            registerWith(cashflow);
    }

    /** Convenience constructor with the official default arguments. */
    public OvernightIndexedSwap(Swap.Type type,
                                double nominal,
                                Schedule schedule,
                                double fixedRate,
                                DayCounter fixedDC,
                                OvernightIndex overnightIndex,
                                double spread) {
        this(type, nominal, schedule, fixedRate, fixedDC, schedule, overnightIndex,
                spread, 0, BusinessDayConvention.FOLLOWING, null, false,
                RateAveraging.Simple);
    }

    @Override
    public Swap.Type swapType() {
        return type_;
    }

    @Override
    public double nominal() {
        return nominal_;
    }

    @Override
    public double fixedRate() {
        return fixedRate_;
    }

    @Override
    public double spread() {
        return spread_;
    }

    @Override
    public DayCounter fixedDayCount() {
        return fixedDayCount_;
    }

    @Override
    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    @Override
    public Schedule floatingSchedule() {
        return overnightSchedule_;
    }

    @Override
    public List<CashFlow> fixedLeg() {
        return List.copyOf(fixedLeg_);
    }

    @Override
    public List<CashFlow> floatingLeg() {
        return List.copyOf(floatingLeg_);
    }

    public OvernightIndex overnightIndex() {
        return overnightIndex_;
    }

    public Date startDate() {
        return fixedSchedule_.startDate();
    }

    public Date maturityDate() {
        return fixedSchedule_.endDate();
    }

    @Override
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

    @Override
    public double fixedLegBPS() {
        calculate();
        return fixedLegBps_;
    }

    @Override
    public double floatingLegBPS() {
        calculate();
        return floatingLegBps_;
    }

    @Override
    public boolean isExpired() {
        Date ref = overnightIndex_.forwardingTermStructure().empty()
                ? new Date()
                : overnightIndex_.forwardingTermStructure().currentLink().referenceDate();
        return TimeUtils.leq(maturityDate(), ref);
    }

    @Override
    public void deepUpdate() {
        for (CashFlow cashflow : floatingLeg_)
            cashflow.update();
        calculated_ = false;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof DiscountingSwapEngine.arguments, "wrong argument type");
        DiscountingSwapEngine.arguments arguments = (DiscountingSwapEngine.arguments) args;
        arguments.fixedRate = fixedRate_;
        arguments.nominal = nominal_;
        arguments.payerSign = type_ == Swap.Type.Payer ? 1.0 : -1.0;
        arguments.fixedPayDates = new ArrayList<>();
        arguments.fixedYearFractions = new ArrayList<>();
        for (CashFlow cashflow : fixedLeg_) {
            arguments.fixedPayDates.add(cashflow.date());
            arguments.fixedYearFractions.add(((Coupon) cashflow).accrualPeriod());
        }
        arguments.floatingLeg = new ArrayList<>(floatingLeg_);
        arguments.floatingNominalAccruals = new ArrayList<>();
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
