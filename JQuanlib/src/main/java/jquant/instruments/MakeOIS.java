package jquant.instruments;

import jquant.Handle;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.RateAveraging;
import jquant.indexes.OvernightIndex;
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

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Builder for {@link OvernightIndexedSwap}, following QuantLib
 * {@code ql/instruments/makeois.{hpp,cpp}}.
 */
public class MakeOIS {
    private final Period swapTenor_;
    private final OvernightIndex overnightIndex_;

    private Double fixedRate_ = null;
    private Period forwardStart_ = new Period(0, TimeUnit.DAYS);
    private Integer settlementDays_ = null;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private Calendar fixedCalendar_;
    private Calendar overnightCalendar_;
    private Calendar settlementCalendar_ = null;
    private Frequency fixedPaymentFrequency_ = Frequency.ANNUAL;
    private Frequency overnightPaymentFrequency_ = Frequency.ANNUAL;
    private Calendar paymentCalendar_ = null;
    private BusinessDayConvention paymentAdjustment_ = BusinessDayConvention.FOLLOWING;
    private int paymentLag_ = 0;
    private BusinessDayConvention fixedConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention fixedTerminationDateConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention overnightConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention overnightTerminationDateConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private DateGenerationRule fixedRule_ = DateGenerationRule.BACKWARD;
    private DateGenerationRule overnightRule_ = DateGenerationRule.BACKWARD;
    private boolean fixedEndOfMonth_ = false;
    private boolean overnightEndOfMonth_ = false;
    private boolean isDefaultEOM_ = true;
    private Boolean maturityEndOfMonth_ = null;
    private Swap.Type type_ = Swap.Type.Payer;
    private double nominal_ = 1.0;
    private double overnightSpread_ = 0.0;
    private DayCounter fixedDayCount_ = null;
    private PricingEngine engine_ = null;
    private boolean telescopicValueDates_ = false;
    private RateAveraging averagingMethod_ = RateAveraging.Compound;

    public MakeOIS(Period swapTenor, OvernightIndex overnightIndex) {
        swapTenor_ = swapTenor;
        overnightIndex_ = overnightIndex;
        fixedCalendar_ = overnightIndex.fixingCalendar();
        overnightCalendar_ = overnightIndex.fixingCalendar();
        fixedDayCount_ = overnightIndex.dayCounter();
    }

    public MakeOIS(Period swapTenor,
                   OvernightIndex overnightIndex,
                   double fixedRate,
                   Period forwardStart) {
        this(swapTenor, overnightIndex);
        withFixedRate(fixedRate);
        withForwardStart(forwardStart);
    }

    public OvernightIndexedSwap makeSwap() {
        QL_REQUIRE(TimeUtils.neq(effectiveDate_, new Date()) || settlementDays_ == null,
                "cannot set both an explicit effective date and settlement days; "
                        + "use one or the other");

        Date startDate;
        if (TimeUtils.neq(effectiveDate_, new Date())) {
            startDate = effectiveDate_;
        } else {
            // settlement days: override if set, else fallback to default by
            // index name (Sonia 0, Corra 1, otherwise 2)
            int settlementDays = settlementDays_ != null ? settlementDays_ : 2;

            Date refDate = Settings.instance.evaluationDate().Date();
            // settlement days are counted from the actual evaluation date,
            // even when it is not a business day (see issue #753)
            Calendar settlementCalendar = (settlementCalendar_ == null
                    || settlementCalendar_.empty()) ? overnightCalendar_ : settlementCalendar_;
            Date spotDate = settlementCalendar.advance(refDate, settlementDays, TimeUnit.DAYS);
            startDate = spotDate.add(forwardStart_);
            if (forwardStart_.length() < 0)
                startDate = overnightCalendar_.adjust(startDate, BusinessDayConvention.PRECEDING);
            else
                startDate = overnightCalendar_.adjust(startDate, BusinessDayConvention.FOLLOWING);
        }

        boolean fixedEndOfMonth;
        boolean overnightEndOfMonth;
        boolean maturityEndOfMonth;
        if (isDefaultEOM_) {
            fixedEndOfMonth = overnightEndOfMonth = maturityEndOfMonth =
                    overnightCalendar_.isEndOfMonth(startDate);
        } else {
            fixedEndOfMonth = fixedEndOfMonth_;
            overnightEndOfMonth = overnightEndOfMonth_;
            maturityEndOfMonth = maturityEndOfMonth_ != null
                    ? maturityEndOfMonth_ : overnightEndOfMonth_;
        }

        Date endDate = terminationDate_;
        if (TimeUtils.equals(endDate, new Date())) {
            endDate = startDate.add(swapTenor_);
            if (maturityEndOfMonth && TimeUtils.allowsEndOfMonth(swapTenor_)
                    && overnightCalendar_.isEndOfMonth(startDate))
                endDate = overnightCalendar_.endOfMonth(endDate);
        }

        Frequency fixedPaymentFrequency;
        DateGenerationRule fixedRule;
        if (fixedPaymentFrequency_ == Frequency.ONCE || fixedRule_ == DateGenerationRule.ZERO) {
            fixedPaymentFrequency = Frequency.ONCE;
            fixedRule = DateGenerationRule.ZERO;
        } else {
            fixedPaymentFrequency = fixedPaymentFrequency_;
            fixedRule = fixedRule_;
        }
        Frequency overnightPaymentFrequency;
        DateGenerationRule overnightRule;
        if (overnightPaymentFrequency_ == Frequency.ONCE
                || overnightRule_ == DateGenerationRule.ZERO) {
            overnightPaymentFrequency = Frequency.ONCE;
            overnightRule = DateGenerationRule.ZERO;
        } else {
            overnightPaymentFrequency = overnightPaymentFrequency_;
            overnightRule = overnightRule_;
        }

        Schedule fixedSchedule = new Schedule(startDate, endDate,
                new Period(fixedPaymentFrequency), fixedCalendar_,
                fixedConvention_, fixedTerminationDateConvention_,
                fixedRule, fixedEndOfMonth, new Date(), new Date());
        Schedule overnightSchedule = new Schedule(startDate, endDate,
                new Period(overnightPaymentFrequency), overnightCalendar_,
                overnightConvention_, overnightTerminationDateConvention_,
                overnightRule, overnightEndOfMonth, new Date(), new Date());

        double usedFixedRate;
        if (fixedRate_ == null) {
            OvernightIndexedSwap temp = new OvernightIndexedSwap(type_, nominal_,
                    fixedSchedule, 0.0, fixedDayCount_, overnightSchedule,
                    overnightIndex_, overnightSpread_, paymentLag_, paymentAdjustment_,
                    paymentCalendar_, telescopicValueDates_, averagingMethod_);
            if (engine_ == null) {
                Handle<YieldTermStructure> disc = overnightIndex_.forwardingTermStructure();
                QL_REQUIRE(!disc.empty(), "null term structure set to this instance of "
                        + overnightIndex_.name());
                temp.setPricingEngine(new DiscountingSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        } else {
            usedFixedRate = fixedRate_;
        }

        OvernightIndexedSwap ois = new OvernightIndexedSwap(type_, nominal_,
                fixedSchedule, usedFixedRate, fixedDayCount_, overnightSchedule,
                overnightIndex_, overnightSpread_, paymentLag_, paymentAdjustment_,
                paymentCalendar_, telescopicValueDates_, averagingMethod_);

        if (engine_ == null) {
            Handle<YieldTermStructure> disc = overnightIndex_.forwardingTermStructure();
            ois.setPricingEngine(new DiscountingSwapEngine(disc));
        } else {
            ois.setPricingEngine(engine_);
        }
        return ois;
    }

    public MakeOIS receiveFixed(boolean flag) {
        type_ = flag ? Swap.Type.Receiver : Swap.Type.Payer;
        return this;
    }

    public MakeOIS withType(Swap.Type type) {
        type_ = type;
        return this;
    }

    public MakeOIS withNominal(double n) {
        nominal_ = n;
        return this;
    }

    public MakeOIS withFixedRate(double k) {
        fixedRate_ = k;
        return this;
    }

    public MakeOIS withForwardStart(Period f) {
        forwardStart_ = f;
        return this;
    }

    public MakeOIS withSettlementDays(int settlementDays) {
        settlementDays_ = settlementDays;
        return this;
    }

    public MakeOIS withSettlementCalendar(Calendar cal) {
        settlementCalendar_ = cal;
        return this;
    }

    public MakeOIS withEffectiveDate(Date effectiveDate) {
        effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeOIS withTerminationDate(Date terminationDate) {
        terminationDate_ = terminationDate;
        return this;
    }

    public MakeOIS withRule(DateGenerationRule r) {
        fixedRule_ = r;
        overnightRule_ = r;
        return this;
    }

    public MakeOIS withFixedLegRule(DateGenerationRule r) {
        fixedRule_ = r;
        return this;
    }

    public MakeOIS withOvernightLegRule(DateGenerationRule r) {
        overnightRule_ = r;
        return this;
    }

    public MakeOIS withPaymentFrequency(Frequency f) {
        fixedPaymentFrequency_ = f;
        overnightPaymentFrequency_ = f;
        return this;
    }

    public MakeOIS withFixedLegPaymentFrequency(Frequency f) {
        fixedPaymentFrequency_ = f;
        return this;
    }

    public MakeOIS withOvernightLegPaymentFrequency(Frequency f) {
        overnightPaymentFrequency_ = f;
        return this;
    }

    public MakeOIS withPaymentAdjustment(BusinessDayConvention convention) {
        paymentAdjustment_ = convention;
        return this;
    }

    public MakeOIS withPaymentLag(int lag) {
        paymentLag_ = lag;
        return this;
    }

    public MakeOIS withPaymentCalendar(Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public MakeOIS withCalendar(Calendar cal) {
        fixedCalendar_ = cal;
        overnightCalendar_ = cal;
        return this;
    }

    public MakeOIS withFixedLegCalendar(Calendar cal) {
        fixedCalendar_ = cal;
        return this;
    }

    public MakeOIS withOvernightLegCalendar(Calendar cal) {
        overnightCalendar_ = cal;
        return this;
    }

    public MakeOIS withConvention(BusinessDayConvention bdc) {
        fixedConvention_ = bdc;
        overnightConvention_ = bdc;
        return this;
    }

    public MakeOIS withFixedLegConvention(BusinessDayConvention bdc) {
        fixedConvention_ = bdc;
        return this;
    }

    public MakeOIS withOvernightLegConvention(BusinessDayConvention bdc) {
        overnightConvention_ = bdc;
        return this;
    }

    public MakeOIS withTerminationDateConvention(BusinessDayConvention bdc) {
        fixedTerminationDateConvention_ = bdc;
        overnightTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeOIS withFixedLegTerminationDateConvention(BusinessDayConvention bdc) {
        fixedTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeOIS withOvernightLegTerminationDateConvention(BusinessDayConvention bdc) {
        overnightTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeOIS withEndOfMonth(boolean flag) {
        isDefaultEOM_ = false;
        fixedEndOfMonth_ = flag;
        overnightEndOfMonth_ = flag;
        return this;
    }

    public MakeOIS withFixedLegEndOfMonth(boolean flag) {
        isDefaultEOM_ = false;
        fixedEndOfMonth_ = flag;
        return this;
    }

    public MakeOIS withOvernightLegEndOfMonth(boolean flag) {
        isDefaultEOM_ = false;
        overnightEndOfMonth_ = flag;
        return this;
    }

    public MakeOIS withMaturityEndOfMonth(boolean flag) {
        maturityEndOfMonth_ = flag;
        return this;
    }

    public MakeOIS withFixedLegDayCount(DayCounter dc) {
        fixedDayCount_ = dc;
        return this;
    }

    public MakeOIS withOvernightLegSpread(double sp) {
        overnightSpread_ = sp;
        return this;
    }

    public MakeOIS withDiscountingTermStructure(Handle<YieldTermStructure> d) {
        engine_ = new DiscountingSwapEngine(d);
        return this;
    }

    public MakeOIS withTelescopicValueDates(boolean telescopicValueDates) {
        telescopicValueDates_ = telescopicValueDates;
        return this;
    }

    public MakeOIS withAveragingMethod(RateAveraging averagingMethod) {
        averagingMethod_ = averagingMethod;
        return this;
    }

    public MakeOIS withPricingEngine(PricingEngine engine) {
        engine_ = engine;
        return this;
    }
}
