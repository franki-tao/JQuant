package time;

import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUtils;
import jquant.time.calendars.JointCalendar;
import jquant.time.calendars.UnitedKingdom;
import jquant.time.calendars.UnitedStates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code CalendarTest::testUKSettlement} and {@code testUKExchange}.
 * The lists are weekday holidays from 1 January 2004 through 31 December 2007.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/QuantLib-v1.16/test-suite/calendars.cpp
 */
public class UnitedKingdomCalendarTest {
    private static final int[][] SETTLEMENT_AND_EXCHANGE = {
            {1, 1, 2004}, {9, 4, 2004}, {12, 4, 2004}, {3, 5, 2004}, {31, 5, 2004},
            {30, 8, 2004}, {27, 12, 2004}, {28, 12, 2004},
            {3, 1, 2005}, {25, 3, 2005}, {28, 3, 2005}, {2, 5, 2005}, {30, 5, 2005},
            {29, 8, 2005}, {26, 12, 2005}, {27, 12, 2005},
            {2, 1, 2006}, {14, 4, 2006}, {17, 4, 2006}, {1, 5, 2006}, {29, 5, 2006},
            {28, 8, 2006}, {25, 12, 2006}, {26, 12, 2006},
            {1, 1, 2007}, {6, 4, 2007}, {9, 4, 2007}, {7, 5, 2007}, {28, 5, 2007},
            {27, 8, 2007}, {25, 12, 2007}, {26, 12, 2007}
    };

    @Test
    public void testUKSettlement() {
        assertHolidayList(new UnitedKingdom(UnitedKingdom.Market.Settlement), "UK settlement");
    }

    @Test
    public void testUKExchange() {
        assertHolidayList(new UnitedKingdom(UnitedKingdom.Market.Exchange), "London stock exchange");
    }

    @Test
    public void testJointRules() {
        Calendar uk = new UnitedKingdom(UnitedKingdom.Market.Exchange);
        Calendar us = new UnitedStates(UnitedStates.Market.Settlement);
        Calendar holidays = new JointCalendar(uk, us, JointCalendar.Rule.JoinHolidays);
        Calendar business = new JointCalendar(uk, us, JointCalendar.Rule.JoinBusinessDays);
        Date earlyMay = new Date(3, Month.MAY, 2004);
        Date independenceDay = new Date(4, Month.JULY, 2005);
        Date ordinary = new Date(15, Month.JANUARY, 2025);
        assertTrue(uk.isHoliday(earlyMay));
        assertTrue(us.isBusinessDay(earlyMay));
        assertTrue(holidays.isHoliday(earlyMay));
        assertTrue(business.isBusinessDay(earlyMay));
        assertTrue(us.isHoliday(independenceDay));
        assertTrue(uk.isBusinessDay(independenceDay));
        assertTrue(holidays.isHoliday(independenceDay));
        assertTrue(business.isBusinessDay(independenceDay));
        assertTrue(uk.isBusinessDay(ordinary));
        assertTrue(us.isBusinessDay(ordinary));
        assertTrue(holidays.isBusinessDay(ordinary));
        assertFalse(holidays.isHoliday(ordinary));
    }

    private static void assertHolidayList(Calendar calendar, String name) {
        assertEquals(name, calendar.name());
        List<Date> actual = calendar.holidayList(
                new Date(1, Month.JANUARY, 2004), new Date(31, Month.DECEMBER, 2007), false);
        assertEquals(SETTLEMENT_AND_EXCHANGE.length, actual.size());
        for (int i = 0; i < SETTLEMENT_AND_EXCHANGE.length; i++) {
            int[] row = SETTLEMENT_AND_EXCHANGE[i];
            Date expected = new Date(row[0], Month.fromValue(row[1]), row[2]);
            assertTrue(TimeUtils.equals(expected, actual.get(i)),
                    "expected " + expected + " but calculated " + actual.get(i));
        }
    }
}
