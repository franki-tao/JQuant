package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstrap helper for a FRA quote. The par implied quote is
 * {@code (discount(start) / discount(end) - 1) / tau}. The indexed quote is
 * the ibor forecast fixing. Neither path returns the stored market quote.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class FraRateHelper extends RateHelper {
    public enum Pillar { MaturityDate, LastRelevantDate }

    private final Period periodToStart_;
    private final int fixingDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    private final boolean endOfMonth_;
    private final DayCounter dayCounter_;
    private final Period tenor_;
    private final Pillar pillarChoice_;
    private final boolean useIndexedCoupon_;
    private IborIndex iborIndex_;
    private Date fixingDate_;
    private double spanningTime_;

    public FraRateHelper(Handle<Quote> rate,
                         int monthsToStart,
                         int monthsToEnd,
                         int fixingDays,
                         Calendar calendar,
                         BusinessDayConvention convention,
                         boolean endOfMonth,
                         DayCounter dayCounter,
                         Pillar pillar,
                         boolean useIndexedCoupon) {
        super(rate);
        QL_REQUIRE(monthsToEnd > monthsToStart,
                "monthsToEnd (" + monthsToEnd + ") must be greater than monthsToStart (" + monthsToStart + ")");
        periodToStart_ = new Period(monthsToStart, TimeUnit.MONTHS);
        tenor_ = new Period(monthsToEnd - monthsToStart, TimeUnit.MONTHS);
        fixingDays_ = fixingDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        dayCounter_ = dayCounter;
        pillarChoice_ = pillar;
        useIndexedCoupon_ = useIndexedCoupon;
        initializeDates();
    }

    public Date fixingDate() {
        return fixingDate_;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        iborIndex_ = new IborIndex("no-fix", tenor_, fixingDays_, new jquant.Currency(),
                calendar_, convention_, endOfMonth_, dayCounter_,
                new Handle<>(termStructure, false));
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        if (useIndexedCoupon_)
            return iborIndex_.forecastFixing(fixingDate_);
        return (termStructure_.discount(earliestDate_, false) / termStructure_.discount(maturityDate_, false) - 1.0)
                / spanningTime_;
    }

    private void initializeDates() {
        Date evaluation = Settings.instance.evaluationDate().Date();
        Date spot = calendar_.advance(evaluation, fixingDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        earliestDate_ = calendar_.advance(spot, periodToStart_, convention_, endOfMonth_);
        maturityDate_ = calendar_.advance(spot,
                new Period(periodToStart_.length() + tenor_.length(), periodToStart_.units()),
                convention_, endOfMonth_);
        if (useIndexedCoupon_)
            latestRelevantDate_ = calendar_.advance(earliestDate_, tenor_, convention_, endOfMonth_);
        else {
            latestRelevantDate_ = maturityDate_;
            spanningTime_ = dayCounter_.yearFraction(earliestDate_, maturityDate_, new Date(), new Date());
        }
        fixingDate_ = calendar_.advance(earliestDate_, -fixingDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        pillarDate_ = pillarChoice_ == Pillar.MaturityDate ? maturityDate_ : latestRelevantDate_;
        QL_REQUIRE(!TimeUtils.greater(pillarDate_, latestRelevantDate_),
                "pillar after the latest relevant date");
    }
}
