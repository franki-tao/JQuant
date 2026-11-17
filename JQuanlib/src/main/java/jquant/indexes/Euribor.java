package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Euribor index.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/ibor/euribor.hpp
 */
public class Euribor extends IborIndex {
    public Euribor(Period tenor) {
        this(tenor, null);
    }

    public Euribor(Period tenor, Handle<YieldTermStructure> h) {
        super("Euribor", tenor,
                2, // settlement days
                eur(), new Target(),
                euriborConvention(tenor), euriborEOM(tenor),
                new Actual360(), h);
        QL_REQUIRE(tenor.units() != TimeUnit.DAYS,
                "for daily tenors (" + tenor
                        + ") dedicated DailyTenor constructor must be used");
    }

    private static BusinessDayConvention euriborConvention(final Period p) {
        switch (p.units()) {
            case DAYS:
            case WEEKS:
                return BusinessDayConvention.FOLLOWING;
            case MONTHS:
            case YEARS:
                return BusinessDayConvention.MODIFIED_FOLLOWING;
            default:
                QL_FAIL("invalid time units");
                return BusinessDayConvention.FOLLOWING;
        }
    }

    private static boolean euriborEOM(final Period p) {
        switch (p.units()) {
            case DAYS:
            case WEEKS:
                return false;
            case MONTHS:
            case YEARS:
                return true;
            default:
                QL_FAIL("invalid time units");
                return false;
        }
    }

    private static Currency eur() {
        return Currencies.eur();
    }
}
