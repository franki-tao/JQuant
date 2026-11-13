package jquant.termstructures.yield;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.instruments.Swap;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.MakeSchedule;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Rate helper for bootstrapping over ibor-ibor basis swaps.
 *
 * <p>The swap pays {@code baseIndex + basis} and receives {@code otherIndex}.
 * It can bootstrap either index; the other one must already have a forecast
 * curve. An exogenous discount curve is required.
 *
 * <p>Port of QuantLib's {@code IborIborBasisSwapRateHelper} in
 * {@code ql/experimental/termstructures/basisswapratehelpers.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/experimental/termstructures/basisswapratehelpers.cpp
 */
public class IborIborBasisSwapRateHelper extends RateHelper {
    private final Period tenor_;
    private final int settlementDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    private final boolean endOfMonth_;
    private final IborIndex baseIndex_;
    private final IborIndex otherIndex_;
    private final Handle<YieldTermStructure> discountHandle_;
    private final boolean bootstrapBaseCurve_;
    private final Optional<Boolean> useIndexedCoupons_;
    private final DateGenerationRule rule_;
    private final int paymentLag_;
    private final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>();
    private Swap swap_;

    public IborIborBasisSwapRateHelper(Handle<Quote> basis,
                                       Period tenor,
                                       int settlementDays,
                                       Calendar calendar,
                                       BusinessDayConvention convention,
                                       boolean endOfMonth,
                                       IborIndex baseIndex,
                                       IborIndex otherIndex,
                                       Handle<YieldTermStructure> discountHandle,
                                       boolean bootstrapBaseCurve) {
        this(basis, tenor, settlementDays, calendar, convention, endOfMonth,
                baseIndex, otherIndex, discountHandle, bootstrapBaseCurve,
                Optional.empty(), DateGenerationRule.BACKWARD, 0);
    }

    public IborIborBasisSwapRateHelper(Handle<Quote> basis,
                                       Period tenor,
                                       int settlementDays,
                                       Calendar calendar,
                                       BusinessDayConvention convention,
                                       boolean endOfMonth,
                                       IborIndex baseIndex,
                                       IborIndex otherIndex,
                                       Handle<YieldTermStructure> discountHandle,
                                       boolean bootstrapBaseCurve,
                                       Optional<Boolean> useIndexedCoupons,
                                       DateGenerationRule rule,
                                       int paymentLag) {
        super(basis);
        QL_REQUIRE(baseIndex != null, "null base ibor index");
        QL_REQUIRE(otherIndex != null, "null other ibor index");
        tenor_ = tenor;
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        discountHandle_ = discountHandle != null
                ? discountHandle : new Handle<>(null, false);
        bootstrapBaseCurve_ = bootstrapBaseCurve;
        useIndexedCoupons_ = useIndexedCoupons != null
                ? useIndexedCoupons : Optional.empty();
        rule_ = rule != null ? rule : DateGenerationRule.BACKWARD;
        paymentLag_ = paymentLag;

        // Clone the index whose forecast curve we want to bootstrap and copy
        // the other one.  The relinkable handle is empty at this point, so the
        // clone does not register with it; it is linked without observation
        // in setTermStructure.
        if (bootstrapBaseCurve_) {
            baseIndex_ = baseIndex.clone(termStructureHandle_);
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

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        boolean observer = false;
        termStructureHandle_.linkTo(termStructure, observer);
    }

    @Override
    public double impliedQuote() {
        swap_.deepUpdate();
        return -(swap_.NPV() / swap_.legBPS(0)) * 1.0e-4;
    }

    private void initializeDates() {
        Date today = Settings.instance.evaluationDate().Date();
        Date start = calendar_.advance(today, settlementDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        // unadjusted, to avoid a spurious stub
        Date terminationDate = start.add(tenor_);

        Schedule baseSchedule = new MakeSchedule()
                .from(start)
                .to(terminationDate)
                .withTenor(baseIndex_.tenor())
                .withCalendar(calendar_)
                .withConvention(convention_)
                .endOfMonth(endOfMonth_)
                .withRule(rule_)
                .toSchedule();
        List<CashFlow> baseLeg = new IborLeg(baseSchedule, baseIndex_)
                .withNotionals(100.0)
                .withPaymentDayCounter(baseIndex_.dayCounter())
                .withPaymentAdjustment(convention_)
                .withPaymentCalendar(calendar_)
                .withPaymentLag(paymentLag_)
                .withIndexedCoupons(useIndexedCoupons_)
                .leg();
        IborCoupon lastBaseCoupon = (IborCoupon) baseLeg.get(baseLeg.size() - 1);

        Schedule otherSchedule = new MakeSchedule()
                .from(start)
                .to(terminationDate)
                .withTenor(otherIndex_.tenor())
                .withCalendar(calendar_)
                .withConvention(convention_)
                .endOfMonth(endOfMonth_)
                .withRule(rule_)
                .toSchedule();
        List<CashFlow> otherLeg = new IborLeg(otherSchedule, otherIndex_)
                .withNotionals(100.0)
                .withPaymentDayCounter(otherIndex_.dayCounter())
                .withPaymentAdjustment(convention_)
                .withPaymentCalendar(calendar_)
                .withPaymentLag(paymentLag_)
                .withIndexedCoupons(useIndexedCoupons_)
                .leg();
        IborCoupon lastOtherCoupon = (IborCoupon) otherLeg.get(otherLeg.size() - 1);

        maturityDate_ = TimeUtils.max(baseSchedule.endDate(), otherSchedule.endDate());

        Date lastPaymentDate = TimeUtils.max(baseLeg.get(baseLeg.size() - 1).date(),
                otherLeg.get(otherLeg.size() - 1).date());
        latestRelevantDate_ = TimeUtils.max(maturityDate_, lastPaymentDate);
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_, lastBaseCoupon.fixingEndDate());
        latestRelevantDate_ = TimeUtils.max(latestRelevantDate_, lastOtherCoupon.fixingEndDate());
        earliestDate_ = start;
        pillarDate_ = latestRelevantDate_;

        swap_ = new Swap(baseLeg, otherLeg);
        swap_.setPricingEngine(new DiscountingMultiLegSwapEngine(discountHandle_));
    }
}
