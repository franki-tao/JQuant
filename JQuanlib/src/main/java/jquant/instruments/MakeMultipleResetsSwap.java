package jquant.instruments;

import jquant.Handle;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
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

import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Builder for {@link MultipleResetsSwap}, following QuantLib
 * {@code ql/instruments/makemultipleresetsswap.{hpp,cpp}}.
 *
 * <p>By default the fixed leg frequency matches the coupon period implied by the
 * index tenor and {@code resetsPerCoupon}. When no fixed rate is given, a
 * temporary swap is priced to derive the fair rate.
 */
public class MakeMultipleResetsSwap {
    private Period tenor_;
    private final IborIndex iborIndex_;
    private final int resetsPerCoupon_;

    private Double fixedRate_ = null;
    private Period forwardStart_ = new Period(0, TimeUnit.DAYS);
    private Integer settlementDays_ = null;
    private Date effectiveDate_ = new Date();
    private Date terminationDate_ = new Date();
    private Swap.Type type_ = Swap.Type.Payer;
    private double nominal_ = 1.0;
    private Frequency fixedFrequency_ = Frequency.NO_FREQUENCY;
    private DayCounter fixedDayCount_;
    private BusinessDayConvention fixedConvention_ = BusinessDayConvention.MODIFIED_FOLLOWING;
    private double spread_ = 0.0;
    private RateAveraging averagingMethod_ = RateAveraging.Compound;
    private PricingEngine engine_ = null;

    public MakeMultipleResetsSwap(Period tenor,
                                  IborIndex iborIndex,
                                  int resetsPerCoupon) {
        tenor_ = tenor;
        iborIndex_ = iborIndex;
        resetsPerCoupon_ = resetsPerCoupon;
        fixedDayCount_ = iborIndex.dayCounter();
    }

    public MultipleResetsSwap makeSwap() {
        Calendar cal = iborIndex_.fixingCalendar();
        BusinessDayConvention bdc = iborIndex_.businessDayConvention();

        QL_REQUIRE(TimeUtils.equals(effectiveDate_, new Date()) || settlementDays_ == null,
                "withEffectiveDate and withSettlementDays are mutually exclusive");

        Date startDate;
        if (TimeUtils.neq(effectiveDate_, new Date())) {
            startDate = effectiveDate_;
        } else {
            int settlDays = settlementDays_ != null
                    ? settlementDays_ : iborIndex_.fixingDays();
            Date refDate = Settings.instance.evaluationDate().Date();
            startDate = cal.advance(cal.adjust(refDate, BusinessDayConvention.FOLLOWING),
                    settlDays, TimeUnit.DAYS);
            startDate = cal.advance(startDate, forwardStart_,
                    forwardStart_.length() < 0
                            ? BusinessDayConvention.PRECEDING
                            : BusinessDayConvention.FOLLOWING);
        }

        // The end date is left unadjusted; the schedules adjust it with their
        // termination-date convention. Generating backwards from an adjusted
        // date would shift the other dates and add a start stub whenever the
        // maturity falls on a holiday.
        Date endDate = TimeUtils.neq(terminationDate_, new Date())
                ? terminationDate_ : startDate.add(tenor_);

        Period resetTenor = iborIndex_.tenor();
        Frequency fixedFreq = fixedFrequency_;
        if (fixedFreq == Frequency.NO_FREQUENCY) {
            Period couponTenor = new Period(resetsPerCoupon_ * resetTenor.length(),
                    resetTenor.units());
            fixedFreq = couponTenor.frequency();
        }

        Schedule fixedSchedule = new Schedule(startDate, endDate, new Period(fixedFreq),
                cal, fixedConvention_, fixedConvention_,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());

        Schedule fullResetSchedule = new Schedule(startDate, endDate, resetTenor,
                cal, bdc, bdc, DateGenerationRule.BACKWARD, false, new Date(), new Date());

        double usedFixedRate;
        if (fixedRate_ == null) {
            MultipleResetsSwap temp = new MultipleResetsSwap(type_, nominal_,
                    fixedSchedule, 0.0, fixedDayCount_, fullResetSchedule,
                    iborIndex_, resetsPerCoupon_, spread_, averagingMethod_,
                    Optional.empty(), 0, null);
            if (engine_ == null) {
                Handle<YieldTermStructure> disc = iborIndex_.forwardingTermStructure();
                QL_REQUIRE(!disc.empty(), "null term structure set to this instance of "
                        + iborIndex_.name());
                temp.setPricingEngine(new DiscountingMultiLegSwapEngine(disc));
            } else {
                temp.setPricingEngine(engine_);
            }
            usedFixedRate = temp.fairRate();
        } else {
            usedFixedRate = fixedRate_;
        }

        MultipleResetsSwap swap = new MultipleResetsSwap(type_, nominal_,
                fixedSchedule, usedFixedRate, fixedDayCount_, fullResetSchedule,
                iborIndex_, resetsPerCoupon_, spread_, averagingMethod_,
                Optional.empty(), 0, null);

        if (engine_ == null) {
            Handle<YieldTermStructure> disc = iborIndex_.forwardingTermStructure();
            if (!disc.empty())
                swap.setPricingEngine(new DiscountingMultiLegSwapEngine(disc));
        } else {
            swap.setPricingEngine(engine_);
        }

        return swap;
    }

    public MakeMultipleResetsSwap receiveFixed(boolean flag) {
        type_ = flag ? Swap.Type.Receiver : Swap.Type.Payer;
        return this;
    }

    public MakeMultipleResetsSwap withType(Swap.Type type) {
        type_ = type;
        return this;
    }

    public MakeMultipleResetsSwap withNominal(double n) {
        nominal_ = n;
        return this;
    }

    public MakeMultipleResetsSwap withFixedRate(double fixedRate) {
        fixedRate_ = fixedRate;
        return this;
    }

    public MakeMultipleResetsSwap withSettlementDays(int settlementDays) {
        settlementDays_ = settlementDays;
        return this;
    }

    public MakeMultipleResetsSwap withEffectiveDate(Date d) {
        effectiveDate_ = d;
        return this;
    }

    public MakeMultipleResetsSwap withTerminationDate(Date d) {
        terminationDate_ = d;
        if (TimeUtils.neq(d, new Date()))
            tenor_ = new Period();
        return this;
    }

    public MakeMultipleResetsSwap withForwardStart(Period fwdStart) {
        forwardStart_ = fwdStart;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegFrequency(Frequency f) {
        fixedFrequency_ = f;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegDayCount(DayCounter dc) {
        fixedDayCount_ = dc;
        return this;
    }

    public MakeMultipleResetsSwap withFixedLegConvention(BusinessDayConvention bdc) {
        fixedConvention_ = bdc;
        return this;
    }

    public MakeMultipleResetsSwap withFloatingLegSpread(double sp) {
        spread_ = sp;
        return this;
    }

    public MakeMultipleResetsSwap withAveragingMethod(RateAveraging m) {
        averagingMethod_ = m;
        return this;
    }

    public MakeMultipleResetsSwap withDiscountingTermStructure(
            Handle<YieldTermStructure> d) {
        engine_ = new DiscountingMultiLegSwapEngine(d);
        return this;
    }

    public MakeMultipleResetsSwap withPricingEngine(PricingEngine engine) {
        engine_ = engine;
        return this;
    }
}
