package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.indexes.IborIndex;
import jquant.instruments.Futures;
import jquant.termstructures.YieldTermStructure;
import jquant.time.ASX;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.IMM;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from an Ibor futures price. The quote is
 * {@code 100 * (1 - futureRate)} and {@code futureRate = forward + convexityAdjustment},
 * so the curve forward is the futures rate minus the adjustment. An IMM or ASX
 * start date has to be a date of that exchange. When the end date is omitted,
 * maturity is three successive IMM or ASX dates; an index supplies the maturity
 * by advancing its tenor with {@code endOfMonth} false.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class FuturesRateHelper extends RateHelper {
    private final double yearFraction_;
    private final Handle<Quote> convAdj_;

    public FuturesRateHelper(Handle<Quote> price,
                             Date iborStartDate,
                             int lengthInMonths,
                             Calendar calendar,
                             BusinessDayConvention convention,
                             boolean endOfMonth,
                             DayCounter dayCounter,
                             Handle<Quote> convexityAdjustment,
                             Futures.Type type) {
        super(price);
        checkDate(iborStartDate, type);
        QL_REQUIRE(calendar != null, "null futures calendar");
        QL_REQUIRE(dayCounter != null, "null futures day counter");
        earliestDate_ = iborStartDate;
        maturityDate_ = calendar.advance(iborStartDate,
                new Period(lengthInMonths, TimeUnit.MONTHS), convention, endOfMonth);
        yearFraction_ = yearFraction(dayCounter);
        pillarDate_ = maturityDate_;
        latestRelevantDate_ = maturityDate_;
        convAdj_ = convexityAdjustment;
    }

    public FuturesRateHelper(Handle<Quote> price,
                             Date iborStartDate,
                             Date iborEndDate,
                             DayCounter dayCounter,
                             Handle<Quote> convexityAdjustment,
                             Futures.Type type) {
        super(price);
        checkDate(iborStartDate, type);
        QL_REQUIRE(dayCounter != null, "null futures day counter");
        if (type == Futures.Type.Custom) {
            maturityDate_ = iborEndDate;
        } else if (iborEndDate == null || iborEndDate.serialNumber() == 0) {
            Date maturity = iborStartDate;
            for (int step = 0; step < 3; step++) {
                maturity = type == Futures.Type.ASX
                        ? ASX.nextDate(maturity, false)
                        : IMM.nextDate(maturity, false);
            }
            maturityDate_ = maturity;
        } else {
            QL_REQUIRE(TimeUtils.greater(iborEndDate, iborStartDate),
                    "end date (" + iborEndDate + ") must be greater than start date ("
                            + iborStartDate + ")");
            maturityDate_ = iborEndDate;
        }
        earliestDate_ = iborStartDate;
        yearFraction_ = yearFraction(dayCounter);
        pillarDate_ = maturityDate_;
        latestRelevantDate_ = maturityDate_;
        convAdj_ = convexityAdjustment;
    }

    public FuturesRateHelper(Handle<Quote> price,
                             Date iborStartDate,
                             IborIndex iborIndex,
                             Handle<Quote> convexityAdjustment,
                             Futures.Type type) {
        super(price);
        checkDate(iborStartDate, type);
        QL_REQUIRE(iborIndex != null, "null ibor index");
        earliestDate_ = iborStartDate;
        // Calendar::advance defaults endOfMonth to false. IborIndex::maturityDate uses the index flag.
        maturityDate_ = iborIndex.fixingCalendar().advance(iborStartDate, iborIndex.tenor(),
                iborIndex.businessDayConvention(), false);
        QL_REQUIRE(TimeUtils.greater(maturityDate_, earliestDate_),
                "futures maturity is not after the start date");
        yearFraction_ = yearFraction(iborIndex.dayCounter());
        pillarDate_ = maturityDate_;
        latestRelevantDate_ = maturityDate_;
        convAdj_ = convexityAdjustment;
    }

    public FuturesRateHelper(Handle<Quote> price,
                             Date iborStartDate,
                             IborIndex iborIndex,
                             Handle<Quote> convexityAdjustment) {
        this(price, iborStartDate, iborIndex, convexityAdjustment, Futures.Type.IMM);
    }

    public FuturesRateHelper(Handle<Quote> price, Date iborStartDate, IborIndex iborIndex) {
        this(price, iborStartDate, iborIndex, null, Futures.Type.IMM);
    }

    private static void checkDate(Date date, Futures.Type type) {
        QL_REQUIRE(date != null, "null futures start date");
        QL_REQUIRE(type != null, "null futures type");
        switch (type) {
            case IMM -> QL_REQUIRE(IMM.isIMMdate(date, false),
                    date + " is not a valid IMM date");
            case ASX -> QL_REQUIRE(ASX.isASXdate(date, false),
                    date + " is not a valid ASX date");
            case Custom -> {
            }
            default -> QL_FAIL("unknown futures type (" + type + ")");
        }
    }

    private double yearFraction(DayCounter dayCounter) {
        double yearFraction = dayCounter.yearFraction(earliestDate_, maturityDate_,
                earliestDate_, maturityDate_);
        QL_REQUIRE(yearFraction > 0.0, "non positive futures year fraction");
        return yearFraction;
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        double forwardRate = (termStructure_.discount(earliestDate_, false)
                / termStructure_.discount(maturityDate_, false) - 1.0) / yearFraction_;
        // futureRate = forwardRate + convexityAdjustment, so forward = futuresRate - adjustment.
        double futureRate = forwardRate + convexityAdjustment();
        return 100.0 * (1.0 - futureRate);
    }

    public double convexityAdjustment() {
        if (convAdj_ == null || convAdj_.empty())
            return 0.0;
        return convAdj_.getValue().value();
    }

    @Override
    public List<Quote> notifications() {
        List<Quote> quotes = new ArrayList<>();
        quotes.add(quote().getValue());
        if (convAdj_ != null && !convAdj_.empty())
            quotes.add(convAdj_.getValue());
        return quotes;
    }
}
