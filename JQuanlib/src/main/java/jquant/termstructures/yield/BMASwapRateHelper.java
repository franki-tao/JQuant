package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.indexes.BMAIndex;
import jquant.indexes.IborIndex;
import jquant.instruments.BMASwap;
import jquant.instruments.Swap;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.MakeSchedule;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from a BMA swap's Libor fraction. The Libor
 * leg is forecast and discounted on the Libor forwarding curve. The BMA leg
 * is forecast on the curve being built.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class BMASwapRateHelper extends RateHelper {
    private final Period tenor_;
    private final int settlementDays_;
    private final Calendar calendar_;
    private final Period bmaPeriod_;
    private final BusinessDayConvention bmaConvention_;
    private final DayCounter bmaDayCount_;
    private final IborIndex iborIndex_;
    private final RelinkableHandle<YieldTermStructure> termStructureHandle_;
    private final BMASwap swap_;

    public BMASwapRateHelper(Handle<Quote> liborFraction,
                             Period tenor,
                             int settlementDays,
                             Calendar calendar,
                             Period bmaPeriod,
                             BusinessDayConvention bmaConvention,
                             DayCounter bmaDayCount,
                             BMAIndex bmaIndex,
                             IborIndex iborIndex) {
        super(liborFraction);
        QL_REQUIRE(bmaIndex != null, "null BMA index");
        tenor_ = tenor;
        settlementDays_ = settlementDays;
        calendar_ = calendar;
        bmaPeriod_ = bmaPeriod;
        bmaConvention_ = bmaConvention;
        bmaDayCount_ = bmaDayCount;
        iborIndex_ = iborIndex;
        termStructureHandle_ = new RelinkableHandle<>(null, false);
        BMAIndex clonedIndex = new BMAIndex(termStructureHandle_);
        Date evaluationDate = jquant.Settings.instance.evaluationDate().Date();
        earliestDate_ = calendar_.advance(evaluationDate, settlementDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        swap_ = buildSwap(clonedIndex, earliestDate_, earliestDate_.add(tenor_));
        Date adjusted = calendar_.adjust(swap_.maturityDate(), BusinessDayConvention.FOLLOWING);
        int weekday = adjusted.weekday().getValue();
        Date nextWednesday = weekday >= 4
                ? adjusted.add(11 - weekday)
                : adjusted.add(4 - weekday);
        Date latest = clonedIndex.valueDate(clonedIndex.fixingCalendar().adjust(
                nextWednesday, BusinessDayConvention.FOLLOWING));
        maturityDate_ = latest;
        latestRelevantDate_ = latest;
        pillarDate_ = latest;
    }

    private BMASwap buildSwap(BMAIndex clonedIndex, Date start, Date maturity) {
        Schedule bmaSchedule = new MakeSchedule().from(start).to(maturity)
                .withTenor(bmaPeriod_)
                .withCalendar(clonedIndex.fixingCalendar())
                .withConvention(bmaConvention_)
                .backwards()
                .toSchedule();
        Schedule liborSchedule = new MakeSchedule().from(start).to(maturity)
                .withTenor(iborIndex_.tenor())
                .withCalendar(iborIndex_.fixingCalendar())
                .withConvention(iborIndex_.businessDayConvention())
                .endOfMonth(iborIndex_.endOfMonth())
                .backwards()
                .toSchedule();
        BMASwap swap = new BMASwap(Swap.Type.Payer, 100.0,
                liborSchedule, 0.75, 0.0, iborIndex_, iborIndex_.dayCounter(),
                bmaSchedule, clonedIndex, bmaDayCount_);
        QL_REQUIRE(iborIndex_.forwardingTermStructure() != null
                        && !iborIndex_.forwardingTermStructure().empty(),
                "null Libor forwarding curve");
        swap.setPricingEngine(new DiscountingMultiLegSwapEngine(
                iborIndex_.forwardingTermStructure().currentLink()));
        return swap;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        termStructureHandle_.linkTo(termStructure, false);
        super.setTermStructure(termStructure);
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        swap_.deepUpdate();
        return swap_.fairLiborFraction();
    }
}
