package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.indexes.IborIndex;
import jquant.instruments.VanillaSwap;
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

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for swap-rate indexes. The fixing is the fair rate of the
 * underlying vanilla swap.
 *
 * <p>This is ported only as far as {@code SwapRateHelper} needs it; the
 * overnight-indexed-swap subclass is not ported.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/swapindex.cpp
 */
public class SwapIndex extends InterestRateIndex {
    private final IborIndex iborIndex_;
    private final Period fixedLegTenor_;
    private final BusinessDayConvention fixedLegConvention_;
    private final boolean exogenousDiscount_;
    private final Handle<YieldTermStructure> discount_;
    private VanillaSwap lastSwap_;
    private Date lastFixingDate_;

    public SwapIndex(String familyName,
                     Period tenor,
                     int settlementDays,
                     Currency currency,
                     Calendar fixingCalendar,
                     Period fixedLegTenor,
                     BusinessDayConvention fixedLegConvention,
                     DayCounter fixedLegDayCounter,
                     IborIndex iborIndex) {
        super(familyName, tenor, settlementDays, currency, fixingCalendar, fixedLegDayCounter);
        iborIndex_ = iborIndex;
        fixedLegTenor_ = fixedLegTenor;
        fixedLegConvention_ = fixedLegConvention;
        exogenousDiscount_ = false;
        discount_ = new Handle<>(null, false);
        registerWith(iborIndex_);
    }

    public SwapIndex(String familyName,
                     Period tenor,
                     int settlementDays,
                     Currency currency,
                     Calendar fixingCalendar,
                     Period fixedLegTenor,
                     BusinessDayConvention fixedLegConvention,
                     DayCounter fixedLegDayCounter,
                     IborIndex iborIndex,
                     Handle<YieldTermStructure> discount) {
        super(familyName, tenor, settlementDays, currency, fixingCalendar, fixedLegDayCounter);
        iborIndex_ = iborIndex;
        fixedLegTenor_ = fixedLegTenor;
        fixedLegConvention_ = fixedLegConvention;
        exogenousDiscount_ = true;
        discount_ = discount != null ? discount : new Handle<>(null, false);
        registerWith(iborIndex_);
        if (!discount_.empty())
            registerWith(discount_.get());
    }

    public Period fixedLegTenor() {
        return fixedLegTenor_;
    }

    public BusinessDayConvention fixedLegConvention() {
        return fixedLegConvention_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    public Handle<YieldTermStructure> forwardingTermStructure() {
        return iborIndex_.forwardingTermStructure();
    }

    public Handle<YieldTermStructure> discountingTermStructure() {
        return discount_;
    }

    public boolean exogenousDiscount() {
        return exogenousDiscount_;
    }

    /**
     * Underlying swap, as {@code SwapIndex::underlyingSwap}. The effective
     * date is the index value date of the fixing date.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/swapindex.cpp
     */
    public VanillaSwap underlyingSwap(Date fixingDate) {
        QL_REQUIRE(TimeUtils.neq(fixingDate, new Date()), "null fixing date");
        if (lastFixingDate_ == null || TimeUtils.neq(lastFixingDate_, fixingDate)) {
            Date effectiveDate = valueDate(fixingDate);
            Date terminationDate = effectiveDate.add(tenor());
            // MakeVanillaSwap defaults: fixed calendar = index fixing
            // calendar, floating calendar = ibor fixing calendar, both
            // backwards, no end-of-month rule.
            Schedule fixed = new Schedule(effectiveDate, terminationDate, fixedLegTenor_,
                    fixingCalendar(), fixedLegConvention_, fixedLegConvention_,
                    DateGenerationRule.BACKWARD, false, new Date(), new Date());
            Schedule floating = new Schedule(effectiveDate, terminationDate,
                    iborIndex_.tenor(), iborIndex_.fixingCalendar(),
                    iborIndex_.businessDayConvention(), iborIndex_.businessDayConvention(),
                    DateGenerationRule.BACKWARD, false, new Date(), new Date());
            lastSwap_ = new VanillaSwap(VanillaSwap.Type.Payer, 100.0, fixed, 0.0,
                    dayCounter(), floating, iborIndex_, 0.0, iborIndex_.dayCounter());
            Handle<YieldTermStructure> discount = exogenousDiscount_
                    ? discount_ : iborIndex_.forwardingTermStructure();
            lastSwap_.setPricingEngine(new DiscountingSwapEngine(discount));
            lastFixingDate_ = fixingDate;
        }
        return lastSwap_;
    }

    @Override
    public Date maturityDate(Date valueDate) {
        return underlyingSwap(fixingDate(valueDate)).maturityDate();
    }

    @Override
    public double forecastFixing(Date fixingDate) {
        return underlyingSwap(fixingDate).fairRate();
    }

    public SwapIndex clone(Handle<YieldTermStructure> forwarding) {
        if (exogenousDiscount_)
            return new SwapIndex(familyName(), tenor(), fixingDays(), currency(),
                    fixingCalendar(), fixedLegTenor(), fixedLegConvention(), dayCounter(),
                    iborIndex_.clone(forwarding), discount_);
        return new SwapIndex(familyName(), tenor(), fixingDays(), currency(),
                fixingCalendar(), fixedLegTenor(), fixedLegConvention(), dayCounter(),
                iborIndex_.clone(forwarding));
    }

    public SwapIndex clone(Handle<YieldTermStructure> forwarding,
                           Handle<YieldTermStructure> discounting) {
        return new SwapIndex(familyName(), tenor(), fixingDays(), currency(),
                fixingCalendar(), fixedLegTenor(), fixedLegConvention(), dayCounter(),
                iborIndex_.clone(forwarding), discounting);
    }

    public SwapIndex clone(Period tenor) {
        if (exogenousDiscount_)
            return new SwapIndex(familyName(), tenor, fixingDays(), currency(),
                    fixingCalendar(), fixedLegTenor(), fixedLegConvention(), dayCounter(),
                    iborIndex_, discount_);
        return new SwapIndex(familyName(), tenor, fixingDays(), currency(),
                fixingCalendar(), fixedLegTenor(), fixedLegConvention(), dayCounter(),
                iborIndex_);
    }
}
