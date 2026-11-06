package termstructures;

import jquant.Compounding;
import jquant.Handle;
import jquant.Settings;
import jquant.math.interpolations.LogLinearInterpolation;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.yield.DepositRateHelper;
import jquant.termstructures.yield.PiecewiseYieldCurve;
import jquant.termstructures.yield.RateHelper;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deposit bootstrap of {@code testLogLinearDiscountConsistency}.
 * The published inputs are the six Euribor deposit quotes in
 * {@code test-suite/piecewiseyieldcurve.cpp}. Swaps, FRAs, futures and bonds
 * from that fixture are not built. The discount at each pillar is the deposit
 * inversion {@code 1 / (1 + quote * Actual/360)}, and {@code impliedQuote}
 * must recover the quote. Tolerance is the official {@code 1e-9}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/piecewiseyieldcurve.cpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/ratehelpers.cpp
 */
public class PiecewiseYieldCurveTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    public void testLogLinearMidpointIsGeometricMean() {
        // Two nodes are legal. The midpoint of (0, 1) and (1, e) is exp(0.5).
        LogLinearInterpolation two = new LogLinearInterpolation(
                new double[] {0.0, 1.0},
                new double[] {1.0, Math.E});
        assertEquals(Math.exp(0.5), two.value(0.5, true), 1.0e-12);

        LogLinearInterpolation three = new LogLinearInterpolation(
                new double[] {0.0, 1.0, 2.0},
                new double[] {1.0, Math.E, Math.E * Math.E});
        assertEquals(Math.exp(0.5), three.value(0.5, true), 1.0e-12);
        assertEquals(Math.E, three.value(1.0, true), 1.0e-12);
    }

    @Test
    public void testTargetHolidaysUsedByTheDepositFixture() {
        Calendar target = new Target();
        assertTrue(target.isHoliday(new Date(1, Month.JANUARY, 2025)));
        assertTrue(target.isHoliday(new Date(18, Month.APRIL, 2025)));
        assertTrue(target.isHoliday(new Date(21, Month.APRIL, 2025)));
        assertTrue(target.isHoliday(new Date(1, Month.MAY, 2025)));
        assertTrue(target.isHoliday(new Date(25, Month.DECEMBER, 2025)));
        assertTrue(target.isHoliday(new Date(26, Month.DECEMBER, 2025)));
        assertTrue(target.isBusinessDay(new Date(15, Month.JANUARY, 2025)));
        assertTrue(target.isBusinessDay(new Date(17, Month.JANUARY, 2025)));
    }

    @Test
    public void testLogLinearDiscountDeposits() {
        // Wednesday 15 Jan 2025. Settlement is two TARGET business days later.
        Date today = new Date(15, Month.JANUARY, 2025);
        Settings.instance.evaluationDate().equal(today);
        Calendar target = new Target();
        DayCounter depositDayCounter = new Actual360(false);
        Date settlement = target.advance(today, 2, TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        assertTrue(TimeUtils.equals(settlement, new Date(17, Month.JANUARY, 2025)), settlement.toString());

        // n, unit, percent quote, expected maturity. Same rows as depositData[].
        Object[][] rows = {
                {1, TimeUnit.WEEKS, 4.559, new Date(24, Month.JANUARY, 2025)},
                {1, TimeUnit.MONTHS, 4.581, new Date(17, Month.FEBRUARY, 2025)},
                {2, TimeUnit.MONTHS, 4.573, new Date(17, Month.MARCH, 2025)},
                {3, TimeUnit.MONTHS, 4.557, new Date(17, Month.APRIL, 2025)},
                {6, TimeUnit.MONTHS, 4.496, new Date(17, Month.JULY, 2025)},
                {9, TimeUnit.MONTHS, 4.490, new Date(17, Month.OCTOBER, 2025)}
        };
        List<RateHelper> helpers = new ArrayList<>();
        SimpleQuote[] quotes = new SimpleQuote[rows.length];
        for (int i = 0; i < rows.length; i++) {
            quotes[i] = new SimpleQuote(((Number) rows[i][2]).doubleValue() / 100.0);
            helpers.add(new DepositRateHelper(
                    new Handle<>(quotes[i], true),
                    new Period((Integer) rows[i][0], (TimeUnit) rows[i][1]),
                    2,
                    target,
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    false,
                    depositDayCounter));
            assertTrue(TimeUtils.equals(helpers.get(i).pillarDate(), (Date) rows[i][3]),
                    helpers.get(i).pillarDate().toString());
        }

        PiecewiseYieldCurve curve = new PiecewiseYieldCurve(
                settlement, helpers, new Actual360(false));
        List<Date> nodes = curve.dates();
        assertEquals(rows.length + 1, nodes.size());
        assertTrue(TimeUtils.equals(nodes.get(0), settlement));
        assertTrue(TimeUtils.equals(curve.maxDate(), (Date) rows[rows.length - 1][3]));

        double previousDf = 1.0;
        for (int i = 0; i < rows.length; i++) {
            double quote = quotes[i].value();
            Date maturity = (Date) rows[i][3];
            assertTrue(TimeUtils.equals(nodes.get(i + 1), maturity));
            assertEquals(quote, helpers.get(i).impliedQuote(), TOLERANCE);

            double yearFraction = depositDayCounter.yearFraction(
                    settlement, maturity, new Date(), new Date());
            double discount = 1.0 / (1.0 + quote * yearFraction);
            assertEquals(discount, curve.discount(maturity, false), TOLERANCE);
            assertEquals(-Math.log(discount) / yearFraction,
                    curve.zeroRate(maturity, depositDayCounter, Compounding.Continuous,
                            Frequency.ANNUAL, false).rate(),
                    TOLERANCE);
            assertTrue(Math.abs(curve.discount(maturity, false) - previousDf) > 1.0e-4);
            previousDf = discount;
            assertEquals(quote, quotes[i].value(), 0.0);
        }

        // One day before the first pillar is on the log-linear segment from
        // the reference date, not equal to the pillar discount.
        Date beforeFirst = ((Date) rows[0][3]).substract(1);
        double t1 = depositDayCounter.yearFraction(settlement, (Date) rows[0][3], new Date(), new Date());
        double df1 = 1.0 / (1.0 + quotes[0].value() * t1);
        double tBefore = depositDayCounter.yearFraction(settlement, beforeFirst, new Date(), new Date());
        double expectedBefore = Math.pow(df1, tBefore / t1);
        assertEquals(expectedBefore, curve.discount(beforeFirst, false), TOLERANCE);
        assertTrue(Math.abs(curve.discount(beforeFirst, false) - df1) > 1.0e-6);

        // Between the 1-week and 1-month pillars the discount is the log-linear
        // blend of the two analytical node discounts.
        Date between = new Date(31, Month.JANUARY, 2025);
        double t2 = depositDayCounter.yearFraction(settlement, (Date) rows[1][3], new Date(), new Date());
        double df2 = 1.0 / (1.0 + quotes[1].value() * t2);
        double t = depositDayCounter.yearFraction(settlement, between, new Date(), new Date());
        double logDf = Math.log(df1) + (t - t1) / (t2 - t1) * (Math.log(df2) - Math.log(df1));
        assertEquals(Math.exp(logDf), curve.discount(between, false), TOLERANCE);
        assertTrue(Math.abs(curve.discount(between, false) - df1) > 1.0e-4);
        assertTrue(Math.abs(curve.discount(between, false) - df2) > 1.0e-4);

        assertThrows(IllegalArgumentException.class,
                () -> curve.discount(((Date) rows[rows.length - 1][3]).add(1), false));
    }
}
