package instruments;

import jquant.cashflows.Coupon;
import jquant.cashflows.FixedDividend;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.SimpleCashFlow;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports. They pin the defensive
 * Date-ownership scheme for the Schedule / cash-flow date surface: an input
 * Date must be copied when stored, and every returned Date (or list) must be
 * a snapshot, so mutating either side cannot change the object's state.
 *
 * <p>Official QuantLib's {@code Date} is a value type; Java's is mutable
 * ({@code addEquals}/{@code substractEquals} edit it in place), so this
 * port stores and returns copies. {@code Date} itself is deliberately not
 * made immutable and the Settings evaluation-date notification path is not
 * touched.
 */
public class DateOwnershipTest {

    private static final DayCounter DAY_COUNTER = new Actual360();

    // ------------------------------------------------------------------
    // Cash flows: input copies
    // ------------------------------------------------------------------

    @Test
    public void simpleCashFlowCopiesInputDate() {
        Date payment = new Date(15, Month.JUNE, 2025);
        SimpleCashFlow flow = new SimpleCashFlow(100.0, payment);

        payment.addEquals(10);

        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2025), flow.date()),
                "input mutation changed the stored cash-flow date");
    }

    @Test
    public void couponCopiesInputDates() {
        Date payment = new Date(15, Month.JUNE, 2025);
        Date start = new Date(15, Month.DECEMBER, 2024);
        Date end = new Date(15, Month.JUNE, 2025);
        Date refStart = new Date(15, Month.DECEMBER, 2024);
        Date refEnd = new Date(15, Month.JUNE, 2025);
        Date exCoupon = new Date(5, Month.JUNE, 2025);

        FixedRateCoupon coupon = new FixedRateCoupon(payment, 100.0, 0.05,
                DAY_COUNTER, start, end, refStart, refEnd, exCoupon);

        payment.addEquals(1);
        start.addEquals(2);
        end.addEquals(3);
        refStart.addEquals(4);
        refEnd.addEquals(5);
        exCoupon.addEquals(6);

        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2025), coupon.date()),
                "payment date aliased the caller's Date");
        assertTrue(TimeUtils.equals(new Date(15, Month.DECEMBER, 2024),
                coupon.accrualStartDate()), "accrual start aliased the caller's Date");
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2025),
                coupon.accrualEndDate()), "accrual end aliased the caller's Date");
        assertTrue(TimeUtils.equals(new Date(15, Month.DECEMBER, 2024),
                coupon.referencePeriodStart()), "reference start aliased the caller's Date");
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2025),
                coupon.referencePeriodEnd()), "reference end aliased the caller's Date");
        assertTrue(TimeUtils.equals(new Date(5, Month.JUNE, 2025),
                coupon.exCouponDate()), "ex-coupon date aliased the caller's Date");
    }

    @Test
    public void couponEmptyReferencePeriodUsesIndependentCopies() {
        Date start = new Date(15, Month.DECEMBER, 2024);
        Date end = new Date(15, Month.JUNE, 2025);
        FixedRateCoupon coupon = new FixedRateCoupon(end, 100.0, 0.05,
                DAY_COUNTER, start, end);

        // The official empty-reference fallback must not make the reference
        // date the same internal object as the accrual date.
        assertNotSame(field(Coupon.class, coupon, "refPeriodStart_"),
                field(Coupon.class, coupon, "accrualStartDate_"),
                "empty reference start aliases the accrual start internally");
        assertNotSame(field(Coupon.class, coupon, "refPeriodEnd_"),
                field(Coupon.class, coupon, "accrualEndDate_"),
                "empty reference end aliases the accrual end internally");

        assertTrue(TimeUtils.equals(start, coupon.referencePeriodStart()),
                "empty reference start did not fall back to the accrual start");
        assertTrue(TimeUtils.equals(end, coupon.referencePeriodEnd()),
                "empty reference end did not fall back to the accrual end");
    }

    @Test
    public void dividendCopiesInputDate() {
        Date payment = new Date(1, Month.MARCH, 2025);
        FixedDividend dividend = new FixedDividend(2.0, payment);

        payment.addEquals(3);

        assertTrue(TimeUtils.equals(new Date(1, Month.MARCH, 2025), dividend.date()),
                "input mutation changed the stored dividend date");
    }

    @Test
    public void scheduleDateListCopiesInputDates() {
        Date first = new Date(1, Month.JANUARY, 2025);
        Date second = new Date(1, Month.JULY, 2025);
        Date third = new Date(1, Month.JANUARY, 2026);
        List<Date> input = new ArrayList<>(List.of(first, second, third));

        Schedule schedule = new Schedule(input);

        first.addEquals(10);
        second.addEquals(20);
        third.addEquals(30);
        input.clear();

        assertEquals(3, schedule.size(), "structurally mutating the input list changed the schedule");
        assertTrue(TimeUtils.equals(new Date(1, Month.JANUARY, 2025), schedule.date(0)),
                "input mutation changed schedule date 0");
        assertTrue(TimeUtils.equals(new Date(1, Month.JULY, 2025), schedule.date(1)),
                "input mutation changed schedule date 1");
        assertTrue(TimeUtils.equals(new Date(1, Month.JANUARY, 2026), schedule.date(2)),
                "input mutation changed schedule date 2");
    }

    // ------------------------------------------------------------------
    // Cash flows: output snapshots
    // ------------------------------------------------------------------

    @Test
    public void cashFlowAccessorsReturnSnapshots() {
        Date payment = new Date(15, Month.JUNE, 2025);
        Date start = new Date(15, Month.DECEMBER, 2024);
        Date end = new Date(15, Month.JUNE, 2025);
        Date exCoupon = new Date(5, Month.JUNE, 2025);

        SimpleCashFlow flow = new SimpleCashFlow(100.0, payment);
        FixedRateCoupon coupon = new FixedRateCoupon(payment, 100.0, 0.05,
                DAY_COUNTER, start, end, start, end, exCoupon);
        FixedDividend dividend = new FixedDividend(2.0, payment);

        flow.date().addEquals(1);
        dividend.date().addEquals(1);
        coupon.date().addEquals(1);
        coupon.accrualStartDate().addEquals(1);
        coupon.accrualEndDate().addEquals(1);
        coupon.referencePeriodStart().addEquals(1);
        coupon.referencePeriodEnd().addEquals(1);
        coupon.exCouponDate().addEquals(1);

        assertTrue(TimeUtils.equals(payment, flow.date()),
                "mutating SimpleCashFlow.date() changed the stored date");
        assertTrue(TimeUtils.equals(payment, dividend.date()),
                "mutating Dividend.date() changed the stored date");
        assertTrue(TimeUtils.equals(payment, coupon.date()),
                "mutating Coupon.date() changed the stored payment date");
        assertTrue(TimeUtils.equals(start, coupon.accrualStartDate()),
                "mutating accrualStartDate() changed the stored date");
        assertTrue(TimeUtils.equals(end, coupon.accrualEndDate()),
                "mutating accrualEndDate() changed the stored date");
        assertTrue(TimeUtils.equals(start, coupon.referencePeriodStart()),
                "mutating referencePeriodStart() changed the stored date");
        assertTrue(TimeUtils.equals(end, coupon.referencePeriodEnd()),
                "mutating referencePeriodEnd() changed the stored date");
        assertTrue(TimeUtils.equals(exCoupon, coupon.exCouponDate()),
                "mutating exCouponDate() changed the stored date");
    }

    // ------------------------------------------------------------------
    // Schedule: output snapshots
    // ------------------------------------------------------------------

    @Test
    public void scheduleAccessorsReturnSnapshots() {
        Schedule schedule = dateListSchedule();
        Date second = new Date(1, Month.JULY, 2025);

        schedule.date(1).addEquals(5);
        schedule.at(1).addEquals(5);
        schedule.front().addEquals(5);
        schedule.back().addEquals(5);
        schedule.startDate().addEquals(5);
        schedule.endDate().addEquals(5);
        schedule.dates().get(1).addEquals(5);
        Date previous = schedule.previousDate(new Date(1, Month.SEPTEMBER, 2025));
        previous.addEquals(5);
        schedule.nextDate(new Date(1, Month.MARCH, 2025)).addEquals(5);

        assertEquals(3, schedule.size(), "mutating returned dates changed the size");
        assertTrue(TimeUtils.equals(second, schedule.date(1)),
                "mutating a returned schedule date changed the internal date");
        assertTrue(TimeUtils.equals(new Date(1, Month.JULY, 2025), schedule.dates().get(1)),
                "mutating dates().get(1) changed the internal date");
    }

    @Test
    public void scheduleReturnedListsAreStructuralCopies() {
        Date first = new Date(1, Month.JANUARY, 2025);
        Date second = new Date(1, Month.JULY, 2025);
        Date third = new Date(1, Month.JANUARY, 2026);
        Schedule schedule = new Schedule(
                new ArrayList<>(List.of(first, second, third)), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                new Period(Frequency.SEMIANNUAL), DateGenerationRule.BACKWARD, false,
                new ArrayList<>(List.of(true, false)));

        int size = schedule.size();

        List<Date> dates = schedule.dates();
        dates.set(0, new Date(1, Month.FEBRUARY, 2030));
        dates.remove(0);
        dates.add(new Date(1, Month.MARCH, 2030));
        assertEquals(size, schedule.size(), "mutating the dates() list changed the schedule");
        assertTrue(TimeUtils.equals(first, schedule.date(0)),
                "mutating the dates() list changed the first date");

        List<Boolean> isRegular = schedule.isRegular();
        isRegular.set(0, false);
        isRegular.clear();
        isRegular.add(true);
        assertEquals(size - 1, schedule.isRegular().size(),
                "mutating the isRegular() list changed the schedule");
        assertTrue(schedule.isRegular().get(0),
                "mutating the isRegular() list changed the first flag");
        assertFalse(schedule.isRegular().get(1),
                "mutating the isRegular() list changed the second flag");
    }

    @Test
    public void scheduleCopyHasIndependentDates() {
        Schedule original = ruleSchedule();
        Schedule copy = original.copy();

        assertTrue(TimeUtils.equals(original.date(1), copy.date(1)),
                "copy has different dates");

        @SuppressWarnings("unchecked")
        List<Date> originalDates = (List<Date>) field(Schedule.class, original, "dates_");
        @SuppressWarnings("unchecked")
        List<Date> copyDates = (List<Date>) field(Schedule.class, copy, "dates_");

        assertNotSame(originalDates.get(1), copyDates.get(1),
                "copy shares a Date object with the original");
        Date originalFirst = original.date(0);
        copyDates.get(1).addEquals(7);
        assertTrue(TimeUtils.equals(originalFirst, original.date(0)),
                "mutating the copy changed the original's first date");
        assertFalse(TimeUtils.equals(original.date(1), copy.date(1)),
                "mutating the copy's internal date did not take effect");

        Date originalFirstDesign = (Date) field(Schedule.class, original, "firstDate_");
        Date originalNextToLast =
                (Date) field(Schedule.class, original, "nextToLastDate_");
        Date copyFirstDesign = (Date) field(Schedule.class, copy, "firstDate_");
        Date copyNextToLast = (Date) field(Schedule.class, copy, "nextToLastDate_");

        assertNotSame(originalFirstDesign, copyFirstDesign,
                "copy shares firstDate_ with the original");
        assertNotSame(originalNextToLast, copyNextToLast,
                "copy shares nextToLastDate_ with the original");

        copyFirstDesign.addEquals(11);
        copyNextToLast.addEquals(13);
        assertTrue(TimeUtils.equals(new Date(1, Month.MARCH, 2020),
                (Date) field(Schedule.class, original, "firstDate_")),
                "mutating the copy's firstDate_ changed the original");
        assertTrue(TimeUtils.equals(new Date(1, Month.SEPTEMBER, 2021),
                (Date) field(Schedule.class, original, "nextToLastDate_")),
                "mutating the copy's nextToLastDate_ changed the original");
    }

    @Test
    public void emptyDatesRemainEmptyCopies() {
        Date empty = new Date();

        SimpleCashFlow flow = new SimpleCashFlow(1.0, empty);
        assertNotNull(flow.date(), "copied empty date became null");
        assertEquals(0, flow.date().serialNumber(), "copied empty date is not serial 0");

        FixedDividend dividend = new FixedDividend(1.0, empty);
        assertNotNull(dividend.date(), "copied empty dividend date became null");
        assertEquals(0, dividend.date().serialNumber(),
                "copied empty dividend date is not serial 0");

        FixedRateCoupon coupon = new FixedRateCoupon(empty, 100.0, 0.05,
                DAY_COUNTER, empty, empty);
        assertEquals(0, coupon.date().serialNumber(),
                "copied empty coupon payment date is not serial 0");
        assertEquals(0, coupon.accrualStartDate().serialNumber(),
                "copied empty accrual start is not serial 0");
        assertEquals(0, coupon.referencePeriodStart().serialNumber(),
                "copied empty reference start is not serial 0");

        Schedule schedule = new Schedule(new ArrayList<>(List.of(new Date(),
                new Date(1, Month.JANUARY, 2025))));
        assertNotNull(schedule.date(0), "copied empty schedule date became null");
        assertEquals(0, schedule.date(0).serialNumber(),
                "copied empty schedule date is not serial 0");
        assertEquals(0, schedule.startDate().serialNumber(),
                "copied empty start date is not serial 0");
        assertEquals(0, schedule.dates().get(0).serialNumber(),
                "copied empty dates() element is not serial 0");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Schedule dateListSchedule() {
        return new Schedule(new ArrayList<>(List.of(
                new Date(1, Month.JANUARY, 2025),
                new Date(1, Month.JULY, 2025),
                new Date(1, Month.JANUARY, 2026))));
    }

    /**
     * Rule-based schedule with real first/next-to-last dates, so that
     * {@link Schedule#copy()} has non-empty internal dates to copy.
     */
    private static Schedule ruleSchedule() {
        return new Schedule(
                new Date(1, Month.JANUARY, 2020),
                new Date(1, Month.JANUARY, 2022),
                new Period(Frequency.SEMIANNUAL), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD, false,
                new Date(1, Month.MARCH, 2020),
                new Date(1, Month.SEPTEMBER, 2021));
    }

    private static Object field(Class<?> owner, Object target, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot read " + owner.getSimpleName()
                    + "." + name, e);
        }
    }
}
