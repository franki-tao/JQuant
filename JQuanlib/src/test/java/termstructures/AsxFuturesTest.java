package termstructures;

import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.ForwardRateAgreement;
import jquant.instruments.Futures;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.FuturesRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.ASX;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ASX futures from {@code test-suite/piecewiseyieldcurve.cpp}. The published
 * rates are 4.581, 4.573 and 4.557. Each helper is built only when the ASX
 * date is a business day, the quote is {@code 100 - rate}, and the type is
 * {@code Futures::ASX}. After a log-linear bootstrap the indexed FRA forward
 * equals that rate. Tolerance is the official {@code 1e-9}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/dates.cpp
 */
public class AsxFuturesTest {
    private static final double TOLERANCE = 1.0e-9;
    private static final double[] ASX_RATES = {4.581, 4.573, 4.557};

    @Test
    public void testNullDateUsesTheEvaluationDate() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date evaluation = new Date(1, Month.JANUARY, 2024);
            Settings.instance.evaluationDate().equal(evaluation);
            assertTrue(TimeUtils.equals(new Date(12, Month.JANUARY, 2024), ASX.nextDate(new Date(), false)));
            assertEquals("F4", ASX.nextCode(new Date(), false));
            assertTrue(ASX.isASXdate(new Date(12, Month.JANUARY, 2024), false));
            assertFalse(ASX.isASXdate(new Date(12, Month.JANUARY, 2024), true));
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testAsxMaturityRules() {
        IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS));
        Date start = new Date(9, Month.FEBRUARY, 2024);
        assertTrue(ASX.isASXdate(start, false));
        Handle<jquant.Quote> price = new Handle<>(new SimpleQuote(95.419), false);

        FuturesRateHelper indexed = new FuturesRateHelper(
                price, start, euribor3m, null, Futures.Type.ASX);
        assertTrue(TimeUtils.equals(new Date(9, Month.MAY, 2024), indexed.maturityDate()));
        assertTrue(TimeUtils.equals(indexed.maturityDate(), indexed.pillarDate()));

        FuturesRateHelper byLength = new FuturesRateHelper(
                price, start, 3, new Target(), BusinessDayConvention.MODIFIED_FOLLOWING, false,
                new Actual360(false), null, Futures.Type.ASX);
        assertTrue(TimeUtils.equals(new Date(9, Month.MAY, 2024), byLength.maturityDate()));

        FuturesRateHelper byExchangeDates = new FuturesRateHelper(
                price, start, new Date(), new Actual360(false), null, Futures.Type.ASX);
        assertTrue(TimeUtils.equals(new Date(10, Month.MAY, 2024), byExchangeDates.maturityDate()));
        assertTrue(TimeUtils.equals(byExchangeDates.maturityDate(), byExchangeDates.latestRelevantDate()));

        assertThrows(IllegalArgumentException.class,
                () -> new FuturesRateHelper(price, new Date(15, Month.JANUARY, 2024),
                        euribor3m, null, Futures.Type.ASX));
        assertThrows(IllegalArgumentException.class,
                () -> new FuturesRateHelper(price, new Date(12, Month.JANUARY, 2024), euribor3m));
    }

    @Test
    public void testAsxFuturesRepriceToTheQuote() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Calendar target = new Target();
            Date today = target.adjust(Date.todaysDate(), BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS));
            List<Date> starts = new ArrayList<>();
            List<Double> rates = new ArrayList<>();
            List<RateHelper> futures = new ArrayList<>();
            Date asxDate = new Date();
            for (double rate : ASX_RATES) {
                asxDate = ASX.nextDate(asxDate, false);
                if (TimeUtils.less(euribor3m.fixingDate(asxDate), today))
                    asxDate = ASX.nextDate(asxDate, false);
                if (!target.isBusinessDay(asxDate))
                    continue;
                assertTrue(ASX.isASXdate(asxDate, false));
                starts.add(asxDate);
                rates.add(rate);
                futures.add(new FuturesRateHelper(
                        new Handle<>(new SimpleQuote(100.0 - rate), false),
                        asxDate, euribor3m, null, Futures.Type.ASX));
            }
            assertTrue(!futures.isEmpty());

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(settlement, futures, new Actual360(false));
            IborIndex forecasting = euribor(new Period(3, TimeUnit.MONTHS), curve);
            for (int i = 0; i < futures.size(); i++) {
                double expected = rates.get(i) / 100.0;
                Date start = starts.get(i);
                ForwardRateAgreement fra = new ForwardRateAgreement(
                        forecasting, start, forecasting.maturityDate(start),
                        ForwardRateAgreement.Position.Long, expected, 100.0, curve, true);
                assertEquals(expected, fra.forwardRate(), TOLERANCE, "ASX future " + (i + 1));
                assertEquals(100.0 - rates.get(i), futures.get(i).impliedQuote(), TOLERANCE);
            }
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    private static IborIndex euribor(Period tenor) {
        return euribor(tenor, flatPlaceholder());
    }

    private static jquant.termstructures.YieldTermStructure flatPlaceholder() {
        return new jquant.termstructures.yield.FlatForward(
                new Date(15, Month.JANUARY, 2025), 0.04, new Actual360(false),
                jquant.Compounding.Continuous, Frequency.ANNUAL);
    }

    private static IborIndex euribor(Period tenor, jquant.termstructures.YieldTermStructure curve) {
        return new IborIndex("Euribor", tenor, 2, new Currency(), new Target(),
                BusinessDayConvention.MODIFIED_FOLLOWING, true, new Actual360(false),
                new Handle<>(curve, false));
    }
}
