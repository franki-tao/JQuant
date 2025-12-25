package time;

import jquant.math.Point;
import jquant.time.*;
import jquant.time.calendars.Japan;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.UnitedStates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.time.calendars.UnitedStates.Market.GovernmentBond;
import static org.junit.jupiter.api.Assertions.*;

public class ScheduleTest {
    public void check_dates(final Schedule s,
                            final List<Date> expected) {
        assertEquals(s.size(), expected.size());
        for (int i = 0; i < expected.size(); ++i) {
            assertTrue(TimeUtils.equals(s.at(i), expected.get(i)));
        }
    }

    public Schedule makeCdsSchedule(Date from, Date to, DateGenerationRule rule) {
        return new MakeSchedule()
                .from(from)
                .to(to)
                .withCalendar(new WeekendsOnly())
                .withTenor(TimeUtils.multiply(3, TimeUnit.MONTHS))
                .withConvention(BusinessDayConvention.FOLLOWING)
                .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                .withRule(rule).toSchedule();
    }

    public void testCDSConventions() {}

    @Test
    public void testDailySchedule() {
        System.out.println("Testing schedule with daily frequency...");
        Date startDate = new Date(17, Month.JANUARY, 2012);

        Schedule s =
                new MakeSchedule().from(startDate).to(startDate.add(7))
                        .withCalendar(new Target())
                        .withFrequency(Frequency.DAILY)
                        .withConvention(BusinessDayConvention.PRECEDING).toSchedule();

        List<Date> expected = new ArrayList<>();
        // The schedule should skip Saturday 21st and Sunday 22rd.
        // Previously, it would adjust them to Friday 20th, resulting
        // in three copies of the same date.
        expected.add(new Date(17, Month.JANUARY, 2012));
        expected.add(new Date(18, Month.JANUARY, 2012));
        expected.add(new Date(19, Month.JANUARY, 2012));
        expected.add(new Date(20, Month.JANUARY, 2012));
        expected.add(new Date(23, Month.JANUARY, 2012));
        expected.add(new Date(24, Month.JANUARY, 2012));

        check_dates(s, expected);
    }

    @Test
    public void testEomAdjustment() {
        System.out.println("Testing end-of-month adjustment with different conventions...");
        Date startDate = new Date(29, Month.FEBRUARY, 2024);
        Date endDate = startDate.add(TimeUtils.multiply(1, TimeUnit.YEARS));

        Schedule s1 =
                new MakeSchedule().from(startDate).to(endDate)
                        .withCalendar(new Target())
                        .withFrequency(Frequency.MONTHLY)
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .endOfMonth(true).toSchedule();

        check_dates(s1, Arrays.asList(
                new Date(29, Month.FEBRUARY, 2024),
                new Date(31, Month.MARCH, 2024),
                new Date(30, Month.APRIL, 2024),
                new Date(31, Month.MAY, 2024),
                new Date(30, Month.JUNE, 2024),
                new Date(31, Month.JULY, 2024),
                new Date(31, Month.AUGUST, 2024),
                new Date(30, Month.SEPTEMBER, 2024),
                new Date(31, Month.OCTOBER, 2024),
                new Date(30, Month.NOVEMBER, 2024),
                new Date(31, Month.DECEMBER, 2024),
                new Date(31, Month.JANUARY, 2025),
                new Date(28, Month.FEBRUARY, 2025)));

        Schedule s2 =
                new MakeSchedule().from(startDate).to(endDate)
                        .withCalendar(new Target())
                        .withFrequency(Frequency.MONTHLY)
                        .withConvention(BusinessDayConvention.FOLLOWING)
                        .endOfMonth(true).toSchedule();

        check_dates(s2, Arrays.asList(
                new Date(29, Month.FEBRUARY, 2024),
                new Date(2, Month.APRIL, 2024),
                new Date(30, Month.APRIL, 2024),
                new Date(31, Month.MAY, 2024),
                new Date(1, Month.JULY, 2024),
                new Date(31, Month.JULY, 2024),
                new Date(2, Month.SEPTEMBER, 2024),
                new Date(30, Month.SEPTEMBER, 2024),
                new Date(31, Month.OCTOBER, 2024),
                new Date(2, Month.DECEMBER, 2024),
                new Date(31, Month.DECEMBER, 2024),
                new Date(31, Month.JANUARY, 2025),
                new Date(28, Month.FEBRUARY, 2025)

        ));

        Schedule s3 =
                new MakeSchedule().from(startDate).to(endDate)
                        .withCalendar(new Target())
                        .withFrequency(Frequency.MONTHLY)
                        .withConvention(BusinessDayConvention.MODIFIED_PRECEDING)
                        .endOfMonth(true).toSchedule();

        check_dates(s3, Arrays.asList(
                new Date(29, Month.FEBRUARY, 2024),
                new Date(28, Month.MARCH, 2024),
                new Date(30, Month.APRIL, 2024),
                new Date(31, Month.MAY, 2024),
                new Date(28, Month.JUNE, 2024),
                new Date(31, Month.JULY, 2024),
                new Date(30, Month.AUGUST, 2024),
                new Date(30, Month.SEPTEMBER, 2024),
                new Date(31, Month.OCTOBER, 2024),
                new Date(29, Month.NOVEMBER, 2024),
                new Date(31, Month.DECEMBER, 2024),
                new Date(31, Month.JANUARY, 2025),
                new Date(28, Month.FEBRUARY, 2025)
        ));
    }

    @Test
    public void testEndDateWithEomAdjustment() {
        System.out.println("Testing end date for schedule with end-of-month adjustment...");
        Schedule s =
                new MakeSchedule().from(new Date(30, Month.SEPTEMBER, 2009))
                        .to(new Date(15, Month.JUNE, 2012))
                        .withCalendar(new Japan())
                        .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                        .withConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                        .withTerminationDateConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                        .forwards()
                        .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(30, Month.SEPTEMBER, 2009),
                new Date(31, Month.MARCH, 2010),
                new Date(30, Month.SEPTEMBER, 2010),
                new Date(31, Month.MARCH, 2011),
                new Date(30, Month.SEPTEMBER, 2011),
                new Date(30, Month.MARCH, 2012),
                new Date(15, Month.JUNE, 2012)
        );
        check_dates(s, expected);
    }

    @Test
    public void testDatesPastEndDateWithEomAdjustment() {
        System.out.println("Testing that no dates are past the end date with EOM adjustment...");
        Schedule s =
                new MakeSchedule().from(new Date(28, Month.MARCH, 2013))
                        .to(new Date(30, Month.MARCH, 2015))
                        .withCalendar(new Target())
                        .withTenor(TimeUtils.multiply(1, TimeUnit.YEARS))
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                        .forwards()
                        .endOfMonth(true).toSchedule();

        List<Date> expected = Arrays.asList(
                new Date(28, Month.MARCH, 2013),
                new Date(31, Month.MARCH, 2014),
                // March 31st 2015, coming from the EOM adjustment of March 28th,
                // should be discarded as past the end date.
                new Date(30, Month.MARCH, 2015));
        check_dates(s, expected);

        // also, the last period should not be regular.
        assertFalse(s.isRegular(2), "last period should not be regular");
    }

    @Test
    public void testDatesSameAsEndDateWithEomAdjustment() {
        System.out.println("Testing that next-to-last date same as end date is removed...");
        Schedule s =
                new MakeSchedule().from(new Date(28, Month.MARCH, 2013))
                        .to(new Date(31, Month.MARCH, 2015))
                        .withCalendar(new Target())
                        .withTenor(TimeUtils.multiply(1, TimeUnit.YEARS))
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                        .forwards()
                        .endOfMonth(true).toSchedule();

        List<Date> expected = Arrays.asList(
                new Date(28, Month.MARCH, 2013),
                new Date(31, Month.MARCH, 2014),
                // March 31st 2015, coming from the EOM adjustment of March 28th,
                // should be discarded as the same as the end date.
                new Date(31, Month.MARCH, 2015));
        check_dates(s, expected);

        // also, the last period should be regular.
        assertTrue(s.isRegular(2), "last period should be regular");
    }

    @Test
    public void testForwardDatesWithEomAdjustment() {
        System.out.println("Testing that the last date is not adjusted for EOM when " +
                "termination date convention is unadjusted...");
        Schedule s =
                new MakeSchedule().from(new Date(31, Month.AUGUST, 1996))
                        .to(new Date(15, Month.SEPTEMBER, 1997))
                        .withCalendar(new UnitedStates(GovernmentBond))
                        .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                        .forwards()
                        .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(31, Month.AUGUST, 1996),
                new Date(28, Month.FEBRUARY, 1997),
                new Date(31, Month.AUGUST, 1997),
                new Date(15, Month.SEPTEMBER, 1997));
        check_dates(s, expected);
    }

    @Test
    public void testBackwardDatesWithEomAdjustment() {
        System.out.println("Testing that the first date is not adjusted for EOM " +
                "going backward when termination date convention is unadjusted...");
        Schedule s =
                new MakeSchedule().from(new Date(22, Month.AUGUST, 1996))
                        .to(new Date(31, Month.AUGUST, 1997))
                        .withCalendar(new UnitedStates(GovernmentBond))
                        .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                        .backwards()
                        .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(22, Month.AUGUST, 1996),
                new Date(31, Month.AUGUST, 1996),
                new Date(28, Month.FEBRUARY, 1997),
                new Date(31, Month.AUGUST, 1997)
        );

        check_dates(s, expected);
    }

    @Test
    public void testDoubleFirstDateWithEomAdjustment() {
        System.out.println("Testing that the first date is not duplicated due to " +
                "EOM convention when going backwards...");
        Schedule s =
                new MakeSchedule().from(new Date(22,Month.AUGUST,1996))
                        .to(new Date(31,Month.AUGUST,1997))
                        .withCalendar(new UnitedStates(GovernmentBond))
                        .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                        .withConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                        .withTerminationDateConvention(BusinessDayConvention.FOLLOWING)
                        .backwards()
                        .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(22, Month.AUGUST, 1996),
                new Date(30, Month.AUGUST, 1996),
                new Date(28, Month.FEBRUARY, 1997),
                new Date(2, Month.SEPTEMBER, 1997));
        check_dates(s, expected);
    }

    @Test
    public void testFirstDateWithEomAdjustment() {
        System.out.println("Testing schedule with first date and EOM adjustments...");
        Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.AUGUST, 1996))
                .to(new Date(10, Month.AUGUST, 1998))
                .withFirstDate(new Date(28, Month.FEBRUARY, 1997))
                .withCalendar(new UnitedStates(GovernmentBond))
                .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                .withConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                .withTerminationDateConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                .forwards()
                .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(12, Month.AUGUST, 1996),
                new Date(28, Month.FEBRUARY, 1997),
                new Date(29, Month.AUGUST, 1997),
                new Date(27, Month.FEBRUARY, 1998),
                new Date(10, Month.AUGUST, 1998));
        check_dates(schedule, expected);
    }

    @Test
    public void testNextToLastWithEomAdjustment() {
        System.out.println("Testing schedule with next to last date and EOM adjustments...");
        Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.AUGUST, 1996))
                .to(new Date(10, Month.AUGUST, 1998))
                .withNextToLastDate(new Date(28, Month.FEBRUARY, 1998))
                .withCalendar(new UnitedStates(GovernmentBond))
                .withTenor(TimeUtils.multiply(6, TimeUnit.MONTHS))
                .withConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                .withTerminationDateConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                .backwards()
                .endOfMonth(true).toSchedule();
        List<Date> expected = Arrays.asList(
                new Date(12, Month.AUGUST, 1996),
                new Date(30, Month.AUGUST, 1996),
                new Date(28, Month.FEBRUARY, 1997),
                new Date(29, Month.AUGUST, 1997),
                new Date(27, Month.FEBRUARY, 1998),
                new Date(10, Month.AUGUST, 1998));

        check_dates(schedule, expected);
    }

    @Test
    public void testEffectiveDateWithEomAdjustment() {
        System.out.println("Testing forward schedule with EOM adjustment and effective date and first date in the same month...");
        Schedule s =
                new MakeSchedule().from(new Date(16,Month.JANUARY,2023))
                        .to(new Date(16,Month.MARCH,2023))
                        .withFirstDate(new Date(31,Month.JANUARY,2023))
                        .withCalendar(new NullCalendar())
                        .withTenor(TimeUtils.multiply(1, TimeUnit.MONTHS))
                        .withConvention(BusinessDayConvention.UNADJUSTED)
                        .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                        .forwards()
                        .endOfMonth(true).toSchedule();

        List<Date> expected = Arrays.asList(
                new Date(16, Month.JANUARY, 2023),
                new Date(31, Month.JANUARY, 2023),
                new Date(28, Month.FEBRUARY, 2023),
                new Date(16, Month.MARCH, 2023)
        );

        check_dates(s, expected);
    }

//    @Test
//    public void testCDS2015Convention() {
//        System.out.println("Testing CDS2015 semi-annual rolling convention...");
//        DateGenerationRule rule = DateGenerationRule.CDS2015;
//        Period tenor = new Period(5, TimeUnit.YEARS);
//
//        // From September 20th 2016 to March 19th 2017 of the next year, end date is December 20th 2021 for a 5 year CDS.
//        // To get the correct schedule, you can first use the cdsMaturity function to get the maturity from the tenor.
//        Date tradeDate = new Date(12, Month.DECEMBER, 2016);
//        Date maturity = cdsMaturity(tradeDate, tenor, rule);
//        Date expStart(20, Sep, 2016);
//        Date expMaturity(20, Dec, 2021);
//    }
}
