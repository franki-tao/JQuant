package termstructures;

import jquant.Compounding;
import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.Futures;
import jquant.math.interpolations.impl.BackwardFlat;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.BootstrapTraits;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.FuturesRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QuantLib {@code testCustomFuturesHelpers}: futures helpers with custom
 * start and end dates bootstrapped on a {@code ForwardRate, BackwardFlat}
 * curve. The three expected rates are the prices {@code 97.0}, {@code 96.5}
 * and {@code 96.0} converted to {@code (100 - price) / 100}, never values
 * computed from the curve under test. Tolerance is the official {@code 1e-8}.
 *
 * <p>QuantLib's {@code CommonVars} uses
 * {@code today = TARGET().adjust(Date::todaysDate())}. A fixed TARGET business
 * day, 15 January 2025, is used here instead.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/interpolatedforwardcurve.hpp
 */
public class ForwardRateBackwardFlatTest {
    private static final double TOLERANCE = 1.0e-8;

    @Test
    public void testCustomFuturesHelpers() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            // TARGET().adjust(Date::todaysDate()) in QuantLib; fixed here.
            Date today = new Target().adjust(new Date(15, Month.JANUARY, 2025),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Target target = new Target();
            Actual360 dayCounter = new Actual360(false);
            List<RateHelper> helpers = new ArrayList<>();

            Date startDate1 = today.add(60);
            double price1 = 97.0;
            int length1 = 2;
            BusinessDayConvention convention = BusinessDayConvention.MODIFIED_FOLLOWING;
            boolean endOfMonth = true;
            helpers.add(new FuturesRateHelper(
                    new Handle<>(new SimpleQuote(price1), false),
                    startDate1, length1, target, convention, endOfMonth,
                    dayCounter, new Handle<>(new SimpleQuote(0.0), false),
                    Futures.Type.Custom));

            Date startDate2 = today.add(120);
            Date endDate2 = startDate2.add(45);
            double price2 = 96.5;
            helpers.add(new FuturesRateHelper(
                    new Handle<>(new SimpleQuote(price2), false),
                    startDate2, endDate2, dayCounter,
                    new Handle<>(new SimpleQuote(0.0), false),
                    Futures.Type.Custom));

            Date startDate3 = today.add(180);
            IborIndex index = euribor3m();
            double price3 = 96.0;
            helpers.add(new FuturesRateHelper(
                    new Handle<>(new SimpleQuote(price3), false),
                    startDate3, index, new Handle<>(new SimpleQuote(0.0), false),
                    Futures.Type.Custom));

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                    today, helpers, dayCounter,
                    BootstrapTraits.ForwardRate, new BackwardFlat());

            Date endDate1 = target.advance(startDate1, length1, TimeUnit.MONTHS,
                    convention, endOfMonth);
            double calculated = curve.forwardRate(startDate1, endDate1, dayCounter,
                    Compounding.Simple, Frequency.ANNUAL, false).rate();
            assertEquals((100 - price1) / 100, calculated, TOLERANCE, "first helper");

            calculated = curve.forwardRate(startDate2, endDate2, dayCounter,
                    Compounding.Simple, Frequency.ANNUAL, false).rate();
            assertEquals((100 - price2) / 100, calculated, TOLERANCE, "second helper");

            Date endDate3 = index.fixingCalendar().advance(startDate3, index.tenor(),
                    index.businessDayConvention(), false);
            calculated = curve.forwardRate(startDate3, endDate3, dayCounter,
                    Compounding.Simple, Frequency.ANNUAL, false).rate();
            assertEquals((100 - price3) / 100, calculated, TOLERANCE, "third helper");
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    /** Euribor3M: two fixing days, TARGET, ModifiedFollowing, end of month, Actual/360. */
    private static IborIndex euribor3m() {
        return new IborIndex("Euribor", new Period(3, TimeUnit.MONTHS), 2, new Currency(),
                new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, true,
                new Actual360(false),
                new Handle<>(new FlatForward(
                        new Date(15, Month.JANUARY, 2025), 0.04, new Actual360(false),
                        Compounding.Continuous, Frequency.ANNUAL), false));
    }
}
