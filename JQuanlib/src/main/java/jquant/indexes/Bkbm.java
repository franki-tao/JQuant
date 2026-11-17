package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NewZealand;
import jquant.time.daycounters.Actual365Fixed;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bkbm index.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/indexes/ibor/bkbm.hpp
 */
public class Bkbm extends IborIndex {
    public Bkbm(Period tenor) {
        this(tenor, null);
    }

    public Bkbm(Period tenor, Handle<YieldTermStructure> h) {
        super("Bkbm", tenor,
                0, // settlement days
                nzd(), new NewZealand(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true,
                new Actual365Fixed(), h);
        QL_REQUIRE(tenor.units() != TimeUnit.DAYS,
                "for daily tenors (" + tenor
                        + ") dedicated DailyTenor constructor must be used");
    }

    private static Currency nzd() {
        return Currencies.nzd();
    }
}
