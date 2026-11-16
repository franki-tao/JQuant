package jquant.termstructures.yield;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.OvernightIndexedCoupon;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.OvernightIndex;
import jquant.instruments.Swap;
import jquant.patterns.Observable;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Rate helper for bootstrapping over overnight-overnight basis swaps.
 *
 * <p>The swap pays {@code baseIndex + basis} and receives {@code otherIndex}.
 * The helper can bootstrap the forecast curve for either index; the other
 * index must have an existing forecast curve.
 *
 * <p>An exogenous discount curve can be passed. If none is passed, the curve
 * being bootstrapped is also used for discounting.
 *
 * <p>Port of QuantLib's {@code OvernightOvernightBasisSwapRateHelper} in
 * {@code ql/experimental/termstructures/basisswapratehelpers.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/experimental/termstructures/basisswapratehelpers.cpp
 */
public class OvernightOvernightBasisSwapRateHelper extends RateHelper {
    private final Period tenor_;
    private final int settlementDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    private final boolean endOfMonth_;
    private final OvernightIndex baseIndex_;
    private final OvernightIndex otherIndex_;
    private final Handle<YieldTermStructure> discountHandle_;
    private final boolean bootstrapBaseCurve_;
    private final int paymentLag_;
    private final Frequency paymentFrequency_;
    private final RateAveraging baseAveragingMethod_;
    private final RateAveraging otherAveragingMethod_;
    private final boolean telescopicValueDates_;
    private final DateGenerationRule rule_;

    private Swap swap_;

    private final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>();
    private final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<>();

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, new Handle<>(null, false), false);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, false);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle,
                                                 boolean bootstrapBaseCurve) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                0, Frequency.ANNUAL, RateAveraging.Compound,
                RateAveraging.Compound, false, DateGenerationRule.BACKWARD);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle,
                                                 boolean bootstrapBaseCurve,
                                                 int paymentLag,
                                                 Frequency paymentFrequency) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, paymentFrequency, RateAveraging.Compound,
                RateAveraging.Compound, false, DateGenerationRule.BACKWARD);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle,
                                                 boolean bootstrapBaseCurve,
                                                 int paymentLag,
                                                 Frequency paymentFrequency,
                                                 RateAveraging baseAveragingMethod,
                                                 RateAveraging otherAveragingMethod) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, paymentFrequency, baseAveragingMethod,
                otherAveragingMethod, false);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle,
                                                 boolean bootstrapBaseCurve,
                                                 int paymentLag,
                                                 Frequency paymentFrequency,
                                                 RateAveraging baseAveragingMethod,
                                                 RateAveraging otherAveragingMethod,
                                                 boolean telescopicValueDates) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, paymentFrequency, baseAveragingMethod,
                otherAveragingMethod, telescopicValueDates,
                DateGenerationRule.BACKWARD);
    }

    public OvernightOvernightBasisSwapRateHelper(Handle<Quote> basis,
                                                 Period tenor,
                                                 int settlementDays,
                                                 Calendar calendar,
                                                 BusinessDayConvention convention,
                                                 boolean endOfMonth,
                                                 OvernightIndex baseIndex,
                                                 OvernightIndex otherIndex,
                                                 Handle<YieldTermStructure> discountHandle,
                                                 boolean bootstrapBaseCurve,
                                                 int paymentLag,
                                                 Frequency paymentFrequency,
                                                 RateAveraging baseAveragingMethod,
                                                 RateAveraging otherAveragingMethod,
                                                 boolean telescopicValueDates,
                                                 DateGenerationRule rule) {
        super(basis);
        QL_REQUIRE(baseIndex != null, "null base overnight index");
        QL_REQUIRE(otherIndex != null, "null other overnight index");

        tenor_ = tenor;
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        discountHandle_ = discountHandle != null
                ? discountHandle : new Handle<>(null, false);
        bootstrapBaseCurve_ = bootstrapBaseCurve;
        paymentLag_ = paymentLag;
        paymentFrequency_ = paymentFrequency != null
                ? paymentFrequency : Frequency.ANNUAL;
        baseAveragingMethod_ = baseAveragingMethod != null
                ? baseAveragingMethod : RateAveraging.Compound;
        otherAveragingMethod_ = otherAveragingMethod != null
                ? otherAveragingMethod : RateAveraging.Compound;
        telescopicValueDates_ = telescopicValueDates;
        rule_ = rule != null ? rule : DateGenerationRule.BACKWARD;

        // We need to clone the index whose forecast curve we want to
        // bootstrap and copy the other one.
        if (bootstrapBaseCurve_) {
            baseIndex_ = (OvernightIndex) baseIndex.clone(termStructureHandle_);
            otherIndex_ = otherIndex;
        } else {
            baseIndex_ = baseIndex;
            otherIndex_ = (OvernightIndex) otherIndex.clone(termStructureHandle_);
        }

        initializeDates();
    }

    public Swap swap() {
        return swap_;
    }

    /**
     * Observables whose changes must rebuild a curve using this helper. The
     * discount handle is included so that late linking an exogenous discount
     * curve after the curve construction triggers a rebootstrap.
     */
    @Override
    public List<Observable> notificationObservables() {
        return List.of(discountHandle_.get());
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        // Do not set the relinkable handle as an observer: force
        // recalculation when needed; the index is not lazy.
        boolean observer = false;

        termStructureHandle_.linkTo(termStructure, observer);

        if (discountHandle_.empty())
            discountRelinkableHandle_.linkTo(termStructure, observer);
        else
            discountRelinkableHandle_.linkTo(discountHandle_.currentLink(), observer);

        super.setTermStructure(termStructure);
    }

    @Override
    public double impliedQuote() {
        swap_.deepUpdate();
        return -(swap_.NPV() / swap_.legBPS(0)) * 1.0e-4;
    }

    private void initializeDates() {
        Date today = Settings.instance.evaluationDate().Date();
        Date earliestDate = calendar_.advance(today, settlementDays_,
                TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        earliestDate_ = earliestDate;
        // unadjusted, to avoid a spurious stub
        Date terminationDate = earliestDate.add(tenor_);

        Schedule schedule = new MakeSchedule()
                .from(earliestDate)
                .to(terminationDate)
                .withFrequency(paymentFrequency_)
                .withCalendar(calendar_)
                .withConvention(convention_)
                .endOfMonth(endOfMonth_)
                .withRule(rule_)
                .toSchedule();

        maturityDate_ = schedule.endDate();

        List<CashFlow> baseLeg = new OvernightLeg(schedule, baseIndex_)
                .withNotionals(100.0)
                .withPaymentLag(paymentLag_)
                .withTelescopicValueDates(
                        telescopicValueDates_
                                && baseAveragingMethod_ == RateAveraging.Compound)
                .withAveragingMethod(baseAveragingMethod_)
                .build();

        List<CashFlow> otherLeg = new OvernightLeg(schedule, otherIndex_)
                .withNotionals(100.0)
                .withPaymentLag(paymentLag_)
                .withTelescopicValueDates(
                        telescopicValueDates_
                                && otherAveragingMethod_ == RateAveraging.Compound)
                .withAveragingMethod(otherAveragingMethod_)
                .build();

        OvernightIndexedCoupon lastBaseCoupon =
                (OvernightIndexedCoupon) baseLeg.get(baseLeg.size() - 1);
        OvernightIndexedCoupon lastOtherCoupon =
                (OvernightIndexedCoupon) otherLeg.get(otherLeg.size() - 1);

        Date lastPaymentDate = TimeUtils.max(baseLeg.get(baseLeg.size() - 1).date(),
                otherLeg.get(otherLeg.size() - 1).date());
        Date lastBaseFixingEndDate = baseIndex_.maturityDate(
                baseIndex_.valueDate(lastBaseCoupon.fixingDate()));
        Date lastOtherFixingEndDate = otherIndex_.maturityDate(
                otherIndex_.valueDate(lastOtherCoupon.fixingDate()));

        latestRelevantDate_ = TimeUtils.max(maturityDate_, lastPaymentDate);
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_, lastBaseFixingEndDate);
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_, lastOtherFixingEndDate);
        pillarDate_ = latestRelevantDate_;

        swap_ = new Swap(baseLeg, otherLeg);
        swap_.setPricingEngine(
                new DiscountingMultiLegSwapEngine(discountRelinkableHandle_));
    }
}
