package jquant.instruments;

import jquant.Handle;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.indexes.Currencies;
import jquant.indexes.IborIndex;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Builder for {@link VanillaSwap}, following QuantLib
 * {@code ql/instruments/makevanillaswap.{hpp,cpp}}.
 *
 * <p>Only the currency-dependent defaults, schedule construction and pricing
 * engine wiring used by {@code test-suite/swaption.cpp} are ported; the
 * builder api mirrors the official one.
 */
public class MakeVanillaSwap {
    private final Period swapTenor_;
    private final IborIndex iborIndex_;

    private Double fixedRate_ = null;
    private Period forwardStart_ = new Period(0, TimeUnit.DAYS);
    private Integer settlementDays_ = null;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private Calendar fixedCalendar_;
    private Calendar floatCalendar_;
    private Calendar settlementCalendar_ = null;  // empty
    private Swap.Type type_ = Swap.Type.Payer;
    private double nominal_ = 1.0;
    private Period fixedTenor_ = new Period();
    private Period floatTenor_;
    private BusinessDayConvention fixedConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention fixedTerminationDateConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private BusinessDayConvention floatConvention_;
    private BusinessDayConvention floatTerminationDateConvention_;
    private DateGenerationRule fixedRule_ = DateGenerationRule.BACKWARD;
    private DateGenerationRule floatRule_ = DateGenerationRule.BACKWARD;
    private boolean fixedEndOfMonth_ = false;
    private boolean floatEndOfMonth_ = false;
    private Boolean maturityEndOfMonth_ = null;
    private Date fixedFirstDate_ = new Date();
    private Date fixedNextToLastDate_ = new Date();
    private Date floatFirstDate_ = new Date();
    private Date floatNextToLastDate_ = new Date();
    private double floatSpread_ = 0.0;
    private DayCounter fixedDayCount_ = null;
    private DayCounter floatDayCount_;
    private Optional<Boolean> useIndexedCoupons_ = Optional.empty();
    private Optional<BusinessDayConvention> paymentConvention_ = Optional.empty();
    private PricingEngine engine_ = null;

    public MakeVanillaSwap(Period swapTenor, IborIndex iborIndex) {
        swapTenor_ = swapTenor;
        iborIndex_ = iborIndex;
        fixedCalendar_ = iborIndex.fixingCalendar();
        floatCalendar_ = iborIndex.fixingCalendar();
        floatTenor_ = iborIndex.tenor();
        floatConvention_ = iborIndex.businessDayConvention();
        floatTerminationDateConvention_ = iborIndex.businessDayConvention();
        floatDayCount_ = iborIndex.dayCounter();
    }

    public MakeVanillaSwap(Period swapTenor,
                           IborIndex iborIndex,
                           double fixedRate,
                           Period forwardStart) {
        this(swapTenor, iborIndex);
        withFixedRate(fixedRate);
        withForwardStart(forwardStart);
    }

    /** Builds the swap, as the official conversion operator. */
    public VanillaSwap makeSwap() {
        QL_REQUIRE(TimeUtils.neq(effectiveDate_, new Date()) || settlementDays_ == null,
                "cannot set both an explicit effective date and settlement days; "
                        + "use one or the other");

        Date startDate;
        if (TimeUtils.neq(effectiveDate_, new Date())) {
            startDate = effectiveDate_;
        } else {
            Date refDate = Settings.instance.evaluationDate().Date();
            Date spotDate;
            if (settlementDays_ == null) {
                // the spot date is defined by the index, so the reference
                // date must be adjusted on the index fixing calendar (not the
                // float/payment calendar) to keep the pre-adjust consistent
                // with valueDate's own fixing-calendar advance.
                refDate = iborIndex_.fixingCalendar().adjust(refDate,
                        BusinessDayConvention.FOLLOWING);
                spotDate = iborIndex_.valueDate(refDate);
            } else {
                // settlement days are counted from the actual evaluation
                // date, even when it is not a business day (see issue #753)
                Calendar settlementCalendar = (settlementCalendar_ == null
                        || settlementCalendar_.empty()) ? floatCalendar_ : settlementCalendar_;
                spotDate = settlementCalendar.advance(refDate,
                        settlementDays_ * 1, TimeUnit.DAYS);
            }
            startDate = spotDate.add(forwardStart_);
            if (forwardStart_.length() < 0)
                startDate = floatCalendar_.adjust(startDate, BusinessDayConvention.PRECEDING);
            else if (forwardStart_.length() > 0)
                startDate = floatCalendar_.adjust(startDate, BusinessDayConvention.FOLLOWING);
        }

        Date endDate = terminationDate_;
        if (TimeUtils.equals(endDate, new Date())) {
            endDate = startDate.add(swapTenor_);
            boolean maturityEndOfMonth = maturityEndOfMonth_ != null
                    ? maturityEndOfMonth_ : floatEndOfMonth_;
            if (maturityEndOfMonth && TimeUtils.allowsEndOfMonth(swapTenor_)
                    && floatCalendar_.isEndOfMonth(startDate))
                endDate = floatCalendar_.endOfMonth(endDate);
        }

        Period fixedTenor = null;
        if (fixedTenor_.length() != 0 || fixedTenor_.units() != TimeUnit.DAYS) {
            fixedTenor = fixedTenor_;
        } else {
            // When swapTenor_ was cleared by withTerminationDate(),
            // use the actual swap length for currency-dependent inference.
            Period tenor = swapTenor_;
            if ((tenor.length() == 0 && tenor.units() == TimeUnit.DAYS)
                    && TimeUtils.greater(endDate, startDate)) {
                // approximate months = days * 12/365, rounded (182 = 365/2)
                int months = (12 * (int) TimeUtils.substract(endDate, startDate) + 182) / 365;
                tenor = new Period(months, TimeUnit.MONTHS);
            }
            // Only the currencies whose Java objects exist are handled; the
            // official code also covers USD/CHF/SEK/GBP/JPY/AUD/HKD.
            if (iborIndex_.currency().equals(Currencies.eur()))
                fixedTenor = new Period(1, TimeUnit.YEARS);
            else if (iborIndex_.currency().equals(Currencies.usd()))
                fixedTenor = new Period(1, TimeUnit.YEARS);
            else
                QL_FAIL("unknown fixed leg default tenor for " + iborIndex_.currency());
        }

        Schedule fixedSchedule = new Schedule(startDate, endDate, fixedTenor,
                fixedCalendar_, fixedConvention_, fixedTerminationDateConvention_,
                fixedRule_, fixedEndOfMonth_, fixedFirstDate_, fixedNextToLastDate_);
        Schedule floatSchedule = new Schedule(startDate, endDate, floatTenor_,
                floatCalendar_, floatConvention_, floatTerminationDateConvention_,
                floatRule_, floatEndOfMonth_, floatFirstDate_, floatNextToLastDate_);

        DayCounter fixedDayCount = null;
        if (fixedDayCount_ != null) {
            fixedDayCount = fixedDayCount_;
        } else {
            if (iborIndex_.currency().equals(Currencies.usd()))
                fixedDayCount = new Actual360();
            else if (iborIndex_.currency().equals(Currencies.eur()))
                fixedDayCount = new Thirty360(Thirty360.Convention.BondBasis);
            else
                QL_FAIL("unknown fixed leg day counter for " + iborIndex_.currency());
        }

        double usedFixedRate;
        if (fixedRate_ == null) {
            VanillaSwap temp = new VanillaSwap(mapType(type_), 100.00, fixedSchedule,
                    0.0, fixedDayCount, floatSchedule, iborIndex_, floatSpread_,
                    floatDayCount_, paymentConvention_, useIndexedCoupons_);
            if (engine_ == null) {
                Handle<YieldTermStructure> disc = iborIndex_.forwardingTermStructure();
                QL_REQUIRE(!disc.empty(), "null term structure set to this instance of "
                        + iborIndex_.name());
                temp.setPricingEngine(new DiscountingSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        } else {
            usedFixedRate = fixedRate_;
        }

        VanillaSwap swap = new VanillaSwap(mapType(type_), nominal_, fixedSchedule,
                usedFixedRate, fixedDayCount, floatSchedule, iborIndex_, floatSpread_,
                floatDayCount_, paymentConvention_, useIndexedCoupons_);

        if (engine_ == null) {
            Handle<YieldTermStructure> disc = iborIndex_.forwardingTermStructure();
            swap.setPricingEngine(new DiscountingSwapEngine(disc));
        } else {
            swap.setPricingEngine(engine_);
        }
        return swap;
    }

    private static VanillaSwap.Type mapType(Swap.Type type) {
        return type == Swap.Type.Payer ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver;
    }

    public MakeVanillaSwap receiveFixed(boolean flag) {
        type_ = flag ? Swap.Type.Receiver : Swap.Type.Payer;
        return this;
    }

    public MakeVanillaSwap withType(Swap.Type type) {
        type_ = type;
        return this;
    }

    public MakeVanillaSwap withNominal(double n) {
        nominal_ = n;
        return this;
    }

    public MakeVanillaSwap withFixedRate(double k) {
        fixedRate_ = k;
        return this;
    }

    public MakeVanillaSwap withForwardStart(Period f) {
        forwardStart_ = f;
        return this;
    }

    public MakeVanillaSwap withSettlementDays(int settlementDays) {
        settlementDays_ = settlementDays;
        return this;
    }

    public MakeVanillaSwap withSettlementCalendar(Calendar cal) {
        settlementCalendar_ = cal;
        return this;
    }

    public MakeVanillaSwap withEffectiveDate(Date effectiveDate) {
        effectiveDate_ = effectiveDate;
        return this;
    }

    public MakeVanillaSwap withTerminationDate(Date terminationDate) {
        terminationDate_ = terminationDate;
        return this;
    }

    public MakeVanillaSwap withRule(DateGenerationRule r) {
        fixedRule_ = r;
        floatRule_ = r;
        return this;
    }

    public MakeVanillaSwap withPaymentConvention(BusinessDayConvention bdc) {
        paymentConvention_ = Optional.of(bdc);
        return this;
    }

    public MakeVanillaSwap withDiscountingTermStructure(Handle<YieldTermStructure> d) {
        engine_ = new DiscountingSwapEngine(d);
        return this;
    }

    public MakeVanillaSwap withPricingEngine(PricingEngine engine) {
        engine_ = engine;
        return this;
    }

    public MakeVanillaSwap withFixedLegTenor(Period t) {
        fixedTenor_ = t;
        return this;
    }

    public MakeVanillaSwap withFixedLegCalendar(Calendar cal) {
        fixedCalendar_ = cal;
        return this;
    }

    public MakeVanillaSwap withFixedLegConvention(BusinessDayConvention bdc) {
        fixedConvention_ = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegTerminationDateConvention(BusinessDayConvention bdc) {
        fixedTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeVanillaSwap withFixedLegRule(DateGenerationRule r) {
        fixedRule_ = r;
        return this;
    }

    public MakeVanillaSwap withFixedLegEndOfMonth(boolean flag) {
        fixedEndOfMonth_ = flag;
        return this;
    }

    public MakeVanillaSwap withFixedLegFirstDate(Date d) {
        fixedFirstDate_ = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegNextToLastDate(Date d) {
        fixedNextToLastDate_ = d;
        return this;
    }

    public MakeVanillaSwap withFixedLegDayCount(DayCounter dc) {
        fixedDayCount_ = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTenor(Period t) {
        floatTenor_ = t;
        return this;
    }

    public MakeVanillaSwap withFloatingLegCalendar(Calendar cal) {
        floatCalendar_ = cal;
        return this;
    }

    public MakeVanillaSwap withFloatingLegConvention(BusinessDayConvention bdc) {
        floatConvention_ = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegTerminationDateConvention(BusinessDayConvention bdc) {
        floatTerminationDateConvention_ = bdc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegRule(DateGenerationRule r) {
        floatRule_ = r;
        return this;
    }

    public MakeVanillaSwap withFloatingLegEndOfMonth(boolean flag) {
        floatEndOfMonth_ = flag;
        return this;
    }

    public MakeVanillaSwap withMaturityEndOfMonth(boolean flag) {
        maturityEndOfMonth_ = flag;
        return this;
    }

    public MakeVanillaSwap withFloatingLegFirstDate(Date d) {
        floatFirstDate_ = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegNextToLastDate(Date d) {
        floatNextToLastDate_ = d;
        return this;
    }

    public MakeVanillaSwap withFloatingLegDayCount(DayCounter dc) {
        floatDayCount_ = dc;
        return this;
    }

    public MakeVanillaSwap withFloatingLegSpread(double sp) {
        floatSpread_ = sp;
        return this;
    }

    public MakeVanillaSwap withIndexedCoupons(Optional<Boolean> b) {
        useIndexedCoupons_ = b;
        return this;
    }

    public MakeVanillaSwap withAtParCoupons(boolean b) {
        useIndexedCoupons_ = Optional.of(!b);
        return this;
    }
}
