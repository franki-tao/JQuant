package termstructures;

import jquant.Currency;
import jquant.Handle;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.instruments.ForwardRateAgreement;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.FuturesRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.termstructures.yield.SwapRateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.IMM;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IMM futures from {@code test-suite/piecewiseyieldcurve.cpp}. The published
 * rates are 4.581, 4.573 and 4.557. The helper quote is {@code 100 - rate}.
 * After a futures-only log-linear bootstrap, each FRA forward equals that
 * rate. Tolerance is the official {@code 1e-9}. Deposits and swaps from the
 * same fixture are then bootstrapped on one curve with the futures, and every
 * helper's implied quote is compared with its market quote.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/QuantLib-v1.16/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/termstructures/yield/ratehelpers.cpp
 */
public class FuturesRateHelperTest {
    private static final double TOLERANCE = 1.0e-9;
    private static final double[] FUTURES_RATES = {4.581, 4.573, 4.557};
    private static final double[] DEPOSIT_RATES = {4.559, 4.581, 4.573, 4.557, 4.496, 4.490};
    private static final int[] DEPOSIT_LENGTHS = {1, 1, 2, 3, 6, 9};
    private static final TimeUnit[] DEPOSIT_UNITS = {
            TimeUnit.WEEKS, TimeUnit.MONTHS, TimeUnit.MONTHS, TimeUnit.MONTHS,
            TimeUnit.MONTHS, TimeUnit.MONTHS
    };
    private static final double[] SWAP_RATES = {
            4.54, 4.63, 4.75, 4.86, 4.99, 5.11, 5.23, 5.33, 5.41, 5.47, 5.60, 5.75, 5.89, 5.95, 5.96
    };
    private static final int[] SWAP_YEARS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30};

    @Test
    public void testImmFuturesRepriceToTheQuote() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS));
            List<Date> immDates = new ArrayList<>();
            List<RateHelper> futures = new ArrayList<>();
            Date immDate = new Date();
            for (double rate : FUTURES_RATES) {
                immDate = IMM.nextDate(immDate, false);
                if (TimeUtils.less(euribor3m.fixingDate(immDate), today))
                    immDate = IMM.nextDate(immDate, false);
                assertTrue(IMM.isIMMdate(immDate, false));
                immDates.add(immDate);
                futures.add(new FuturesRateHelper(
                        new Handle<>(new SimpleQuote(100.0 - rate), false), immDate, euribor3m));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> new FuturesRateHelper(new Handle<>(new SimpleQuote(95.0), false),
                            new Date(16, Month.JANUARY, 2025), euribor3m));

            PiecewiseYieldCurve curve = new PiecewiseYieldCurve(settlement, futures, new Actual360(false));
            IborIndex forecasting = euribor(new Period(3, TimeUnit.MONTHS), curve);
            for (int i = 0; i < FUTURES_RATES.length; i++) {
                double expected = FUTURES_RATES[i] / 100.0;
                Date start = immDates.get(i);
                Date end = target.advance(start, 3, TimeUnit.MONTHS,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true);
                ForwardRateAgreement fra = new ForwardRateAgreement(forecasting, start, end,
                        ForwardRateAgreement.Position.Long, expected, 100.0, curve, false);
                assertEquals(expected, fra.forwardRate(), TOLERANCE, "IMM future " + (i + 1));
                assertEquals(100.0 - FUTURES_RATES[i], futures.get(i).impliedQuote(), TOLERANCE);
            }

            SimpleQuote adjustment = new SimpleQuote(0.001);
            FuturesRateHelper adjusted = new FuturesRateHelper(
                    new Handle<>(new SimpleQuote(100.0 - FUTURES_RATES[0]), false),
                    immDates.get(0), euribor3m, new Handle<>(adjustment, false));
            assertEquals(0.001, adjusted.convexityAdjustment(), 0.0);
            PiecewiseYieldCurve adjustedCurve = new PiecewiseYieldCurve(
                    settlement, List.of(adjusted), new Actual360(false));
            Date start = immDates.get(0);
            Date end = euribor3m.fixingCalendar().advance(start, euribor3m.tenor(),
                    euribor3m.businessDayConvention(), false);
            double tau = euribor3m.dayCounter().yearFraction(start, end, start, end);
            double forward = (adjustedCurve.discount(start, false)
                    / adjustedCurve.discount(end, false) - 1.0) / tau;
            // forward = 0.04581 - 0.001
            assertEquals(0.04481, forward, TOLERANCE);
            adjustment.setValue(0.002);
            double bumped = (adjustedCurve.discount(start, false)
                    / adjustedCurve.discount(end, false) - 1.0) / tau;
            // forward = 0.04581 - 0.002
            assertEquals(0.04381, bumped, TOLERANCE);
        } finally {
            Settings.instance.evaluationDate().equal(saved);
        }
    }

    @Test
    public void testDepositsFuturesAndSwapsReprice() {
        Date saved = Settings.instance.evaluationDate().Date().copy();
        try {
            Date today = new Date(15, Month.JANUARY, 2025);
            Settings.instance.evaluationDate().equal(today);
            Calendar target = new Target();
            DayCounter actual360 = new Actual360(false);
            Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                    BusinessDayConvention.FOLLOWING, false);
            IborIndex euribor6m = euribor(new Period(6, TimeUnit.MONTHS));
            IborIndex euribor3m = euribor(new Period(3, TimeUnit.MONTHS));
            List<RateHelper> helpers = new ArrayList<>();
            List<Double> quotes = new ArrayList<>();
            for (int i = 0; i < DEPOSIT_RATES.length; i++) {
                double quote = DEPOSIT_RATES[i] / 100.0;
                quotes.add(quote);
                helpers.add(new DepositRateHelper(new Handle<>(new SimpleQuote(quote), false),
                        new Period(DEPOSIT_LENGTHS[i], DEPOSIT_UNITS[i]), 2, target,
                        BusinessDayConvention.MODIFIED_FOLLOWING, true, actual360));
            }
            Date immDate = new Date();
            for (double rate : FUTURES_RATES) {
                immDate = IMM.nextDate(immDate, false);
                if (TimeUtils.less(euribor3m.fixingDate(immDate), today))
                    immDate = IMM.nextDate(immDate, false);
                double price = 100.0 - rate;
                quotes.add(price);
                helpers.add(new FuturesRateHelper(
                        new Handle<>(new SimpleQuote(price), false), immDate, euribor3m));
            }
            for (int i = 0; i < SWAP_RATES.length; i++) {
                double quote = SWAP_RATES[i] / 100.0;
                quotes.add(quote);
                helpers.add(new SwapRateHelper(new Handle<>(new SimpleQuote(quote), false),
                        new Period(SWAP_YEARS[i], TimeUnit.YEARS), target, Frequency.ANNUAL,
                        BusinessDayConvention.UNADJUSTED, new Thirty360(Thirty360.Convention.BondBasis),
                        euribor6m));
            }
            new PiecewiseYieldCurve(settlement, helpers, actual360);
            for (int i = 0; i < helpers.size(); i++)
                assertEquals(quotes.get(i), helpers.get(i).impliedQuote(), TOLERANCE, "helper " + i);
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
