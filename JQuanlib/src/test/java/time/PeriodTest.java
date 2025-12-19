package time;

import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import org.junit.jupiter.api.Test;

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
}
