package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.indexes.IborIndex;
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

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from a spot vanilla-swap quote. The implied
 * quote is the swap fair rate on the curve being built, not the stored quote.
 * The default pillar is the last relevant date, which is the later of the
 * swap maturity and the last floating index end.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class SwapRateHelper extends RateHelper {
    public enum Pillar { MaturityDate, LastRelevantDate }

    private final Period tenor_;
    private final Calendar calendar_;
    private final Frequency fixedFrequency_;
    private final BusinessDayConvention fixedConvention_;
    private final DayCounter fixedDayCount_;
    private final IborIndex iborIndex_;
    private final Pillar pillarChoice_;
    private VanillaSwap swap_;

    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex) {
        this(rate, tenor, calendar, fixedFrequency, fixedConvention, fixedDayCount,
                iborIndex, Pillar.LastRelevantDate);
    }

    public SwapRateHelper(Handle<Quote> rate,
                          Period tenor,
                          Calendar calendar,
                          Frequency fixedFrequency,
                          BusinessDayConvention fixedConvention,
                          DayCounter fixedDayCount,
                          IborIndex iborIndex,
                          Pillar pillarChoice) {
        super(rate);
        QL_REQUIRE(iborIndex != null, "null ibor index");
        tenor_ = tenor;
        calendar_ = calendar;
        fixedFrequency_ = fixedFrequency;
        fixedConvention_ = fixedConvention;
        fixedDayCount_ = fixedDayCount;
        iborIndex_ = iborIndex;
        pillarChoice_ = pillarChoice;
        initializeDates(iborIndex);
    }

    public VanillaSwap swap() {
        return swap_;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        IborIndex linked = iborIndex_.clone(new Handle<>(termStructure, false));
        initializeDates(linked);
        swap_.setPricingEngine(new DiscountingSwapEngine(termStructure));
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        swap_.recalculate();
        return swap_.fairRate();
    }

    private void initializeDates(IborIndex index) {
        Date evaluation = Settings.instance.evaluationDate().Date();
        // Mirrors MakeVanillaSwap: the spot date comes from the index's
        // valueDate interface, and the termination date is the unadjusted
        // start + tenor.  The schedule then adjusts the termination date
        // with its termination-date convention; advancing the maturity
        // here instead would create spurious stubs whenever the
        // anniversary falls on a weekend.
        Date refDate = index.fixingCalendar().adjust(evaluation,
                BusinessDayConvention.FOLLOWING);
        Date start = index.fixingCalendar().advance(refDate, index.fixingDays(),
                TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        Date maturity = start.add(tenor_);
        // MakeVanillaSwap uses fixedConvention_ for both the fixed leg's
        // business-day convention and its termination-date convention.
        Schedule fixed = new Schedule(start, maturity, new Period(fixedFrequency_), calendar_,
                fixedConvention_, fixedConvention_,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        Schedule floating = new Schedule(start, maturity, index.tenor(), calendar_,
                index.businessDayConvention(), index.businessDayConvention(),
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        swap_ = new VanillaSwap(VanillaSwap.Type.Payer, 1.0, fixed, 0.0, fixedDayCount_,
                floating, index, 0.0, index.dayCounter());
        maturityDate_ = swap_.maturityDate();
        Date fixingEnd = swap_.lastFloatingFixingEnd();
        latestRelevantDate_ = TimeUtils.greater(fixingEnd, maturityDate_) ? fixingEnd : maturityDate_;
        earliestDate_ = start;
        pillarDate_ = pillarChoice_ == Pillar.MaturityDate ? maturityDate_ : latestRelevantDate_;
        QL_REQUIRE(!TimeUtils.greater(pillarDate_, latestRelevantDate_),
                "pillar after the latest relevant date");
    }
}
