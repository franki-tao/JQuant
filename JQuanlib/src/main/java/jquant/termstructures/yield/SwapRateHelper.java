package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
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

import java.util.ArrayList;
import java.util.List;

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
    private final Handle<Quote> spread_;
    private final Period fwdStart_;
    private final Handle<YieldTermStructure> discountHandle_;
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
                Pillar.LastRelevantDate);
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
                iborIndex, null, new Period(0, TimeUnit.DAYS), null, pillarChoice);
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
                iborIndex, spread, fwdStart, discountingCurve, Pillar.LastRelevantDate);
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
        super(rate);
        QL_REQUIRE(iborIndex != null, "null ibor index");
        tenor_ = tenor;
        calendar_ = calendar;
        fixedFrequency_ = fixedFrequency;
        fixedConvention_ = fixedConvention;
        fixedDayCount_ = fixedDayCount;
        pillarChoice_ = pillarChoice;
        spread_ = spread != null ? spread : new Handle<>(null, false);
        fwdStart_ = fwdStart != null ? fwdStart : new Period(0, TimeUnit.DAYS);
        discountHandle_ = discountingCurve != null
                ? discountingCurve : new Handle<>(null, false);

        // Clone the index and link it to the curve under construction.  The
        // relinkable handle must not register as an observer (it would create
        // a notification cycle while bootstrapping).
        iborIndex_ = iborIndex.clone(termStructureHandle_);
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
        swap_.recalculate();
        double basisPoint = 1.0e-4;
        double floatingLegNPV = swap_.floatingLegNPV();
        double spreadNPV = swap_.floatingLegBPS() / basisPoint * spread();
        double totNPV = -(floatingLegNPV + spreadNPV);
        return totNPV / (swap_.fixedLegBPS() / basisPoint);
    }

    private void initializeDates() {
        Date evaluation = Settings.instance.evaluationDate().Date();
        // Mirrors MakeVanillaSwap: the spot date comes from the index's
        // valueDate interface, and the termination date is the unadjusted
        // start + tenor.  The schedule then adjusts the termination date
        // with its termination-date convention; advancing the maturity
        // here instead would create spurious stubs whenever the
        // anniversary falls on a weekend.
        Date refDate = iborIndex_.fixingCalendar().adjust(evaluation,
                BusinessDayConvention.FOLLOWING);
        Date spot = iborIndex_.fixingCalendar().advance(refDate, iborIndex_.fixingDays(),
                TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        Date start = spot.add(fwdStart_);
        if (fwdStart_.length() < 0)
            start = calendar_.adjust(start, BusinessDayConvention.PRECEDING);
        else if (fwdStart_.length() > 0)
            start = calendar_.adjust(start, BusinessDayConvention.FOLLOWING);
        Date maturity = start.add(tenor_);
        // MakeVanillaSwap uses fixedConvention_ for both the fixed leg's
        // business-day convention and its termination-date convention.
        Schedule fixed = new Schedule(start, maturity, new Period(fixedFrequency_), calendar_,
                fixedConvention_, fixedConvention_,
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        Schedule floating = new Schedule(start, maturity, iborIndex_.tenor(), calendar_,
                iborIndex_.businessDayConvention(), iborIndex_.businessDayConvention(),
                DateGenerationRule.BACKWARD, false, new Date(), new Date());
        swap_ = new VanillaSwap(VanillaSwap.Type.Payer, 1.0, fixed, 0.0, fixedDayCount_,
                floating, iborIndex_, 0.0, iborIndex_.dayCounter());
        swap_.setPricingEngine(new DiscountingSwapEngine(discountRelinkableHandle_));
        maturityDate_ = swap_.maturityDate();
        Date fixingEnd = swap_.lastFloatingFixingEnd();
        latestRelevantDate_ = TimeUtils.greater(fixingEnd, maturityDate_) ? fixingEnd : maturityDate_;
        earliestDate_ = start;
        pillarDate_ = pillarChoice_ == Pillar.MaturityDate ? maturityDate_ : latestRelevantDate_;
        QL_REQUIRE(!TimeUtils.greater(pillarDate_, latestRelevantDate_),
                "pillar after the latest relevant date");
    }
}
