package jquant.cashflows;

import jquant.Handle;
import jquant.Quote;

/**
 * Interface for coupon pricers using a mean-reverting model.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/couponpricer.hpp}
 * ({@code MeanRevertingPricer}).
 */
public interface MeanRevertingPricer {

    /** Current mean reversion value. */
    double meanReversion();

    /** Sets the mean-reversion quote. */
    void setMeanReversion(Handle<Quote> meanReversion);
}
