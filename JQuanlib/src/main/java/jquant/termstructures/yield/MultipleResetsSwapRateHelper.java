package jquant.termstructures.yield;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.cashflows.MultipleResetsCoupon;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
import jquant.instruments.MakeMultipleResetsSwap;
import jquant.instruments.MultipleResetsSwap;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Rate helper for bootstrapping from multiple-resets swap quotes.
 *
 * <p>Port of QuantLib's {@code MultipleResetsSwapRateHelper} in
 * {@code ql/termstructures/yield/multipleresetsswaphelper.{hpp,cpp}}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/multipleresetsswaphelper.hpp
 */
public class MultipleResetsSwapRateHelper extends RelativeDateRateHelper {
    protected final int settlementDays_;
    protected final Period tenor_;
    protected IborIndex iborIndex_;
    protected final int resetsPerCoupon_;
    protected final RateAveraging averagingMethod_;
    protected final double spread_;
    protected final Frequency fixedFrequency_;
    protected final DayCounter fixedDayCount_;
    protected final BusinessDayConvention fixedConvention_;
    protected final Handle<YieldTermStructure> discountHandle_;
    protected final RelinkableHandle<YieldTermStructure> termStructureHandle_ =
            new RelinkableHandle<>();
    protected final RelinkableHandle<YieldTermStructure> discountRelinkableHandle_ =
            new RelinkableHandle<>();
    protected MultipleResetsSwap swap_;

    public MultipleResetsSwapRateHelper(int settlementDays,
                                        Period tenor,
                                        Handle<Quote> fixedRate,
                                        IborIndex iborIndex,
                                        int resetsPerCoupon) {
        this(settlementDays, tenor, fixedRate, iborIndex, resetsPerCoupon,
                new Handle<YieldTermStructure>(null, false),
                RateAveraging.Compound, 0.0, Frequency.NO_FREQUENCY, null,
                BusinessDayConvention.MODIFIED_FOLLOWING);
    }

    public MultipleResetsSwapRateHelper(int settlementDays,
                                        Period tenor,
                                        double fixedRate,
                                        IborIndex iborIndex,
                                        int resetsPerCoupon) {
        this(settlementDays, tenor,
                new Handle<Quote>(new SimpleQuote(fixedRate), true),
                iborIndex, resetsPerCoupon);
    }

    public MultipleResetsSwapRateHelper(int settlementDays,
                                        Period tenor,
                                        double fixedRate,
                                        IborIndex iborIndex,
                                        int resetsPerCoupon,
                                        Handle<YieldTermStructure> discountingCurve,
                                        RateAveraging averagingMethod,
                                        double spread,
                                        Frequency fixedFrequency,
                                        DayCounter fixedDayCount,
                                        BusinessDayConvention fixedConvention) {
        this(settlementDays, tenor,
                new Handle<Quote>(new SimpleQuote(fixedRate), true),
                iborIndex, resetsPerCoupon, discountingCurve, averagingMethod,
                spread, fixedFrequency, fixedDayCount, fixedConvention);
    }

    public MultipleResetsSwapRateHelper(int settlementDays,
                                        Period tenor,
                                        Handle<Quote> fixedRate,
                                        IborIndex iborIndex,
                                        int resetsPerCoupon,
                                        Handle<YieldTermStructure> discountingCurve,
                                        RateAveraging averagingMethod,
                                        double spread,
                                        Frequency fixedFrequency,
                                        DayCounter fixedDayCount,
                                        BusinessDayConvention fixedConvention) {
        super(fixedRate);

        settlementDays_ = settlementDays;
        tenor_ = tenor;
        resetsPerCoupon_ = resetsPerCoupon;
        averagingMethod_ = averagingMethod;
        spread_ = spread;
        fixedFrequency_ = fixedFrequency;
        fixedDayCount_ = (fixedDayCount == null || fixedDayCount.empty())
                ? iborIndex.dayCounter() : fixedDayCount;
        fixedConvention_ = fixedConvention;
        discountHandle_ = discountingCurve != null
                ? discountingCurve : new Handle<YieldTermStructure>(null, false);

        // Clone the index so it forwards rates from termStructureHandle_, but
        // don't register the clone as an observer of termStructureHandle_
        // (that would interfere with bootstrapping). Java indexes register
        // with Handle.get(), the handle's link.
        iborIndex_ = iborIndex.clone(termStructureHandle_);
        iborIndex_.unregisterWith(termStructureHandle_.get());

        registerWith(iborIndex_);
        registerWith(discountHandle_.get());
        initializeDates();
    }

    @Override
    protected void initializeDates() {
        swap_ = new MakeMultipleResetsSwap(tenor_, iborIndex_, resetsPerCoupon_)
                .withFixedRate(0.0)
                .withSettlementDays(settlementDays_)
                .withFixedLegFrequency(fixedFrequency_)
                .withFixedLegDayCount(fixedDayCount_)
                .withFixedLegConvention(fixedConvention_)
                .withFloatingLegSpread(spread_)
                .withAveragingMethod(averagingMethod_)
                .withDiscountingTermStructure(discountRelinkableHandle_)
                .makeSwap();

        // Official simplifyNotificationGraph(*swap_, true) is a notification
        // graph optimization with no effect on prices; it is not ported.

        earliestDate_ = swap_.startDate();
        maturityDate_ = swap_.maturityDate();

        // The last fixing forecasts the index until its own maturity, which
        // can be later than the end of the swap if the last reset date was
        // adjusted; the curve must extend at least that far.
        List<CashFlow> floatingLeg = swap_.floatingLeg();
        CashFlow lastCashFlow = floatingLeg.get(floatingLeg.size() - 1);
        QL_REQUIRE(lastCashFlow instanceof MultipleResetsCoupon,
                "multiple-resets coupon expected");
        MultipleResetsCoupon lastCoupon = (MultipleResetsCoupon) lastCashFlow;
        List<Date> fixingDates = lastCoupon.fixingDates();
        Date lastFixingEndDate = iborIndex_.maturityDate(
                iborIndex_.valueDate(fixingDates.get(fixingDates.size() - 1)));

        Date latest = swap_.fixedLeg().get(swap_.fixedLeg().size() - 1).date();
        Date floatingLast = swap_.floatingLeg().get(swap_.floatingLeg().size() - 1).date();
        if (TimeUtils.greater(floatingLast, latest))
            latest = floatingLast;
        if (TimeUtils.greater(lastFixingEndDate, latest))
            latest = lastFixingEndDate;
        latestRelevantDate_ = latest;
        // Official also assigns latestDate_ here; Java RateHelper has no such
        // field and no empty-Date fallbacks, so pin the pillar directly.
        pillarDate_ = latestRelevantDate_;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
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
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        swap_.deepUpdate();
        return swap_.fairRate();
    }

    public MultipleResetsSwap swap() {
        return swap_;
    }
}
