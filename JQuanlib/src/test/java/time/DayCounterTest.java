package time;

import jquant.Settings;
import jquant.time.*;
import jquant.time.calendars.Canada;
import jquant.time.calendars.China;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuantLib {@code test-suite/daycounters.cpp} cases whose conventions this
 * tree already implements. Expected year fractions and day counts are the
 * published literals, not values computed by the counter under test.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/daycounters.cpp
 */
public class DayCounterTest {
    private static final double ACT_ACT_TOLERANCE = 1.0e-10;
    private static final double LINEAR_TOLERANCE = 1.0e-12;
    private static final double CONSISTENCY_TOLERANCE = 1.0e-14;
    private static final Date NULL_DATE = new Date();

    /**
     * Demo entry kept for the previous {@code main} callers. It runs this
     * JUnit 5 suite instead of printing a day count.
     */
    public static void main(String[] args) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(DayCounterTest.class))
                        .build(),
                listener);
        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        if (summary.getTotalFailureCount() > 0) {
            throw new IllegalStateException(summary.getTotalFailureCount()
                    + " day-counter tests failed");
        }
    }

    @Test
    public void testActualActual() {
        // QuantLib testActualActual. ISMA rows carry the reference period.
        // ISDA and AFB rows use a null reference period.
        assertActAct(ActualActual.Convention.ISDA,
                d(1, Month.NOVEMBER, 2003), d(1, Month.MAY, 2004),
                NULL_DATE, NULL_DATE, 0.497724380567);
        assertActAct(ActualActual.Convention.ISMA,
                d(1, Month.NOVEMBER, 2003), d(1, Month.MAY, 2004),
                d(1, Month.NOVEMBER, 2003), d(1, Month.MAY, 2004), 0.500000000000);
        assertActAct(ActualActual.Convention.AFB,
                d(1, Month.NOVEMBER, 2003), d(1, Month.MAY, 2004),
                NULL_DATE, NULL_DATE, 0.497267759563);

        assertActAct(ActualActual.Convention.ISDA,
                d(1, Month.FEBRUARY, 1999), d(1, Month.JULY, 1999),
                NULL_DATE, NULL_DATE, 0.410958904110);
        assertActAct(ActualActual.Convention.ISMA,
                d(1, Month.FEBRUARY, 1999), d(1, Month.JULY, 1999),
                d(1, Month.JULY, 1998), d(1, Month.JULY, 1999), 0.410958904110);
        assertActAct(ActualActual.Convention.AFB,
                d(1, Month.FEBRUARY, 1999), d(1, Month.JULY, 1999),
                NULL_DATE, NULL_DATE, 0.410958904110);

        assertActAct(ActualActual.Convention.ISDA,
                d(1, Month.JULY, 1999), d(1, Month.JULY, 2000),
                NULL_DATE, NULL_DATE, 1.001377348600);
        assertActAct(ActualActual.Convention.ISMA,
                d(1, Month.JULY, 1999), d(1, Month.JULY, 2000),
                d(1, Month.JULY, 1999), d(1, Month.JULY, 2000), 1.000000000000);
        assertActAct(ActualActual.Convention.AFB,
                d(1, Month.JULY, 1999), d(1, Month.JULY, 2000),
                NULL_DATE, NULL_DATE, 1.000000000000);

        assertActAct(ActualActual.Convention.ISDA,
                d(15, Month.AUGUST, 2002), d(15, Month.JULY, 2003),
                NULL_DATE, NULL_DATE, 0.915068493151);
        assertActAct(ActualActual.Convention.ISMA,
                d(15, Month.AUGUST, 2002), d(15, Month.JULY, 2003),
                d(15, Month.JANUARY, 2003), d(15, Month.JULY, 2003), 0.915760869565);
        assertActAct(ActualActual.Convention.AFB,
                d(15, Month.AUGUST, 2002), d(15, Month.JULY, 2003),
                NULL_DATE, NULL_DATE, 0.915068493151);

        assertActAct(ActualActual.Convention.ISDA,
                d(15, Month.JULY, 2003), d(15, Month.JANUARY, 2004),
                NULL_DATE, NULL_DATE, 0.504004790778);
        assertActAct(ActualActual.Convention.ISMA,
                d(15, Month.JULY, 2003), d(15, Month.JANUARY, 2004),
                d(15, Month.JULY, 2003), d(15, Month.JANUARY, 2004), 0.500000000000);
        assertActAct(ActualActual.Convention.AFB,
                d(15, Month.JULY, 2003), d(15, Month.JANUARY, 2004),
                NULL_DATE, NULL_DATE, 0.504109589041);

        assertActAct(ActualActual.Convention.ISDA,
                d(30, Month.JULY, 1999), d(30, Month.JANUARY, 2000),
                NULL_DATE, NULL_DATE, 0.503892506924);
        assertActAct(ActualActual.Convention.ISMA,
                d(30, Month.JULY, 1999), d(30, Month.JANUARY, 2000),
                d(30, Month.JULY, 1999), d(30, Month.JANUARY, 2000), 0.500000000000);
        assertActAct(ActualActual.Convention.AFB,
                d(30, Month.JULY, 1999), d(30, Month.JANUARY, 2000),
                NULL_DATE, NULL_DATE, 0.504109589041);

        assertActAct(ActualActual.Convention.ISDA,
                d(30, Month.JANUARY, 2000), d(30, Month.JUNE, 2000),
                NULL_DATE, NULL_DATE, 0.415300546448);
        assertActAct(ActualActual.Convention.ISMA,
                d(30, Month.JANUARY, 2000), d(30, Month.JUNE, 2000),
                d(30, Month.JANUARY, 2000), d(30, Month.JULY, 2000), 0.417582417582);
        assertActAct(ActualActual.Convention.AFB,
                d(30, Month.JANUARY, 2000), d(30, Month.JUNE, 2000),
                NULL_DATE, NULL_DATE, 0.41530054644);
    }

    @Test
    public void testActualActualIsma() {
        // QuantLib testActualActualIsma. The numerators and denominators are
        // the published day counts, not a call into the counter.
        assertIsmaSchedule(
                false, Frequency.SEMIANNUAL,
                d(30, Month.JANUARY, 1999), d(30, Month.JUNE, 2000),
                d(30, Month.JULY, 1999), d(30, Month.JANUARY, 2000),
                d(30, Month.JANUARY, 2000), d(30, Month.JUNE, 2000),
                152.0 / (182.0 * 2));
        assertIsmaSchedule(
                true, Frequency.QUARTERLY,
                d(31, Month.MAY, 1999), d(30, Month.APRIL, 2000),
                d(31, Month.AUGUST, 1999), d(30, Month.NOVEMBER, 1999),
                d(30, Month.NOVEMBER, 1999), d(30, Month.APRIL, 2000),
                91.0 / (91.0 * 4) + 61.0 / (92.0 * 4));
        assertIsmaSchedule(
                false, Frequency.QUARTERLY,
                d(31, Month.MAY, 1999), d(30, Month.APRIL, 2000),
                d(31, Month.AUGUST, 1999), d(30, Month.NOVEMBER, 1999),
                d(30, Month.NOVEMBER, 1999), d(30, Month.APRIL, 2000),
                91.0 / (91.0 * 4) + 61.0 / (90.0 * 4));
    }

    @Test
    public void testActualActualWithLongFirstAndShortLastCoupon() {
        Schedule schedule = new Schedule(
                List.of(
                        d(6, Month.DECEMBER, 2024),
                        d(15, Month.JUNE, 2025),
                        d(15, Month.DECEMBER, 2025),
                        d(15, Month.JUNE, 2026),
                        d(15, Month.OCTOBER, 2026)),
                new NullCalendar(),
                BusinessDayConvention.UNADJUSTED,
                Optional.of(BusinessDayConvention.UNADJUSTED),
                Optional.of(new Period(6, TimeUnit.MONTHS)),
                Optional.of(DateGenerationRule.BACKWARD),
                Optional.of(false),
                List.of(false, true, true, false));
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        assertEquals(0.5, yearFraction(dayCounter,
                        d(15, Month.DECEMBER, 2025), d(15, Month.JUNE, 2026)),
                ACT_ACT_TOLERANCE);
        assertEquals(1.0 / 3.0, yearFraction(dayCounter,
                        d(15, Month.JUNE, 2026), d(15, Month.OCTOBER, 2026)),
                ACT_ACT_TOLERANCE);
    }

    @Test
    public void testActualActualWithSemiannualSchedule() {
        // QuantLib testActualActualWithSemiannualSchedule. endOfMonth is true
        // for the coupon that must be exactly one half year.
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Date fromDate = d(10, Month.JANUARY, 2017);
        Date firstCoupon = d(31, Month.AUGUST, 2017);
        Schedule schedule = new MakeSchedule()
                .from(fromDate)
                .withFirstDate(firstCoupon)
                .to(d(28, Month.FEBRUARY, 2026))
                .withFrequency(Frequency.SEMIANNUAL)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .backwards()
                .endOfMonth(true)
                .toSchedule();
        assertSameDate(firstCoupon, schedule.date(1));
        assertSameDate(d(28, Month.FEBRUARY, 2018), schedule.date(2));
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        DayCounter noSchedule = new ActualActual(ActualActual.Convention.ISMA);
        Date referencePeriodStart = schedule.date(1);
        Date referencePeriodEnd = schedule.date(2);
        assertEquals(0.0, yearFraction(dayCounter, referencePeriodStart, referencePeriodStart),
                ACT_ACT_TOLERANCE);
        assertEquals(0.0, yearFraction(noSchedule, referencePeriodStart, referencePeriodStart),
                ACT_ACT_TOLERANCE);
        assertEquals(0.5, yearFraction(dayCounter, referencePeriodStart, referencePeriodEnd),
                ACT_ACT_TOLERANCE);
        assertEquals(0.5, yearFraction(noSchedule, referencePeriodStart, referencePeriodEnd,
                referencePeriodStart, referencePeriodEnd), ACT_ACT_TOLERANCE);

        for (Date testDate = schedule.date(1); TimeUtils.less(testDate, referencePeriodEnd);
             testDate = calendar.advance(testDate, 1, TimeUnit.DAYS,
                     BusinessDayConvention.FOLLOWING, false)) {
            double difference = yearFraction(dayCounter, testDate, referencePeriodEnd,
                    referencePeriodStart, referencePeriodEnd)
                    - yearFraction(dayCounter, testDate, referencePeriodEnd);
            assertEquals(0.0, difference, ACT_ACT_TOLERANCE, testDate.toString());
        }

        double calculatedYearFraction = yearFraction(dayCounter, fromDate, firstCoupon);
        double expectedYearFraction = 0.5
                + dayCounter.dayCount(fromDate, d(28, Month.FEBRUARY, 2017))
                / (2.0 * dayCounter.dayCount(d(31, Month.AUGUST, 2016), d(28, Month.FEBRUARY, 2017)));
        assertEquals(expectedYearFraction, calculatedYearFraction, ACT_ACT_TOLERANCE);
    }

    @Test
    public void testActualActualWithAnnualSchedule() {
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Schedule schedule = new MakeSchedule()
                .from(d(10, Month.JANUARY, 2017))
                .withFirstDate(d(31, Month.AUGUST, 2017))
                .to(d(28, Month.FEBRUARY, 2026))
                .withFrequency(Frequency.ANNUAL)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .backwards()
                .endOfMonth(false)
                .toSchedule();
        Date referencePeriodStart = schedule.date(1);
        Date referencePeriodEnd = schedule.date(2);
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        for (Date testDate = schedule.date(1); TimeUtils.less(testDate, referencePeriodEnd);
             testDate = calendar.advance(testDate, 1, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false)) {
            double difference = ismaWithReference(dayCounter, testDate, referencePeriodEnd,
                    referencePeriodStart, referencePeriodEnd)
                    - yearFraction(dayCounter, testDate, referencePeriodEnd);
            assertEquals(0.0, difference, ACT_ACT_TOLERANCE, testDate.toString());
        }
    }

    @Test
    public void testActualActualOutOfScheduleRange() {
        // QuantLib testActualActualOutOfScheduleRange. China(IB) is the
        // schedule calendar; the assertion is the out-of-range rejection,
        // not a Spring Festival holiday date.
        Date today = d(10, Month.NOVEMBER, 2020);
        Date previous = Settings.instance.evaluationDate().Date();
        Settings.instance.evaluationDate().equal(today);
        try {
            Schedule schedule = new MakeSchedule()
                    .from(d(21, Month.MAY, 2019))
                    .to(d(21, Month.MAY, 2029))
                    .withTenor(new Period(1, TimeUnit.YEARS))
                    .withCalendar(new China(China.Market.IB))
                    .withConvention(BusinessDayConvention.UNADJUSTED)
                    .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                    .backwards()
                    .endOfMonth(false)
                    .toSchedule();
            DayCounter dayCounter = new ActualActual(ActualActual.Convention.Bond, schedule);
            assertThrows(IllegalArgumentException.class,
                    () -> yearFraction(dayCounter, today, today.add(new Period(9, TimeUnit.YEARS))));
        } finally {
            Settings.instance.evaluationDate().equal(previous);
        }
    }

    @Test
    public void testActualActualScheduleReferenceFraction() {
        // QuantLib testActualActualWithSchedule published values.
        Calendar calendar = new Canada();
        Schedule schedule = new MakeSchedule()
                .from(d(17, Month.JANUARY, 2017))
                .withFirstDate(d(31, Month.AUGUST, 2017))
                .to(d(28, Month.FEBRUARY, 2026))
                .withFrequency(Frequency.SEMIANNUAL)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .backwards()
                .endOfMonth(true)
                .toSchedule();
        assertSameDate(d(17, Month.JANUARY, 2017), schedule.date(0));
        assertSameDate(d(31, Month.AUGUST, 2017), schedule.date(1));

        Date firstCouponDate = schedule.date(1);
        Date quasiCouponDate2 = schedule.calendar().advance(
                firstCouponDate, TimeUtils.negetive(schedule.tenor()),
                schedule.businessDayConvention(), schedule.endOfMonth());
        Date quasiCouponDate1 = schedule.calendar().advance(
                quasiCouponDate2, TimeUtils.negetive(schedule.tenor()),
                schedule.businessDayConvention(), schedule.endOfMonth());
        assertSameDate(d(28, Month.FEBRUARY, 2017), quasiCouponDate2);
        assertSameDate(d(31, Month.AUGUST, 2016), quasiCouponDate1);

        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        Date issueDate = schedule.date(0);
        double withReference = yearFraction(dayCounter, issueDate, firstCouponDate,
                quasiCouponDate2, firstCouponDate);
        double noReference = yearFraction(dayCounter, issueDate, firstCouponDate);
        double total = ismaWithReference(dayCounter, issueDate, quasiCouponDate2,
                quasiCouponDate1, quasiCouponDate2) + 0.5;
        assertEquals(0.6160220994, total, ACT_ACT_TOLERANCE);
        assertEquals(0.6160220994, withReference, ACT_ACT_TOLERANCE);
        assertEquals(withReference, noReference, ACT_ACT_TOLERANCE);

        Date settlementDate = d(29, Month.JANUARY, 2017);
        double firstQuasi = ismaWithReference(dayCounter, issueDate, settlementDate,
                quasiCouponDate1, quasiCouponDate2);
        assertEquals(0.03314917127071823, firstQuasi, ACT_ACT_TOLERANCE);
        assertEquals(firstQuasi, yearFraction(dayCounter, issueDate, settlementDate), ACT_ACT_TOLERANCE);
        double remainder = yearFraction(dayCounter, settlementDate, firstCouponDate);
        assertEquals(0.6160220994, firstQuasi + remainder, ACT_ACT_TOLERANCE);
    }

    @Test
    public void testSimple() {
        Period[] periods = {
                new Period(3, TimeUnit.MONTHS),
                new Period(6, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS)
        };
        double[] expected = {0.25, 0.5, 1.0};
        DayCounter dayCounter = new SimpleDayCounter();
        Date last = d(31, Month.DECEMBER, 2005);
        for (Date start = d(1, Month.JANUARY, 2002); !TimeUtils.greater(start, last); start = start.add(1)) {
            for (int i = 0; i < periods.length; i++) {
                Date end = start.add(periods[i]);
                assertEquals(expected[i], yearFraction(dayCounter, start, end), LINEAR_TOLERANCE,
                        start + " to " + end);
            }
        }
    }

    @Test
    public void testOne() {
        Period[] periods = {
                new Period(3, TimeUnit.MONTHS),
                new Period(6, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS)
        };
        DayCounter dayCounter = new OneDayCounter();
        Date last = d(31, Month.DECEMBER, 2004);
        for (Date start = d(1, Month.JANUARY, 2004); !TimeUtils.greater(start, last); start = start.add(1)) {
            for (Period period : periods) {
                assertEquals(1.0, yearFraction(dayCounter, start, start.add(period)), LINEAR_TOLERANCE);
            }
        }
    }

    @Test
    public void testThirty365() {
        // QuantLib testThirty365. Day counts are the published integers.
        int[][] cases = {
                {17, 6, 2011, 30, 12, 2012, 553},
                {31, 3, 2025, 30, 4, 2025, 30},
                {30, 9, 2024, 31, 3, 2025, 180},
                {30, 3, 2025, 31, 3, 2025, 0}
        };
        DayCounter dayCounter = new Thirty365();
        for (int[] row : cases) {
            Date start = d(row[0], Month.fromValue(row[1]), row[2]);
            Date end = d(row[3], Month.fromValue(row[4]), row[5]);
            assertEquals(row[6], dayCounter.dayCount(start, end), start + " to " + end);
            assertEquals(row[6] / 365.0, yearFraction(dayCounter, start, end), LINEAR_TOLERANCE);
        }
    }

    @Test
    public void testThirty360BondBasis() {
        // ISDA 30/360 examples copied by QuantLib testThirty360_BondBasis.
        // https://www.isda.org/2008/12/22/30-360-day-count-conventions/
        DayCounter dayCounter = new Thirty360(Thirty360.Convention.BondBasis);
        assertThirty360(dayCounter, bondBasisCases());
    }

    @Test
    public void testThirty360EurobondBasis() {
        DayCounter dayCounter = new Thirty360(Thirty360.Convention.EurobondBasis);
        assertThirty360(dayCounter, eurobondCases());
    }

    @Test
    public void testThirty360Usa() {
        DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        assertThirty360(dayCounter, usaCases());
    }

    @Test
    public void testThirty360Isda() {
        Thirty360 first = new Thirty360(Thirty360.Convention.ISDA, d(20, Month.AUGUST, 2009));
        assertThirty360(first, new int[][]{
                {20, 8, 2006, 20, 2, 2007, 180},
                {20, 2, 2007, 20, 8, 2007, 180},
                {20, 8, 2007, 20, 2, 2008, 180},
                {20, 2, 2008, 20, 8, 2008, 180},
                {20, 8, 2008, 20, 2, 2009, 180},
                {20, 2, 2009, 20, 8, 2009, 180}
        });
        Thirty360 second = new Thirty360(Thirty360.Convention.ISDA, d(29, Month.FEBRUARY, 2012));
        assertThirty360(second, new int[][]{
                {28, 2, 2006, 31, 8, 2006, 180},
                {31, 8, 2006, 28, 2, 2007, 180},
                {28, 2, 2007, 31, 8, 2007, 180},
                {31, 8, 2007, 29, 2, 2008, 180},
                {29, 2, 2008, 31, 8, 2008, 180},
                {31, 8, 2008, 28, 2, 2009, 180},
                {28, 2, 2009, 31, 8, 2009, 180},
                {31, 8, 2009, 28, 2, 2010, 180},
                {28, 2, 2010, 31, 8, 2010, 180},
                {31, 8, 2010, 28, 2, 2011, 180},
                {28, 2, 2011, 31, 8, 2011, 180},
                {31, 8, 2011, 29, 2, 2012, 179}
        });
        Thirty360 third = new Thirty360(Thirty360.Convention.ISDA, d(29, Month.FEBRUARY, 2008));
        assertThirty360(third, new int[][]{
                {31, 1, 2006, 28, 2, 2006, 30},
                {30, 1, 2006, 28, 2, 2006, 30},
                {28, 2, 2006, 3, 3, 2006, 3},
                {14, 2, 2006, 28, 2, 2006, 16},
                {30, 9, 2006, 31, 10, 2006, 30},
                {31, 10, 2006, 28, 11, 2006, 28},
                {31, 8, 2007, 28, 2, 2008, 178},
                {28, 2, 2008, 28, 8, 2008, 180},
                {28, 2, 2008, 30, 8, 2008, 182},
                {28, 2, 2008, 31, 8, 2008, 182},
                {28, 2, 2007, 28, 2, 2008, 358},
                {28, 2, 2007, 29, 2, 2008, 359},
                {29, 2, 2008, 28, 2, 2009, 360},
                {29, 2, 2008, 30, 3, 2008, 30},
                {29, 2, 2008, 31, 3, 2008, 30}
        });
    }

    @Test
    public void testAct366() {
        assertLinearFractions(new Actual366(), new double[]{
                0.00819672131147541,
                1.27322404371585,
                0.587431693989071,
                1.0000000000000,
                1.00273224043716,
                0.0382513661202186,
                0.191256830601093,
                0.172131147540984,
                -0.16120218579235,
                0.16120218579235,
                0.19672131147541,
                0.920765027322404,
                2.21584699453552,
                6.84426229508197
        });
    }

    @Test
    public void testAct36525() {
        assertLinearFractions(new Actual36525(), new double[]{
                0.0082135523613963,
                1.27583846680356,
                0.588637919233402,
                1.00205338809035,
                1.00479123887748,
                0.0383299110198494,
                0.191649555099247,
                0.172484599589322,
                -0.161533196440794,
                0.161533196440794,
                0.197125256673511,
                0.922655715263518,
                2.22039698836413,
                6.85831622176591
        });
    }

    @Test
    public void testActual365Canadian() {
        Actual365Fixed dayCounter = new Actual365Fixed(Actual365Fixed.Convention.Canadian);
        assertThrows(IllegalArgumentException.class, () -> yearFraction(dayCounter,
                d(10, Month.SEPTEMBER, 2018), d(10, Month.SEPTEMBER, 2019)));
        assertThrows(IllegalArgumentException.class, () -> yearFraction(dayCounter,
                d(10, Month.SEPTEMBER, 2018), d(12, Month.SEPTEMBER, 2018),
                d(10, Month.SEPTEMBER, 2018), d(15, Month.SEPTEMBER, 2018)));
        assertThrows(IllegalArgumentException.class, () -> yearFraction(dayCounter,
                d(8, Month.JANUARY, 2025), d(8, Month.JANUARY, 2027),
                d(8, Month.JANUARY, 2025), d(8, Month.JANUARY, 2027)));
    }

    @Test
    public void testActualConsistency() {
        // QuantLib testActualConsistency, without intraday dates.
        Date today = d(12, Month.JANUARY, 2022);
        Date[] dates = {
                d(1, Month.FEBRUARY, 2023), d(4, Month.FEBRUARY, 2023), d(16, Month.MAY, 2024),
                d(17, Month.DECEMBER, 2024), d(17, Month.DECEMBER, 2025), d(19, Month.DECEMBER, 2026),
                d(2, Month.JANUARY, 2027), d(13, Month.MARCH, 2028), d(15, Month.MAY, 2028),
                d(26, Month.JULY, 2036)
        };
        DayCounter actual365 = new Actual365Fixed();
        DayCounter actual366 = new Actual366();
        DayCounter actual364 = new Actual364();
        DayCounter actual36525 = new Actual36525();
        DayCounter actual360 = new Actual360(false);
        DayCounter actual360Incl = new Actual360(true);
        for (Date date : dates) {
            double t365 = yearFraction(actual365, today, date);
            assertEquals(0.0, t365 * 365.0 / 366.0 - yearFraction(actual366, today, date),
                    CONSISTENCY_TOLERANCE);
            assertEquals(0.0, t365 * 365.0 / 364.0 - yearFraction(actual364, today, date),
                    CONSISTENCY_TOLERANCE);
            assertEquals(0.0, t365 * 365.0 / 360.0 - yearFraction(actual360, today, date),
                    CONSISTENCY_TOLERANCE);
            assertEquals(0.0, t365 * 365.0 / 365.25 - yearFraction(actual36525, today, date),
                    CONSISTENCY_TOLERANCE);
            double t360Incl = yearFraction(actual360Incl, today, date);
            assertEquals(0.0, t365 * 365.0 / 360.0 - (t360Incl * 360.0 - 1.0) / 360.0,
                    CONSISTENCY_TOLERANCE);
        }
    }

    private static void assertActAct(ActualActual.Convention convention,
                                     Date start, Date end, Date refStart, Date refEnd,
                                     double expected) {
        DayCounter dayCounter = new ActualActual(convention);
        assertEquals(expected, yearFraction(dayCounter, start, end, refStart, refEnd), ACT_ACT_TOLERANCE,
                dayCounter.name() + " " + start + " to " + end);
    }

    private static void assertIsmaSchedule(boolean endOfMonth, Frequency frequency,
                                           Date accrual, Date maturity, Date firstCoupon, Date penultimate,
                                           Date start, Date end, double expected) {
        Schedule schedule = new MakeSchedule()
                .from(accrual)
                .to(maturity)
                .withFrequency(frequency)
                .withFirstDate(firstCoupon)
                .withNextToLastDate(penultimate)
                .withCalendar(new NullCalendar())
                .endOfMonth(endOfMonth)
                .toSchedule();
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        assertEquals(expected, yearFraction(dayCounter, start, end), ACT_ACT_TOLERANCE,
                start + " to " + end + " eom=" + endOfMonth);
    }

    private static double ismaWithReference(DayCounter dayCounter, Date start, Date end,
                                            Date refStart, Date refEnd) {
        double referenceDayCount = dayCounter.dayCount(refStart, refEnd);
        int couponsPerYear = (int) Math.round(365.0 / referenceDayCount);
        return dayCounter.dayCount(start, end) / (referenceDayCount * couponsPerYear);
    }

    private static void assertLinearFractions(DayCounter dayCounter, double[] expected) {
        Date[] dates = sharedLinearDates();
        assertEquals(expected.length, dates.length - 1);
        for (int i = 1; i < dates.length; i++) {
            assertEquals(expected[i - 1], yearFraction(dayCounter, dates[i - 1], dates[i]), LINEAR_TOLERANCE,
                    dates[i - 1] + " to " + dates[i]);
        }
    }

    private static Date[] sharedLinearDates() {
        return new Date[]{
                d(1, Month.FEBRUARY, 2002),
                d(4, Month.FEBRUARY, 2002),
                d(16, Month.MAY, 2003),
                d(17, Month.DECEMBER, 2003),
                d(17, Month.DECEMBER, 2004),
                d(19, Month.DECEMBER, 2005),
                d(2, Month.JANUARY, 2006),
                d(13, Month.MARCH, 2006),
                d(15, Month.MAY, 2006),
                d(17, Month.MARCH, 2006),
                d(15, Month.MAY, 2006),
                d(26, Month.JULY, 2006),
                d(28, Month.JUNE, 2007),
                d(16, Month.SEPTEMBER, 2009),
                d(26, Month.JULY, 2016)
        };
    }

    private static void assertThirty360(DayCounter dayCounter, int[][] rows) {
        for (int[] row : rows) {
            Date start = d(row[0], Month.fromValue(row[1]), row[2]);
            Date end = d(row[3], Month.fromValue(row[4]), row[5]);
            assertEquals(row[6], dayCounter.dayCount(start, end),
                    dayCounter.name() + " " + start + " to " + end);
        }
    }

    private static int[][] bondBasisCases() {
        return new int[][]{
                {20, 8, 2006, 20, 2, 2007, 180},
                {20, 2, 2007, 20, 8, 2007, 180},
                {20, 8, 2007, 20, 2, 2008, 180},
                {20, 2, 2008, 20, 8, 2008, 180},
                {20, 8, 2008, 20, 2, 2009, 180},
                {20, 2, 2009, 20, 8, 2009, 180},
                {31, 8, 2006, 28, 2, 2007, 178},
                {28, 2, 2007, 31, 8, 2007, 183},
                {31, 8, 2007, 29, 2, 2008, 179},
                {29, 2, 2008, 31, 8, 2008, 182},
                {31, 8, 2008, 28, 2, 2009, 178},
                {28, 2, 2009, 31, 8, 2009, 183},
                {31, 1, 2006, 28, 2, 2006, 28},
                {30, 1, 2006, 28, 2, 2006, 28},
                {28, 2, 2006, 3, 3, 2006, 5},
                {14, 2, 2006, 28, 2, 2006, 14},
                {30, 9, 2006, 31, 10, 2006, 30},
                {31, 10, 2006, 28, 11, 2006, 28},
                {31, 8, 2007, 28, 2, 2008, 178},
                {28, 2, 2008, 28, 8, 2008, 180},
                {28, 2, 2008, 30, 8, 2008, 182},
                {28, 2, 2008, 31, 8, 2008, 183},
                {26, 2, 2007, 28, 2, 2008, 362},
                {26, 2, 2007, 29, 2, 2008, 363},
                {29, 2, 2008, 28, 2, 2009, 359},
                {28, 2, 2008, 30, 3, 2008, 32},
                {28, 2, 2008, 31, 3, 2008, 33}
        };
    }

    private static int[][] eurobondCases() {
        return new int[][]{
                {20, 8, 2006, 20, 2, 2007, 180},
                {20, 2, 2007, 20, 8, 2007, 180},
                {20, 8, 2007, 20, 2, 2008, 180},
                {20, 2, 2008, 20, 8, 2008, 180},
                {20, 8, 2008, 20, 2, 2009, 180},
                {20, 2, 2009, 20, 8, 2009, 180},
                {28, 2, 2006, 31, 8, 2006, 182},
                {31, 8, 2006, 28, 2, 2007, 178},
                {28, 2, 2007, 31, 8, 2007, 182},
                {31, 8, 2007, 29, 2, 2008, 179},
                {29, 2, 2008, 31, 8, 2008, 181},
                {31, 8, 2008, 28, 2, 2009, 178},
                {28, 2, 2009, 31, 8, 2009, 182},
                {31, 8, 2009, 28, 2, 2010, 178},
                {28, 2, 2010, 31, 8, 2010, 182},
                {31, 8, 2010, 28, 2, 2011, 178},
                {28, 2, 2011, 31, 8, 2011, 182},
                {31, 8, 2011, 29, 2, 2012, 179},
                {31, 1, 2006, 28, 2, 2006, 28},
                {30, 1, 2006, 28, 2, 2006, 28},
                {28, 2, 2006, 3, 3, 2006, 5},
                {14, 2, 2006, 28, 2, 2006, 14},
                {30, 9, 2006, 31, 10, 2006, 30},
                {31, 10, 2006, 28, 11, 2006, 28},
                {31, 8, 2007, 28, 2, 2008, 178},
                {28, 2, 2008, 28, 8, 2008, 180},
                {28, 2, 2008, 30, 8, 2008, 182},
                {28, 2, 2008, 31, 8, 2008, 182},
                {26, 2, 2007, 28, 2, 2008, 362},
                {26, 2, 2007, 29, 2, 2008, 363},
                {29, 2, 2008, 28, 2, 2009, 359},
                {28, 2, 2008, 30, 3, 2008, 32},
                {28, 2, 2008, 31, 3, 2008, 32}
        };
    }

    private static int[][] usaCases() {
        return new int[][]{
                {20, 8, 2006, 20, 2, 2007, 180},
                {20, 2, 2007, 20, 8, 2007, 180},
                {20, 8, 2007, 20, 2, 2008, 180},
                {20, 2, 2008, 20, 8, 2008, 180},
                {20, 8, 2008, 20, 2, 2009, 180},
                {20, 2, 2009, 20, 8, 2009, 180},
                {31, 8, 2006, 28, 2, 2007, 178},
                {28, 2, 2007, 31, 8, 2007, 180},
                {31, 8, 2007, 29, 2, 2008, 179},
                {29, 2, 2008, 31, 8, 2008, 180},
                {31, 8, 2008, 28, 2, 2009, 178},
                {28, 2, 2009, 31, 8, 2009, 180},
                {31, 1, 2006, 28, 2, 2006, 28},
                {30, 1, 2006, 28, 2, 2006, 28},
                {28, 2, 2006, 3, 3, 2006, 3},
                {14, 2, 2006, 28, 2, 2006, 14},
                {30, 9, 2006, 31, 10, 2006, 30},
                {31, 10, 2006, 28, 11, 2006, 28},
                {31, 8, 2007, 28, 2, 2008, 178},
                {28, 2, 2008, 28, 8, 2008, 180},
                {28, 2, 2008, 30, 8, 2008, 182},
                {28, 2, 2008, 31, 8, 2008, 183},
                {26, 2, 2007, 28, 2, 2008, 362},
                {26, 2, 2007, 29, 2, 2008, 363},
                {29, 2, 2008, 28, 2, 2009, 360},
                {28, 2, 2008, 30, 3, 2008, 32},
                {28, 2, 2008, 31, 3, 2008, 33}
        };
    }

    private static void assertSameDate(Date expected, Date actual) {
        assertTrue(TimeUtils.equals(expected, actual),
                "expected " + expected + " but was " + actual);
    }

    private static Date d(int day, Month month, int year) {
        return new Date(day, month, year);
    }

    private static double yearFraction(DayCounter dayCounter, Date start, Date end) {
        return yearFraction(dayCounter, start, end, NULL_DATE, NULL_DATE);
    }

    private static double yearFraction(DayCounter dayCounter, Date start, Date end,
                                       Date refStart, Date refEnd) {
        return dayCounter.yearFraction(start, end, refStart, refEnd);
    }
}
