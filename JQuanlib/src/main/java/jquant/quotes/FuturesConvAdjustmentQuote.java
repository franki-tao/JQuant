package jquant.quotes;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Quote for the futures-convexity adjustment of an index. The value is the
 * difference between the futures implied rate and the forward rate, computed
 * with {@link HullWhite#convexityBias}. Times are measured with the index day
 * counter between the evaluation date and the futures date (for the start
 * time) or the index maturity date (for the index maturity).
 *
 * <p>This is a port of QuantLib's {@code FuturesConvAdjustmentQuote}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/quotes/futuresconvadjustmentquote.hpp
 */
public class FuturesConvAdjustmentQuote extends Quote implements Observer {
    private final Set<Observable> registeredObservables = new HashSet<>();
    protected final DayCounter dc_;
    protected final Date futuresDate_;
    protected final Date indexMaturityDate_;
    protected final Handle<Quote> futuresQuote_;
    protected final Handle<Quote> volatility_;
    protected final Handle<Quote> meanReversion_;
    private double rate_ = Double.NaN;

    public FuturesConvAdjustmentQuote(IborIndex index,
                                      Date futuresDate,
                                      Handle<Quote> futuresQuote,
                                      Handle<Quote> volatility,
                                      Handle<Quote> meanReversion) {
        QL_REQUIRE(index != null, "null index");
        QL_REQUIRE(futuresDate != null, "null futures date");
        dc_ = index.dayCounter();
        futuresDate_ = futuresDate;
        indexMaturityDate_ = index.maturityDate(futuresDate_);
        futuresQuote_ = futuresQuote;
        volatility_ = volatility;
        meanReversion_ = meanReversion;

        registerWith(futuresQuote_ == null ? null : futuresQuote_.get());
        registerWith(volatility_ == null ? null : volatility_.get());
        registerWith(meanReversion_ == null ? null : meanReversion_.get());
        registerWith(Settings.instance.evaluationDate());
    }

    @Override
    public double value() {
        if (Double.isNaN(rate_)) {
            Date settlementDate = Settings.instance.evaluationDate().Date();
            Date nullDate = new Date();
            double startTime = dc_.yearFraction(settlementDate, futuresDate_, nullDate, nullDate);
            double indexMaturity = dc_.yearFraction(settlementDate, indexMaturityDate_, nullDate, nullDate);
            rate_ = HullWhite.convexityBias(futuresQuote_.getValue().value(),
                    startTime,
                    indexMaturity,
                    volatility_.getValue().value(),
                    meanReversion_.getValue().value());
        }
        return rate_;
    }

    @Override
    public boolean isValid() {
        return !futuresQuote_.empty() && !volatility_.empty() && !meanReversion_.empty()
                && futuresQuote_.getValue().isValid()
                && volatility_.getValue().isValid()
                && meanReversion_.getValue().isValid();
    }

    @Override
    public void update() {
        rate_ = Double.NaN;
        notifyObservers();
    }

    public double futuresValue() {
        return futuresQuote_.getValue().value();
    }

    public double volatility() {
        return volatility_.getValue().value();
    }

    public double meanReversion() {
        return meanReversion_.getValue().value();
    }

    public Date immDate() {
        return futuresDate_;
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable observable) {
        if (observable != null) {
            observable.unregisterObserver(this);
            registeredObservables.remove(observable);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable observable : registeredObservables) {
            observable.unregisterObserver(this);
        }
        registeredObservables.clear();
    }
}
