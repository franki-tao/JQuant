package jquant.termstructures.yield;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.CashflowUtil;
import jquant.cashflows.FloatingRateCouponPricer;
import jquant.cashflows.IborCoupon;
import jquant.indexes.IborIndex;
import jquant.indexes.SwapIndex;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from a spot vanilla-swap quote. The implied
 * quote is the swap fair rate (including the quoted spread) on the curve being
 * built, not the stored quote. The default pillar is the last relevant date,
 * which is the later of the swap maturity and the last floating index end.
 *
 * <p>If an exogenous discounting curve is passed, the floating leg is still
 * forecast on the curve being built while both legs are discounted on the
 * exogenous curve.
 *
 * <p>This ports all three official constructors: the tenor one (including its
 * optional settlement days, end-of-month flag, indexed-coupon override,
 * floating convention override, coupon pricer and custom pillar), the dated
 * one (explicit start and end dates, which rejects {@link Frequency#ONCE}),
 * and the {@code SwapIndex} one.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class SwapRateHelper extends RateHelper {
    public enum Pillar { MaturityDate, LastRelevantDate, CustomDate }

    private final Period tenor_;
    private final Date startDate_, endDate_;
    private final Calendar calendar_;
    private final Frequency fixedFrequency_;
    private final BusinessDayConvention fixedConvention_;
    private final DayCounter fixedDayCount_;
    private IborIndex iborIndex_;
    private final Pillar pillarChoice_;
    private final Date customPillarDate_;
    private final Handle<Quote> spread_;
    private final Period fwdStart_;
    private final Handle<YieldTermStructure> discountHandle_;
    private final Integer settlementDays_;
    private final boolean endOfMonth_;
    private final Optional<Boolean> useIndexedCoupons_;
    private final Optional<BusinessDayConvention> floatConvention_;
    private final FloatingRateCouponPricer couponPricer_;
    private final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>();
    private final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<>();
    private VanillaSwap swap_;

    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount,
                iborIndex, null, new Period(0, TimeUnit.DAYS), null,
                null, Pillar.LastRelevantDate, new Date(), false,
                Optional.empty(), Optional.empty(), null);
    }

    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Pillar pillarChoice) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount,
                iborIndex, null, new Period(0, TimeUnit.DAYS), null,
                null, pillarChoice, new Date(), false,
                Optional.empty(), Optional.empty(), null);
    }

    /** Mirrors {@code SwapRateHelper(rate, tenor, ..., spread, fwdStart, discountingCurve)}. */
    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Handle<Quote> spread,
                          Period fwdStart,
                          Handle<YieldTermStructure> discountingCurve) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount,
                iborIndex, spread, fwdStart, discountingCurve,
                null, Pillar.LastRelevantDate, new Date(), false,
                Optional.empty(), Optional.empty(), null);
    }

    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Handle<Quote> spread,
                          Period fwdStart,
                          Handle<YieldTermStructure> discountingCurve,
                          Pillar pillarChoice) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount,
                iborIndex, spread, fwdStart, discountingCurve,
                null, pillarChoice, new Date(), false,
                Optional.empty(), Optional.empty(), null);
    }

    /**
     * Official tenor constructor with all optional arguments. {@code settlementDays}
     * may be {@code null} (the official {@code Null<Natural>()}); in that case
     * the spot date is the index value date, as in {@code MakeVanillaSwap}.
     */
    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Handle<Quote> spread,
                          Period fwdStart,
                          Handle<YieldTermStructure> discountingCurve,
                          Integer settlementDays,
                          Pillar pillarChoice,
                          Date customPillarDate,
                          boolean endOfMonth,
                          Optional<Boolean> useIndexedCoupons,
                          Optional<BusinessDayConvention> floatConvention,
                          FloatingRateCouponPricer couponPricer) {
        super(rate);
        QL_REQUIRE(iborIndex != null, "null ibor index");
        tenor_ = tenor;
        startDate_ = new Date();
        endDate_ = new Date();
        calendar_ = calendar;
        fixedFrequency_ = fixedFrequency;
        fixedConvention_ = fixedConvention;
        fixedDayCount_ = fixedDayCount;
        pillarChoice_ = pillarChoice != null ? pillarChoice : Pillar.LastRelevantDate;
        customPillarDate_ = customPillarDate != null ? customPillarDate : new Date();
        spread_ = spread != null ? spread : new Handle<>(null, false);
        fwdStart_ = fwdStart != null ? fwdStart : new Period(0, TimeUnit.DAYS);
        discountHandle_ = discountingCurve != null
                ? discountingCurve : new Handle<>(null, false);
        settlementDays_ = settlementDays;
        endOfMonth_ = endOfMonth;
        useIndexedCoupons_ = useIndexedCoupons != null ? useIndexedCoupons : Optional.empty();
        floatConvention_ = floatConvention != null ? floatConvention : Optional.empty();
        couponPricer_ = couponPricer;
        initialize(iborIndex);
    }

    /**
     * Official dated constructor. As in QuantLib, an explicit start and end
     * date are incompatible with a {@link Frequency#ONCE} fixed leg.
     */
    public SwapRateHelper(Handle<Quote> rate,
                          Date startDate,
                          Date endDate,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex) {
        this(rate, startDate, endDate, calendar, fixedFrequency, fixedConvention,
                fixedDayCount, iborIndex, null, null, Pillar.LastRelevantDate,
                new Date(), false, Optional.empty(), Optional.empty(), null);
    }

    public SwapRateHelper(Handle<Quote> rate,
                          Date startDate,
                          Date endDate,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Handle<Quote> spread,
                          Handle<YieldTermStructure> discountingCurve,
                          Pillar pillarChoice,
                          Date customPillarDate,
                          boolean endOfMonth,
                          Optional<Boolean> useIndexedCoupons,
                          Optional<BusinessDayConvention> floatConvention,
                          FloatingRateCouponPricer couponPricer) {
        super(rate);
        QL_REQUIRE(iborIndex != null, "null ibor index");
        QL_REQUIRE(fixedFrequency != Frequency.ONCE,
                "fixedFrequency == Once is not supported when passing explicit "
                        + "startDate and endDate");
        tenor_ = null;
        startDate_ = startDate != null ? startDate : new Date();
        endDate_ = endDate != null ? endDate : new Date();
        calendar_ = calendar;
        fixedFrequency_ = fixedFrequency;
        fixedConvention_ = fixedConvention;
        fixedDayCount_ = fixedDayCount;
        pillarChoice_ = pillarChoice != null ? pillarChoice : Pillar.LastRelevantDate;
        customPillarDate_ = customPillarDate != null ? customPillarDate : new Date();
        spread_ = spread != null ? spread : new Handle<>(null, false);
        fwdStart_ = new Period(0, TimeUnit.DAYS);
        discountHandle_ = discountingCurve != null
                ? discountingCurve : new Handle<>(null, false);
        settlementDays_ = null;
        endOfMonth_ = endOfMonth;
        useIndexedCoupons_ = useIndexedCoupons != null ? useIndexedCoupons : Optional.empty();
        floatConvention_ = floatConvention != null ? floatConvention : Optional.empty();
        couponPricer_ = couponPricer;
        initialize(iborIndex);
    }

    /**
     * Official constructor taking a {@code SwapIndex}. QuantLib delegates to
     * the tenor constructor using the index's tenor, fixing calendar, fixed
     * leg frequency/convention/day counter and ibor index, with null
     * settlement days and no floating convention override.
     */
    public SwapRateHelper(Handle<Quote> rate,
                          SwapIndex swapIndex,
                          Handle<Quote> spread,
                          Period fwdStart,
                          Handle<YieldTermStructure> discountingCurve,
                          Pillar pillarChoice,
                          Date customPillarDate,
                          boolean endOfMonth,
                          Optional<Boolean> useIndexedCoupons,
                          FloatingRateCouponPricer couponPricer) {
        this(rate, swapIndex.tenor(), swapIndex.fixingCalendar(),
                swapIndex.fixedLegTenor().frequency(), swapIndex.fixedLegConvention(),
                swapIndex.dayCounter(), swapIndex.iborIndex(), spread, fwdStart,
                discountingCurve, null, pillarChoice, customPillarDate, endOfMonth,
                useIndexedCoupons, Optional.empty(), couponPricer);
    }

    private void initialize(IborIndex iborIndex) {
        // Clone the index and link it to the curve under construction.  The
        // relinkable handle must not register as an observer (it would create
        // a notification cycle while bootstrapping).
        iborIndex_ = iborIndex.clone(termStructureHandle_);
        pillarDate_ = customPillarDate_;
        initializeDates();
    }

    public VanillaSwap swap() {
        return swap_;
    }

    public double spread() {
        return spread_.empty() ? 0.0 : spread_.getValue().value();
    }

    public Period forwardStart() {
        return fwdStart_;
    }

    @Override
    public List<Quote> notifications() {
        List<Quote> result = new ArrayList<>(super.notifications());
        if (!spread_.empty())
            result.add(spread_.getValue());
        return result;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        // Do not register the relinkable handles as observers: force
        // recalculation when needed instead.
        boolean observer = false;
        termStructureHandle_.linkTo(termStructure, observer);
        if (discountHandle_.empty())
            discountRelinkableHandle_.linkTo(termStructure, observer);
        else
            discountRelinkableHandle_.linkTo(discountHandle_.currentLink(), observer);
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        // The helper does not register as an observer of the curve, so force
        // the coupons to recalculate against the current curve.
        swap_.deepUpdate();
        double basisPoint = 1.0e-4;
        double floatingLegNPV = swap_.floatingLegNPV();
        double spreadNPV = swap_.floatingLegBPS() / basisPoint * spread();
        double totNPV = -(floatingLegNPV + spreadNPV);
        return totNPV / (swap_.fixedLegBPS() / basisPoint);
    }

    private void initializeDates() {
        Date start;
        boolean explicitStart = TimeUtils.neq(startDate_, new Date());
        if (explicitStart) {
            start = startDate_;
        } else {
            Date evaluation = Settings.instance.evaluationDate().Date();
            Date spot;
            if (settlementDays_ == null) {
                // Mirrors MakeVanillaSwap: the spot date comes from the index's
                // valueDate interface, whose reference date is the evaluation
                // date adjusted on the index fixing calendar.
                Date refDate = iborIndex_.fixingCalendar().adjust(evaluation,
                        BusinessDayConvention.FOLLOWING);
                spot = iborIndex_.valueDate(refDate);
            } else {
                // Settlement days are counted from the actual evaluation date,
                // even when it is not a business day (official issue #753).
                Calendar settlementCalendar = calendar_;
                spot = settlementCalendar.advance(evaluation, settlementDays_,
                        TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
            }
            start = spot.add(fwdStart_);
            if (fwdStart_.length() < 0)
                start = calendar_.adjust(start, BusinessDayConvention.PRECEDING);
            else if (fwdStart_.length() > 0)
                start = calendar_.adjust(start, BusinessDayConvention.FOLLOWING);
        }

        Date end;
        if (TimeUtils.neq(endDate_, new Date()))
            end = endDate_;
        else {
            // Mirrors MakeVanillaSwap: termination date is the unadjusted
            // start plus the swap tenor. The schedule then adjusts the
            // termination date with its termination-date convention; advancing
            // the maturity here instead would create spurious stubs whenever
            // the anniversary falls on a weekend.
            end = start.add(tenor_);
        }

        // MakeVanillaSwap uses fixedFrequency_ == Once ? tenor_ : Period(fixedFrequency_).
        Period fixedTenor = fixedFrequency_ == Frequency.ONCE
                ? tenor_ : new Period(fixedFrequency_);
        BusinessDayConvention floatConvention = floatConvention_.orElse(
                iborIndex_.businessDayConvention());

        Schedule fixed = new Schedule(start, end, fixedTenor, calendar_,
                fixedConvention_, fixedConvention_,
                DateGenerationRule.BACKWARD, endOfMonth_, new Date(), new Date());
        Schedule floating = new Schedule(start, end, iborIndex_.tenor(), calendar_,
                floatConvention, floatConvention,
                DateGenerationRule.BACKWARD, endOfMonth_, new Date(), new Date());
        swap_ = new VanillaSwap(VanillaSwap.Type.Payer, 1.0, fixed, 0.0, fixedDayCount_,
                floating, iborIndex_, 0.0, iborIndex_.dayCounter(),
                Optional.empty(), useIndexedCoupons_);
        swap_.setPricingEngine(new DiscountingSwapEngine(discountRelinkableHandle_));

        if (couponPricer_ != null)
            CashflowUtil.setCouponPricer(swap_.floatingLeg(), couponPricer_);

        earliestDate_ = swap_.startDate();
        maturityDate_ = swap_.maturityDate();
        List<CashFlow> floatingLeg = swap_.floatingLeg();
        Date lastFixingEnd = ((IborCoupon) floatingLeg.get(floatingLeg.size() - 1)).fixingEndDate();
        latestRelevantDate_ = TimeUtils.greater(lastFixingEnd, maturityDate_)
                ? lastFixingEnd : maturityDate_;

        switch (pillarChoice_) {
            case MaturityDate:
                pillarDate_ = maturityDate_;
                break;
            case LastRelevantDate:
                pillarDate_ = latestRelevantDate_;
                break;
            case CustomDate:
                // pillarDate_ already assigned at construction time
                QL_REQUIRE(!TimeUtils.less(pillarDate_, earliestDate_),
                        "pillar date (" + pillarDate_ + ") must be later "
                                + "than or equal to the instrument's earliest date ("
                                + earliestDate_ + ")");
                QL_REQUIRE(!TimeUtils.greater(pillarDate_, latestRelevantDate_),
                        "pillar date (" + pillarDate_ + ") must be before "
                                + "or equal to the instrument's latest relevant date ("
                                + latestRelevantDate_ + ")");
                break;
            default:
                QL_REQUIRE(false, "unknown Pillar::Choice");
        }
    }
}
