package jquant.termstructures.yield;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.cashflows.OvernightIndexedCoupon;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
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
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Rate helper for bootstrapping over overnight-ibor basis swaps.
 *
 * <p>The swap pays overnight + basis and receives ibor. As a default, the
 * helper is used to bootstrap the forecast curve for the ibor index; the
 * overnight index will need an existing forecast curve. If
 * {@code bootstrapBaseCurve} is set to true, the helper bootstraps the
 * forecast curve for the overnight index instead.
 *
 * <p>An exogenous discount curve can be passed; if not, the curve being
 * bootstrapped is also used for discounting.
 *
 * <p>Port of QuantLib's {@code OvernightIborBasisSwapRateHelper} in
 * {@code ql/experimental/termstructures/basisswapratehelpers.cpp}. The
 * stub-index selection is not ported (it is not available in JQuant).
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/experimental/termstructures/basisswapratehelpers.cpp
 */
public class OvernightIborBasisSwapRateHelper extends RateHelper {
    private final Period tenor_;
    private final int settlementDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    private final boolean endOfMonth_;
    private final OvernightIndex baseIndex_;
    private final IborIndex otherIndex_;
    private final Handle<YieldTermStructure> discountHandle_;
    private final boolean bootstrapBaseCurve_;
    private final int paymentLag_;
    private final Frequency overnightPaymentFrequency_;
    private final Optional<Boolean> useIndexedCoupons_;
    private final DateGenerationRule rule_;
    private final RateAveraging averagingMethod_;
    private final boolean telescopicValueDates_;
    private final boolean basisOnIborLeg_;

    private Swap swap_;

    private final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>();
    private final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<>();

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, new Handle<>(null, false), false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                0, null, null, DateGenerationRule.BACKWARD,
                RateAveraging.Compound, false, false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve,
                                            int paymentLag,
                                            Frequency overnightPaymentFrequency) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, overnightPaymentFrequency, null,
                DateGenerationRule.BACKWARD, RateAveraging.Compound, false, false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve,
                                            int paymentLag,
                                            Frequency overnightPaymentFrequency,
                                            Boolean useIndexedCoupons) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, overnightPaymentFrequency, useIndexedCoupons,
                DateGenerationRule.BACKWARD);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve,
                                            int paymentLag,
                                            Frequency overnightPaymentFrequency,
                                            Boolean useIndexedCoupons,
                                            DateGenerationRule rule) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, overnightPaymentFrequency, useIndexedCoupons,
                rule, RateAveraging.Compound, false, false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve,
                                            int paymentLag,
                                            Frequency overnightPaymentFrequency,
                                            Boolean useIndexedCoupons,
                                            DateGenerationRule rule,
                                            RateAveraging averagingMethod,
                                            boolean telescopicValueDates) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                paymentLag, overnightPaymentFrequency, useIndexedCoupons,
                rule, averagingMethod, telescopicValueDates, false);
    }

    public OvernightIborBasisSwapRateHelper(Handle<Quote> basis,
                                            Period tenor,
                                            int settlementDays,
                                            Calendar calendar,
                                            BusinessDayConvention convention,
                                            boolean endOfMonth,
                                            OvernightIndex baseIndex,
                                            IborIndex otherIndex,
                                            Handle<YieldTermStructure> discountHandle,
                                            boolean bootstrapBaseCurve,
                                            int paymentLag,
                                            Frequency overnightPaymentFrequency,
                                            Boolean useIndexedCoupons,
                                            DateGenerationRule rule,
                                            RateAveraging averagingMethod,
                                            boolean telescopicValueDates,
                                            boolean basisOnIborLeg) {
        super(basis);
        QL_REQUIRE(baseIndex != null, "null base overnight index");
        QL_REQUIRE(otherIndex != null, "null other ibor index");

        tenor_ = tenor;
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        discountHandle_ = discountHandle != null
                ? discountHandle : new Handle<>(null, false);
        bootstrapBaseCurve_ = bootstrapBaseCurve;
        paymentLag_ = paymentLag;
        overnightPaymentFrequency_ = overnightPaymentFrequency;
        useIndexedCoupons_ = useIndexedCoupons != null
                ? Optional.of(useIndexedCoupons) : Optional.empty();
        rule_ = rule != null ? rule : DateGenerationRule.BACKWARD;
        averagingMethod_ = averagingMethod != null ? averagingMethod : RateAveraging.Compound;
        telescopicValueDates_ = telescopicValueDates;
        basisOnIborLeg_ = basisOnIborLeg;

        // We need to clone the index whose forecast curve we want to
        // bootstrap and copy the other one. The relinkable handle is empty
        // at this point, so the clone does not register with it; it is
        // linked without observation in setTermStructure.
        if (bootstrapBaseCurve_) {
            baseIndex_ = (OvernightIndex) baseIndex.clone(termStructureHandle_);
            otherIndex_ = otherIndex;
        } else {
            baseIndex_ = baseIndex;
            otherIndex_ = otherIndex.clone(termStructureHandle_);
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
        // do not set the relinkable handle as an observer -
        // force recalculation when needed---the index is not lazy
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
        int leg = basisOnIborLeg_ ? 1 : 0;
        return -(swap_.NPV() / swap_.legBPS(leg)) * 1.0e-4;
    }

    private void initializeDates() {
        Date today = Settings.instance.evaluationDate().Date();
        Date earliestDate = calendar_.advance(today, settlementDays_,
                TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        earliestDate_ = earliestDate;
        // unadjusted, to avoid a spurious stub
        Date terminationDate = earliestDate.add(tenor_);

        Period overnightTenor = overnightPaymentFrequency_ != null
                ? new Period(overnightPaymentFrequency_)
                : otherIndex_.tenor();
        Schedule overnightSchedule = new MakeSchedule()
                .from(earliestDate)
                .to(terminationDate)
                .withTenor(overnightTenor)
                .withCalendar(calendar_)
                .withConvention(convention_)
                .endOfMonth(endOfMonth_)
                .withRule(rule_)
                .toSchedule();

        List<CashFlow> baseLeg = new OvernightLeg(overnightSchedule, baseIndex_)
                .withNotionals(100.0)
                .withPaymentLag(paymentLag_)
                .withTelescopicValueDates(
                        telescopicValueDates_ && averagingMethod_ == RateAveraging.Compound)
                .withAveragingMethod(averagingMethod_)
                .build();
        OvernightIndexedCoupon lastBaseCoupon =
                (OvernightIndexedCoupon) baseLeg.get(baseLeg.size() - 1);

        // an ibor leg pays one coupon per fixing, so its payment frequency
        // is the tenor of its own index
        Schedule iborSchedule = new MakeSchedule()
                .from(earliestDate)
                .to(terminationDate)
                .withTenor(otherIndex_.tenor())
                .withCalendar(calendar_)
                .withConvention(convention_)
                .endOfMonth(endOfMonth_)
                .withRule(rule_)
                .toSchedule();

        List<CashFlow> otherLeg = new IborLeg(iborSchedule, otherIndex_)
                .withNotionals(100.0)
                .withPaymentLag(paymentLag_)
                .withIndexedCoupons(useIndexedCoupons_)
                .leg();
        IborCoupon lastOtherCoupon = (IborCoupon) otherLeg.get(otherLeg.size() - 1);

        maturityDate_ = TimeUtils.max(overnightSchedule.endDate(), iborSchedule.endDate());

        // the payment lag can push the last payment past the maturity date,
        // in which case the discount curve is needed up to that date
        Date lastPaymentDate = TimeUtils.max(baseLeg.get(baseLeg.size() - 1).date(),
                otherLeg.get(otherLeg.size() - 1).date());
        Date lastBaseFixingEndDate = baseIndex_.maturityDate(
                baseIndex_.valueDate(lastBaseCoupon.fixingDate()));

        latestRelevantDate_ = TimeUtils.max(maturityDate_, lastPaymentDate);
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_, lastBaseFixingEndDate);
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_,
                lastOtherCoupon.fixingEndDate());
        pillarDate_ = latestRelevantDate_;

        swap_ = new Swap(baseLeg, otherLeg);
        swap_.setPricingEngine(
                new DiscountingMultiLegSwapEngine(discountRelinkableHandle_));
    }
}
