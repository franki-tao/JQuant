package indexes;

import jquant.indexes.USDLibor;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.JointCalendar;
import jquant.time.calendars.UnitedKingdom;
import jquant.time.calendars.UnitedStates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * USD Libor value and maturity dates from {@code Libor::valueDate} and
 * {@code Libor::maturityDate}. 15 January 2025 is a London business day.
 * Two London business days later is 17 January 2025, which is also a US
 * Libor-impact business day. Three months modified-following is 17 April 2025.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/indexes/ibor/libor.cpp
 */
public class USDLiborTest {
    @Test
    public void testValueAndMaturityDates() {
        USDLibor libor = new USDLibor(new Period(3, TimeUnit.MONTHS));
        assertEquals("London stock exchange", libor.fixingCalendar().name());
        assertEquals("US with Libor impact", libor.financialCenterCalendar().name());
        Date fixing = new Date(15, Month.JANUARY, 2025);
        assertTrue(TimeUtils.equals(new Date(17, Month.JANUARY, 2025), libor.valueDate(fixing)));
        assertTrue(TimeUtils.equals(new Date(17, Month.APRIL, 2025),
                libor.maturityDate(new Date(17, Month.JANUARY, 2025))));

        Calendar london = new UnitedKingdom(UnitedKingdom.Market.Exchange);
        Calendar us = new UnitedStates(UnitedStates.Market.LiborImpact);
        Calendar joint = new JointCalendar(london, us, JointCalendar.Rule.JoinHolidays);
        Date londonValue = london.advance(fixing, 2, TimeUnit.DAYS, BusinessDayConvention.FOLLOWING, false);
        assertTrue(TimeUtils.equals(joint.adjust(londonValue, BusinessDayConvention.FOLLOWING),
                libor.valueDate(fixing)));
    }
}
