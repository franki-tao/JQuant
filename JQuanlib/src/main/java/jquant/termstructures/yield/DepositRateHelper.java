package jquant.termstructures.yield;

import jquant.Currency;
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

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Deposit used to bootstrap a yield curve. The implied quote is the simply
 * compounded forward from the index value date to its maturity, read off the
 * curve. Dates follow {@code DepositRateHelper::initializeDates}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class DepositRateHelper extends RateHelper {
    private final Period tenor_;
    private final int fixingDays_;
    private final Calendar calendar_;
    private final BusinessDayConvention convention_;
    private final boolean endOfMonth_;
    private final DayCounter dayCounter_;
    private Date fixingDate_;
    private IborIndex iborIndex_;

    public DepositRateHelper(Handle<Quote> rate,
                             Period tenor,
                             int fixingDays,
                             Calendar calendar,
                             BusinessDayConvention convention,
                             boolean endOfMonth,
                             DayCounter dayCounter) {
        super(rate);
        QL_REQUIRE(tenor != null, "null tenor");
        QL_REQUIRE(calendar != null, "null calendar");
        QL_REQUIRE(dayCounter != null, "null day counter");
        tenor_ = tenor;
        fixingDays_ = fixingDays;
        calendar_ = calendar;
        convention_ = convention;
        endOfMonth_ = endOfMonth;
        dayCounter_ = dayCounter;
        initializeDates();
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
        // The index forecasts from the curve. It is not registered as an
        // observer of a relink: bootstrap reads the discount directly.
        iborIndex_ = new IborIndex(
                "no-fix",
                tenor_,
                fixingDays_,
                new Currency(),
                calendar_,
                convention_,
                endOfMonth_,
                dayCounter_,
                new Handle<>(termStructure, false));
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        return iborIndex_.forecastFixing(fixingDate_);
    }

    private void initializeDates() {
        Date evaluationDate = Settings.instance.evaluationDate().Date();
        earliestDate_ = calendar_.advance(evaluationDate, fixingDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        fixingDate_ = calendar_.advance(earliestDate_, -fixingDays_, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        maturityDate_ = calendar_.advance(earliestDate_, tenor_, convention_, endOfMonth_);
        pillarDate_ = maturityDate_;
        latestRelevantDate_ = maturityDate_;
    }
}
