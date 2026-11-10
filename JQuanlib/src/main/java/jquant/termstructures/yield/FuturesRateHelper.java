package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.indexes.IborIndex;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.IMM;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstraps a discount curve from an Ibor futures price. The quote is
 * {@code 100 * (1 - futuresRate)}. The futures rate is the simply compounded
 * forward over the index tenor minus the convexity adjustment.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class FuturesRateHelper extends RateHelper {
    private final double yearFraction_;
    private final Handle<Quote> convAdj_;

    public FuturesRateHelper(Handle<Quote> price,
                             Date iborStartDate,
                             IborIndex iborIndex,
                             Handle<Quote> convexityAdjustment) {
        super(price);
        QL_REQUIRE(iborStartDate != null, "null futures start date");
        QL_REQUIRE(iborIndex != null, "null ibor index");
        QL_REQUIRE(IMM.isIMMdate(iborStartDate, false),
                iborStartDate + " is not a valid IMM date");
        earliestDate_ = iborStartDate;
        maturityDate_ = iborIndex.maturityDate(earliestDate_);
        QL_REQUIRE(TimeUtils.greater(maturityDate_, earliestDate_),
                "futures maturity is not after the start date");
        yearFraction_ = iborIndex.dayCounter().yearFraction(earliestDate_, maturityDate_,
                new Date(), new Date());
        QL_REQUIRE(yearFraction_ > 0.0, "non positive futures year fraction");
        pillarDate_ = maturityDate_;
        latestRelevantDate_ = maturityDate_;
        convAdj_ = convexityAdjustment;
    }

    public FuturesRateHelper(Handle<Quote> price, Date iborStartDate, IborIndex iborIndex) {
        this(price, iborStartDate, iborIndex, null);
    }

    @Override
    public void setTermStructure(YieldTermStructure termStructure) {
        super.setTermStructure(termStructure);
    }

    @Override
    public double impliedQuote() {
        QL_REQUIRE(termStructure_ != null, "term structure not set");
        double forward = (termStructure_.discount(earliestDate_, false)
                / termStructure_.discount(maturityDate_, false) - 1.0) / yearFraction_;
        double futuresRate = forward - convexityAdjustment();
        return 100.0 * (1.0 - futuresRate);
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
