package jquant.indexes;

import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Thirty360;

/**
 * Euribor ISDA Fix A swap-rate index.
 *
 * <p>Ported from QuantLib
 * {@code ql/indexes/swap/euriborswap.{hpp,cpp}}.
 */
public class EuriborSwapIsdaFixA extends SwapIndex {
    public EuriborSwapIsdaFixA(Period tenor, Handle<YieldTermStructure> h) {
        super("EuriborSwapIsdaFixA", // familyName
                tenor,
                2, // settlementDays
                Currencies.eur(),
                new Target(),
                new Period(1, TimeUnit.YEARS), // fixedLegTenor
                BusinessDayConvention.MODIFIED_FOLLOWING, // fixedLegConvention
                new Thirty360(Thirty360.Convention.BondBasis), // fixedLegDayCounter
                TimeUtils.greater(tenor, new Period(1, TimeUnit.YEARS))
                        ? new Euribor(new Period(6, TimeUnit.MONTHS), h)
                        : new Euribor(new Period(3, TimeUnit.MONTHS), h));
    }

    public EuriborSwapIsdaFixA(Period tenor,
                               Handle<YieldTermStructure> forwarding,
                               Handle<YieldTermStructure> discounting) {
        super("EuriborSwapIsdaFixA",
                tenor,
                2,
                Currencies.eur(),
                new Target(),
                new Period(1, TimeUnit.YEARS),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                new Thirty360(Thirty360.Convention.BondBasis),
                TimeUtils.greater(tenor, new Period(1, TimeUnit.YEARS))
                        ? new Euribor(new Period(6, TimeUnit.MONTHS), forwarding)
                        : new Euribor(new Period(3, TimeUnit.MONTHS), forwarding),
                discounting);
    }
}
