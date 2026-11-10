package jquant.termstructures.yield;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bootstrap helper. {@code quoteError} is the market quote minus {@link #impliedQuote}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/bootstraphelper.hpp
 */
public abstract class RateHelper {
    private final Handle<Quote> quote_;
    protected Date earliestDate_;
    protected Date latestRelevantDate_;
    protected Date pillarDate_;
    protected Date maturityDate_;
    protected YieldTermStructure termStructure_;

    protected RateHelper(Handle<Quote> quote) {
        QL_REQUIRE(quote != null, "null quote");
        quote_ = quote;
    }

    public Handle<Quote> quote() {
        return quote_;
    }

    //! quotes whose changes must rebuild a curve that uses this helper
    public List<Quote> notifications() {
        return List.of(quote_.getValue());
    }

    public Date earliestDate() {
        return earliestDate_;
    }

    public Date latestRelevantDate() {
        return latestRelevantDate_;
    }

    public Date pillarDate() {
        return pillarDate_;
    }

    public Date maturityDate() {
        return maturityDate_;
    }

    public abstract double impliedQuote();

    public void setTermStructure(YieldTermStructure termStructure) {
        termStructure_ = termStructure;
    }

    public double quoteError() {
        QL_REQUIRE(quote_.getValue().isValid(), "invalid quote");
        return quote_.getValue().value() - impliedQuote();
    }
}
