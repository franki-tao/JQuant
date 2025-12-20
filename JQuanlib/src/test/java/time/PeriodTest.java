package time;

import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PeriodTest {
    @Test
    public void testYearsMonthsAlgebra() {
        Period OneYear = new Period(1, TimeUnit.YEARS);
        Period SixMonths = new Period(6, TimeUnit.MONTHS);
        Period ThreeMonths = new Period(3, TimeUnit.MONTHS);

        int n = 4;
        System.out.println(TimeUtils.divide(OneYear, n));
        assertFalse(TimeUtils.neq(TimeUtils.divide(OneYear, n), ThreeMonths), "division error: " + OneYear + "/" + n + " not equal to " + ThreeMonths);
        n = 2;
        assertFalse(TimeUtils.neq(TimeUtils.divide(OneYear, n), SixMonths), "division error: " + OneYear + "/" + n + " not equal to " + SixMonths);

        Period sum = ThreeMonths.clone();
        sum.addEquals(SixMonths);
        assertFalse(TimeUtils.neq(sum, new Period(9, TimeUnit.MONTHS)),
                "sum error: " + ThreeMonths + " + " + SixMonths + " != " + new Period(9, TimeUnit.MONTHS));
        sum.addEquals(OneYear);
        assertFalse(TimeUtils.neq(sum, new Period(21, TimeUnit.MONTHS)), "sum error: " + ThreeMonths +
                " + " + SixMonths +
                " + " + OneYear +
                " != " +new Period(21, TimeUnit.MONTHS));

        Period TwelveMonths = new Period(12, TimeUnit.MONTHS);
        assertFalse(TwelveMonths.length() != 12, "normalization error: TwelveMonths.length()" +
                " is " + TwelveMonths.length() +
                " instead of 12");
        assertSame(TimeUnit.MONTHS, TwelveMonths.units(), "normalization error: TwelveMonths.units()" +
                " is " + TwelveMonths.units() +
                " instead of " + "MONTHS");

        Period NormalizedTwelveMonths = new Period(12, TimeUnit.MONTHS);
        NormalizedTwelveMonths.normalize();
        assertFalse(NormalizedTwelveMonths.length() != 1, "normalization error: NormalizedTwelveMonths.length()" +
                " is " + NormalizedTwelveMonths.length() +
                " instead of 1");
        assertSame(TimeUnit.YEARS, NormalizedTwelveMonths.units(), "normalization error: TwelveMonths.units()" +
                " is " + NormalizedTwelveMonths.units() +
                " instead of " + "YEARS");
    }

    @Test
    public void testWeeksDaysAlgebra() {
        System.out.println("Testing period algebra on weeks/days...");
        Period TwoWeeks = new Period(2, TimeUnit.WEEKS);
        Period OneWeek = new Period(1, TimeUnit.WEEKS);
        Period ThreeDays = new Period(3, TimeUnit.DAYS);
        Period OneDay = new Period(1, TimeUnit.DAYS);
        Period ZeroDays = new Period(0, TimeUnit.DAYS);

        int n = 2;
        assertFalse(TimeUtils.neq(TimeUtils.divide(TwoWeeks, n),OneWeek), "division error: " + TwoWeeks + "/" + n +
                " not equal to " + OneWeek);
        n = 7;
        assertFalse(TimeUtils.neq(TimeUtils.divide(OneWeek, n),OneDay), "division error: " + OneWeek + "/" + n +
                " not equal to " + OneDay);

        Period sum=ThreeDays.clone();
        sum.addEquals(OneDay);
        assertFalse(TimeUtils.neq(sum, new Period(4, TimeUnit.DAYS)), "sum error: " + ThreeDays +
                " + " + OneDay +
                " != " +new Period(4, TimeUnit.DAYS));

        sum.addEquals(OneWeek);
        assertFalse(TimeUtils.neq(sum, new Period(11, TimeUnit.DAYS)), "sum error: " + ThreeDays +
                " + " + OneDay +
                " + " + OneWeek +
                " != " +new Period(11, TimeUnit.DAYS));
        assertTrue(TimeUtils.equals(TimeUtils.add(OneWeek, ZeroDays), OneWeek));
        assertTrue(TimeUtils.equals(TimeUtils.add(OneWeek, TimeUtils.multiply(3, OneDay)), new Period(10, TimeUnit.DAYS)));
        assertTrue(TimeUtils.equals(TimeUtils.add(OneWeek, TimeUtils.multiply(7, OneDay)), TwoWeeks));

        Period SevenDays = new Period(7, TimeUnit.DAYS);
        assertFalse(SevenDays.length()!=7, "normalization error: SevenDays.length()" +
                " is " + SevenDays.length() +
                " instead of 7");
        assertSame(TimeUnit.DAYS, SevenDays.units(), "normalization error: SevenDays.units()" +
                " is " + SevenDays.units() +
                " instead of " + "Days");
    }

    @Test
    public void testOperators() {
        System.out.println("Testing period operators...");
        Period p = new Period(3, TimeUnit.DAYS);
        p.multiplyEquals(2);
        assertTrue(TimeUtils.equals(p, new Period(6, TimeUnit.DAYS)));

        p.subtractEquals(new Period(2, TimeUnit.DAYS));
        assertTrue(TimeUtils.equals(p, new Period(4, TimeUnit.DAYS)));
    }

    @Test
    public void testConvertToYears() {
        System.out.println("Testing conversion of periods to years...");
        assertEquals(0, TimeUtils.years(new Period(0, TimeUnit.YEARS)));
        assertEquals(1, TimeUtils.years(new Period(1, TimeUnit.YEARS)));
        assertEquals(5, TimeUtils.years(new Period(5, TimeUnit.YEARS)));

        double tol = 1e-15;

        assertTrue(Math.abs(TimeUtils.years(new Period(1, TimeUnit.MONTHS)) - 1.0/12.0) <= tol);
        assertTrue(Math.abs(TimeUtils.years(new Period(8, TimeUnit.MONTHS)) - 8.0/12.0) <= tol);
        assertEquals(1, TimeUtils.years(new Period(12, TimeUnit.MONTHS)));
        assertTrue(Math.abs(TimeUtils.years(new Period(18, TimeUnit.MONTHS)) - 1.5) <= tol);
    }

    @Test
    public void testConvertToMonths() {
        System.out.println("Testing conversion of periods to months...");
        assertEquals(0, TimeUtils.months(new Period(0, TimeUnit.MONTHS)));
        assertEquals(1, TimeUtils.months(new Period(1, TimeUnit.MONTHS)));
        assertEquals(5, TimeUtils.months(new Period(5, TimeUnit.MONTHS)));

        assertEquals(12, TimeUtils.months(new Period(1, TimeUnit.YEARS)));
        assertEquals(36, TimeUtils.months(new Period(3, TimeUnit.YEARS)));
    }

    @Test
    public void testConvertToWeeks() {
        System.out.println("Testing conversion of periods to weeks...");
        assertEquals(0, TimeUtils.weeks(new Period(0, TimeUnit.WEEKS)));
        assertEquals(1, TimeUtils.weeks(new Period(1, TimeUnit.WEEKS)));
        assertEquals(5, TimeUtils.weeks(new Period(5, TimeUnit.WEEKS)));
        final double tol = 1e-15;
        assertTrue(Math.abs(1.0/7.0 - TimeUtils.weeks(new Period(1, TimeUnit.DAYS))) <= tol);
        assertTrue(Math.abs(3.0/7.0 - TimeUtils.weeks(new Period(3, TimeUnit.DAYS))) <= tol);
        assertTrue(Math.abs(11.0/7.0 - TimeUtils.weeks(new Period(11, TimeUnit.DAYS))) <= tol);
    }

    @Test
    public void testNormalization() {
        System.out.println("Testing period normalization...");
        Period[] test_values = {
                new Period(0, TimeUnit.DAYS),
                new Period(0, TimeUnit.WEEKS),
                new Period(0, TimeUnit.MONTHS),
                new Period(0, TimeUnit.YEARS),
                new Period(3, TimeUnit.DAYS),
                new Period(7, TimeUnit.DAYS),
                new Period(14, TimeUnit.DAYS),
                new Period(30, TimeUnit.DAYS),
                new Period(60, TimeUnit.DAYS),
                new Period(365, TimeUnit.DAYS),
                new Period(1, TimeUnit.WEEKS),
                new Period(2, TimeUnit.WEEKS),
                new Period(4, TimeUnit.WEEKS),
                new Period(8, TimeUnit.WEEKS),
                new Period(52, TimeUnit.WEEKS),
                new Period(1, TimeUnit.MONTHS),
                new Period(2, TimeUnit.MONTHS),
                new Period(6, TimeUnit.MONTHS),
                new Period(12, TimeUnit.MONTHS),
                new Period(18, TimeUnit.MONTHS),
                new Period(24, TimeUnit.MONTHS),
                new Period(1, TimeUnit.YEARS),
                new Period(2, TimeUnit.YEARS)
        };
        for (Period p1 : test_values) {
            Period n1 = p1.normalized();
            assertFalse(TimeUtils.neq(n1, p1), "Normalizing " + p1 + " yields " + n1 + ", which compares different");
            for (Period p2 : test_values) {
                Period n2 = p2.normalized();
                Optional<Boolean> comparison = Optional.empty();
                try {
                    comparison = Optional.of(TimeUtils.equals(p1, p2));
                } catch (Exception e) {
                    System.out.println(p1 + " and " + p2);;
                }
                if (comparison.isPresent() && comparison.get()) {
                    assertFalse(n1.units() != n2.units() || n1.length() != n2.length(), p1 + " and " + p2 + " compare equal, but normalize to "
                            + n1 + " and " + n2 + " respectively");
                }

                if (n1.units() == n2.units() && n1.length() == n2.length()) {
                    // periods normalizing to exactly the same period must compare equal
                    assertFalse(TimeUtils.neq(p1, p2), p1 + " and " + p2 + " compare different, but normalize to "
                            + n1 + " and " + n2 + " respectively");
                }
            }
        }
    }

    @Test
    public void testFrequencyComputation() {
        System.out.println("Testing computation of frequency from period...");
        // frequency -> period -> frequency == initial frequency?
        Frequency[] ff = {Frequency.NO_FREQUENCY, Frequency.ONCE, Frequency.ANNUAL, Frequency.SEMIANNUAL, Frequency.EVERY_FOURTH_MONTH, Frequency.QUARTERLY,
        Frequency.BIMONTHLY,Frequency.MONTHLY,Frequency.EVERY_FOURTH_WEEK, Frequency.BIWEEKLY, Frequency.WEEKLY, Frequency.DAILY};
        for (Frequency f : ff) {
            assertSame(new Period(f).frequency(), f);
        }

        // test Period(count, timeUnit).frequency()
        assertSame(new Period(1, TimeUnit.YEARS).frequency(), Frequency.ANNUAL);
        assertSame(new Period(6, TimeUnit.MONTHS).frequency(), Frequency.SEMIANNUAL);
        assertSame(new Period(4, TimeUnit.MONTHS).frequency(), Frequency.EVERY_FOURTH_MONTH);
        assertSame(new Period(3, TimeUnit.MONTHS).frequency(), Frequency.QUARTERLY);
        assertSame(new Period(2, TimeUnit.MONTHS).frequency(), Frequency.BIMONTHLY);
        assertSame(new Period(1, TimeUnit.MONTHS).frequency(), Frequency.MONTHLY);
        assertSame(new Period(4, TimeUnit.WEEKS).frequency(), Frequency.EVERY_FOURTH_WEEK);
        assertSame(new Period(2, TimeUnit.WEEKS).frequency(), Frequency.BIWEEKLY);
        assertSame(new Period(1, TimeUnit.WEEKS).frequency(), Frequency.WEEKLY);
        assertSame(new Period(1, TimeUnit.DAYS).frequency(), Frequency.DAILY);
    }
}
