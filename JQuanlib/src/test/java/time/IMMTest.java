package time;

import jquant.time.Date;
import jquant.time.IMM;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuantLib {@code DateTest::immDates} in {@code test-suite/dates.cpp}.
 * The loop is every calendar day from 1 Jan 2010 through 31 Dec 2030, plus
 * the first and last 400 serial days of the QuantLib date range. Each day
 * checks {@code nextDate}, {@code isIMMdate}, {@code code}/{@code date}, and
 * that the first 40 IMM codes are not in the past.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/QuantLib-v1.16/test-suite/dates.cpp
 */
public class IMMTest {
    private static final String[] IMM_CODES = {
            "F0", "G0", "H0", "J0", "K0", "M0", "N0", "Q0", "U0", "V0", "X0", "Z0",
            "F1", "G1", "H1", "J1", "K1", "M1", "N1", "Q1", "U1", "V1", "X1", "Z1",
            "F2", "G2", "H2", "J2", "K2", "M2", "N2", "Q2", "U2", "V2", "X2", "Z2",
            "F3", "G3", "H3", "J3", "K3", "M3", "N3", "Q3", "U3", "V3", "X3", "Z3"
    };

    @Test
    public void testKnownImmDates() {
        Date march2013 = new Date(20, Month.MARCH, 2013);
        assertTrue(IMM.isIMMdate(march2013, true));
        assertEquals("H3", IMM.code(march2013));
        assertTrue(TimeUtils.equals(march2013, IMM.date("H3", new Date(1, Month.JANUARY, 2013))));

        Date january2015 = new Date(15, Month.JANUARY, 2025);
        assertTrue(IMM.isIMMdate(january2015, false));
        assertFalse(IMM.isIMMdate(january2015, true));
        assertFalse(IMM.isIMMdate(new Date(16, Month.JANUARY, 2025), false));
        assertTrue(TimeUtils.equals(new Date(19, Month.FEBRUARY, 2025), IMM.nextDate(january2015, false)));
        assertTrue(TimeUtils.equals(new Date(19, Month.MARCH, 2025), IMM.nextDate(january2015, true)));
        assertEquals("G5", IMM.nextCode(january2015, false));

        assertThrows(IllegalArgumentException.class,
                () -> IMM.code(new Date(16, Month.JANUARY, 2025)));
        assertFalse(IMM.isIMMCode("A0", false));
        assertTrue(IMM.isIMMCode("H3", true));
        assertFalse(IMM.isIMMCode("G3", true));
    }

    @Test
    public void testImmDates() {
        checkRange(new Date(1, Month.JANUARY, 2010), new Date(31, Month.DECEMBER, 2030));
        Date min = Date.minDate();
        checkRange(min, min.add(400));
        Date last = Date.maxDate().substract(new Period(121, TimeUnit.MONTHS));
        checkRange(last.substract(400), last);
    }

    private static void checkRange(Date from, Date to) {
        Date counter = from;
        while (TimeUtils.leq(counter, to)) {
            Date imm = IMM.nextDate(counter, false);
            assertTrue(TimeUtils.greater(imm, counter), imm + " is not after " + counter);
            assertTrue(IMM.isIMMdate(imm, false), imm + " is not an IMM date");
            assertTrue(TimeUtils.leq(imm, IMM.nextDate(counter, true)),
                    imm + " is after the next main-cycle IMM date");
            assertTrue(TimeUtils.equals(IMM.date(IMM.code(imm), counter), imm),
                    IMM.code(imm) + " does not round-trip at " + counter);
            for (int i = 0; i < 40; i++)
                assertFalse(TimeUtils.less(IMM.date(IMM_CODES[i], counter), counter),
                        IMM_CODES[i] + " is in the past at " + counter);
            counter = counter.add(1);
        }
    }
}
