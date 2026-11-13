package jquant.instruments;

import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.SimpleCashFlow;
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
        type_ = type;
        nominal_ = nominal;
        fixedSchedule_ = fixedSchedule;
        fixedRate_ = fixedRate;
        fixedDayCount_ = fixedDayCount;
        floatSchedule_ = floatSchedule;
        iborIndex_ = iborIndex;
        spread_ = spread;
        floatingDayCount_ = floatingDayCount;
        registerWith(iborIndex_);
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

    public Date maturityDate() {
        return fixedSchedule_.endDate();
    }

    /**
     * End of the estimation period of the last floating coupon, mirroring
     * {@code IborCoupon::fixingEndDate()}. This can fall after the fixed
     * maturity when the ibor date is rolled and the fixed leg is not.
     */
    public Date lastFloatingFixingEnd() {
        int last = floatSchedule_.size() - 1;
        Date start = floatSchedule_.date(Math.max(last - 1, 0));
        Date end = floatSchedule_.date(last);
        return floatingFixingEndDate(start, end);
    }

    /**
     * Mirrors {@code IborCouponPricer::initializeCachedData}: indexed coupons
     * end at the index maturity, while at-par coupons end at the accrual end
     * rolled through the index fixing calendar.
     */
    private Date floatingFixingEndDate(Date start, Date end) {
        Date fixingDate = iborIndex_.fixingDate(start);
        Date fixingValueDate = iborIndex_.valueDate(fixingDate);
        if (!IborCoupon.Settings.instance().usingAtParCoupons())
            return iborIndex_.maturityDate(fixingValueDate);
        Date nextFixingDate = iborIndex_.fixingCalendar().advance(end,
                -iborIndex_.fixingDays(), TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        Date fixingEnd = iborIndex_.fixingCalendar().advance(nextFixingDate,
                iborIndex_.fixingDays(), TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        // make sure the estimation period contains at least one day
        return TimeUtils.max(fixingEnd, fixingValueDate.add(1));
    }

    /** Mirrors {@code IborCoupon::hasFixed()}. */
    private boolean hasFixed(Date fixingDate) {
        Date today = Settings.instance.evaluationDate().Date();
        if (TimeUtils.greater(fixingDate, today))
            return false;
        if (TimeUtils.less(fixingDate, today))
            return true;
        if (Settings.instance.enforcesTodaysHistoricFixings())
            return true;
        return iborIndex_.hasHistoricalFixing(fixingDate);
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
        // No explicit payment convention: QuantLib uses the floating schedule's.
        BusinessDayConvention paymentConvention = floatSchedule_.businessDayConvention();
        int fixedPeriods = fixedSchedule_.size() - 1;
        for (int i = 0; i < fixedPeriods; i++) {
            Date start = fixedSchedule_.date(i);
            Date end = fixedSchedule_.date(i + 1);
            arguments.fixedPayDates.add(floatSchedule_.calendar().adjust(end, paymentConvention));
            arguments.fixedYearFractions.add(fixedDayCount_.yearFraction(start, end, new Date(), new Date()));
        }
        arguments.floatingLeg = new ArrayList<>();
        arguments.floatingNominalAccruals = new ArrayList<>();
        int floatPeriods = floatSchedule_.size() - 1;
        for (int i = 0; i < floatPeriods; i++) {
            Date start = floatSchedule_.date(i);
            Date end = floatSchedule_.date(i + 1);
            Date pay = floatSchedule_.calendar().adjust(end, paymentConvention);
            Date fixingDate = iborIndex_.fixingDate(start);
            Date fixingValueDate = iborIndex_.valueDate(fixingDate);
            Date fixingEnd = floatingFixingEndDate(start, end);
            // Past fixing dates read the stored history. Future dates forecast
            // over the coupon's estimation period, as IborCoupon does: at-par
            // coupons end at the accrual end, indexed coupons at the index
            // maturity.
            double forward;
            if (hasFixed(fixingDate)) {
                forward = iborIndex_.pastFixing(fixingDate);
                QL_REQUIRE(!Double.isNaN(forward),
                        "Missing " + iborIndex_.name() + " fixing for " + fixingDate);
            } else {
                double spanningTime = iborIndex_.dayCounter().yearFraction(
                        fixingValueDate, fixingEnd, new Date(), new Date());
                forward = iborIndex_.forecastFixing(fixingValueDate, fixingEnd, spanningTime);
            }
            double accrual = floatingDayCount_.yearFraction(start, end, new Date(), new Date());
            double amount = nominal_ * (forward + spread_) * accrual;
            arguments.floatingLeg.add(new SimpleCashFlow(amount, pay));
            arguments.floatingNominalAccruals.add(nominal_ * accrual);
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
