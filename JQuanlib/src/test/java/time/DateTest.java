package time;

import jquant.Settings;
import jquant.time.*;
import jquant.utilities.DateParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DateTest {

    @Test
    public void ecbIsECBcode() {
        System.out.println("Testing ECB codes for validity...");
        assertTrue(ECB.isECBcode("JAN00"));
        assertTrue(ECB.isECBcode("FEB78"));
        assertTrue(ECB.isECBcode("mar58"));
        assertTrue(ECB.isECBcode("aPr99"));

        assertFalse(ECB.isECBcode(""));
        assertFalse(ECB.isECBcode("JUNE99"));
        assertFalse(ECB.isECBcode("JUN1999"));
        assertFalse(ECB.isECBcode("JUNE"));
        assertFalse(ECB.isECBcode("JUNE1999"));
        assertFalse(ECB.isECBcode("1999"));
    }

    @Test
    public void ecbDates() {
        System.out.println("Testing ECB dates...");
        Set<Date> knownDates = ECB.knownDates();
        assertFalse(knownDates.isEmpty(), "empty ECB date vector");
        final int n = ECB.nextDates(Date.minDate()).size();
        assertEquals(n, knownDates.size(), "nextDates(minDate) returns " + n +
                " instead of " + knownDates.size() + " dates");

        Date previousEcbDate = Date.minDate();
        for (Date currentEcbDate : knownDates) {
            assertTrue(ECB.isECBdate(currentEcbDate), currentEcbDate + " fails isECBdate check");

            Date ecbDateMinusOne = currentEcbDate.substract(1);
            assertFalse(ECB.isECBdate(ecbDateMinusOne), ecbDateMinusOne + " fails isECBdate check");
            assertFalse(TimeUtils.neq(ECB.nextDate(ecbDateMinusOne), currentEcbDate), "next ECB date following " + ecbDateMinusOne +
                    " must be " + currentEcbDate);

            assertFalse(TimeUtils.neq(ECB.nextDate(previousEcbDate), currentEcbDate), "next ECB date following " + previousEcbDate +
                    " must be " + currentEcbDate);

            previousEcbDate = currentEcbDate.copy();
        }

        Date knownDate = ECB.get(0);
        ECB.removeDate(knownDate);
        assertFalse(ECB.isECBdate(knownDate), "unable to remove an ECB date");

        ECB.addDate(knownDate);
        assertTrue(ECB.isECBdate(knownDate),
                "unable to add an ECB date");
    }

    @Test
    public void ecbGetDateFromCode() {
        System.out.println("Testing conversion of ECB codes to dates...");
        final Date ref2000 = new Date(1, Month.JANUARY, 2000);
        assertTrue(TimeUtils.equals(ECB.date("JAN05", ref2000), new Date(19, Month.JANUARY, 2005)));
        assertTrue(TimeUtils.equals(ECB.date("FEB06", ref2000), new Date(8, Month.FEBRUARY, 2006)));
        assertTrue(TimeUtils.equals(ECB.date("MAR07", ref2000), new Date(14, Month.MARCH, 2007)));
        assertTrue(TimeUtils.equals(ECB.date("APR08", ref2000), new Date(16, Month.APRIL, 2008)));
        assertTrue(TimeUtils.equals(ECB.date("JUN09", ref2000), new Date(10, Month.JUNE, 2009)));
        assertTrue(TimeUtils.equals(ECB.date("JUL10", ref2000), new Date(14, Month.JULY, 2010)));
        assertTrue(TimeUtils.equals(ECB.date("AUG11", ref2000), new Date(10, Month.AUGUST, 2011)));
        assertTrue(TimeUtils.equals(ECB.date("SEP12", ref2000), new Date(12, Month.SEPTEMBER, 2012)));
        assertTrue(TimeUtils.equals(ECB.date("OCT13", ref2000), new Date(9, Month.OCTOBER, 2013)));
        Settings.instance.resetEvaluationDate();
        Settings.instance.evaluationDate().equal(ref2000);
        assertTrue(TimeUtils.equals(ECB.date("NOV14", new Date()), new Date(12, Month.NOVEMBER, 2014)));
        assertTrue(TimeUtils.equals(ECB.date("DEC15", new Date()), new Date(9, Month.DECEMBER, 2015)));
    }

    @Test
    public void ecbGetCodeFromDate() {
        System.out.println("Testing creation of ECB code from a given date...");
        assertEquals("JAN06", ECB.code(new Date(18, Month.JANUARY, 2006)));
        assertEquals("MAR10", ECB.code(new Date(10, Month.MARCH, 2010)));
        assertEquals("NOV17", ECB.code(new Date(1, Month.NOVEMBER, 2017)));
    }

    @Test
    public void ecbNextCode() {
        System.out.println("Testing calculation of the next ECB code from a given code...");
        assertEquals("FEB06", ECB.nextCode("JAN06"));
        assertEquals("MAR10", ECB.nextCode("FeB10"));
        assertEquals("NOV17", ECB.nextCode("OCT17"));
        assertEquals("JAN18", ECB.nextCode("dEC17"));
        assertEquals("JAN00", ECB.nextCode("dec99"));
    }

    @Test
    public void immDates() {
        System.out.println("Testing IMM dates...");
        String[] IMMcodes = {
                "F0", "G0", "H0", "J0", "K0", "M0", "N0", "Q0", "U0", "V0", "X0", "Z0",
                "F1", "G1", "H1", "J1", "K1", "M1", "N1", "Q1", "U1", "V1", "X1", "Z1",
                "F2", "G2", "H2", "J2", "K2", "M2", "N2", "Q2", "U2", "V2", "X2", "Z2",
                "F3", "G3", "H3", "J3", "K3", "M3", "N3", "Q3", "U3", "V3", "X3", "Z3",
                "F4", "G4", "H4", "J4", "K4", "M4", "N4", "Q4", "U4", "V4", "X4", "Z4",
                "F5", "G5", "H5", "J5", "K5", "M5", "N5", "Q5", "U5", "V5", "X5", "Z5",
                "F6", "G6", "H6", "J6", "K6", "M6", "N6", "Q6", "U6", "V6", "X6", "Z6",
                "F7", "G7", "H7", "J7", "K7", "M7", "N7", "Q7", "U7", "V7", "X7", "Z7",
                "F8", "G8", "H8", "J8", "K8", "M8", "N8", "Q8", "U8", "V8", "X8", "Z8",
                "F9", "G9", "H9", "J9", "K9", "M9", "N9", "Q9", "U9", "V9", "X9", "Z9"
        };
        Date counter = new Date(1, Month.JANUARY, 2000);
        Date last = new Date(1, Month.JANUARY, 2040);
        Date imm;

        while (TimeUtils.leq(counter, last)) {
            imm = IMM.nextDate(counter, false);

            // check that imm is greater than counter
            assertFalse(TimeUtils.leq(imm, counter), imm.weekday() + " " + imm
                    + " is not greater than "
                    + counter.weekday() + " " + counter);
            // check that imm is an IMM date
            assertTrue(IMM.isIMMdate(imm, false), imm.weekday() + " " + imm
                    + " is not an IMM date (calculated from "
                    + counter.weekday() + " " + counter + ")");
            // check that imm is <= to the next IMM date in the main cycle
            assertFalse(TimeUtils.greater(imm, IMM.nextDate(counter, true)), imm.weekday() + " " + imm
                    + " is not less than or equal to the next future in the main cycle "
                    + IMM.nextDate(counter, true));
            // check that for every date IMMdate is the inverse of IMMcode
            assertFalse(TimeUtils.neq(IMM.date(IMM.code(imm), counter), imm), IMM.code(imm)
                    + " at calendar day " + counter
                    + " is not the IMM code matching " + imm);
            // check that for every date the 120 IMM codes refer to future dates
            for (int i = 0; i < 40; ++i) {
                assertFalse(TimeUtils.less(IMM.date(IMMcodes[i], counter), counter), IMM.date(IMMcodes[i], counter)
                        + " is wrong for " + IMMcodes[i]
                        + " at reference date " + counter);
            }
            counter = counter.add(1);
        }
    }

    @Test
    public void asxDates() {
        System.out.println("Testing ASX dates...");
        String[] ASXcodes = {
                "F0", "G0", "H0", "J0", "K0", "M0", "N0", "Q0", "U0", "V0", "X0", "Z0",
                "F1", "G1", "H1", "J1", "K1", "M1", "N1", "Q1", "U1", "V1", "X1", "Z1",
                "F2", "G2", "H2", "J2", "K2", "M2", "N2", "Q2", "U2", "V2", "X2", "Z2",
                "F3", "G3", "H3", "J3", "K3", "M3", "N3", "Q3", "U3", "V3", "X3", "Z3",
                "F4", "G4", "H4", "J4", "K4", "M4", "N4", "Q4", "U4", "V4", "X4", "Z4",
                "F5", "G5", "H5", "J5", "K5", "M5", "N5", "Q5", "U5", "V5", "X5", "Z5",
                "F6", "G6", "H6", "J6", "K6", "M6", "N6", "Q6", "U6", "V6", "X6", "Z6",
                "F7", "G7", "H7", "J7", "K7", "M7", "N7", "Q7", "U7", "V7", "X7", "Z7",
                "F8", "G8", "H8", "J8", "K8", "M8", "N8", "Q8", "U8", "V8", "X8", "Z8",
                "F9", "G9", "H9", "J9", "K9", "M9", "N9", "Q9", "U9", "V9", "X9", "Z9"
        };

        for (Date counter = new Date(1, Month.JANUARY, 2000);
             TimeUtils.leq(counter, new Date(1, Month.JANUARY, 2040)); counter.addEquals(1)) {
            Date asx = ASX.nextDate(counter, false);

            // check that asx is greater than counter
            assertFalse(TimeUtils.leq(asx, counter), asx.weekday() + " " + asx
                    + " is not greater than "
                    + counter.weekday() + " " + counter);
            // check that asx is an ASX date
            assertTrue(ASX.isASXdate(asx, false), asx.weekday() + " " + asx
                    + " is not an ASX date (calculated from "
                    + counter.weekday() + " " + counter + ")");
            // check that asx is <= to the next ASX date in the main cycle
            assertFalse(TimeUtils.greater(asx, ASX.nextDate(counter, true)), asx.weekday() + " " + asx
                    + " is not less than or equal to the next future in the main cycle "
                    + ASX.nextDate(counter, true));
            // check that for every date ASXdate is the inverse of ASXcode
            assertFalse(TimeUtils.neq(ASX.date(ASX.code(asx), counter), asx), ASX.code(asx)
                    + " at calendar day " + counter
                    + " is not the ASX code matching " + asx);
            // check that for every date the 120 ASX codes refer to future dates
            for (String ASXcode : ASXcodes) {
                assertFalse(TimeUtils.less(ASX.date(ASXcode, counter), counter), ASX.date(ASXcode, counter) + " is wrong for " + ASXcode
                        + " at reference date " + counter);
            }
        }
    }

    @Test
    public void asxDatesSpecific() {
        System.out.println("Testing ASX functionality with specific dates...");
        // isASXdate
        {
            // date is ASX date depending on mainCycle
            Date date = new Date(12, Month.JANUARY, 2024);
            assertEquals(date.weekday(), Weekday.FRIDAY);

            // check mainCycle
            assertTrue(ASX.isASXdate(date, /*mainCycle*/false));
            assertFalse(ASX.isASXdate(date, /*mainCycle*/true));
        }

        // nextDate from code + ref date
        assertTrue(TimeUtils.equals(new Date(8, Month.FEBRUARY, 2002), ASX.nextDate("F2", /*mainCycle*/false, new Date(1, Month.JANUARY, 2000))));
        assertTrue(TimeUtils.equals(new Date(9, Month.JUNE, 2023), ASX.nextDate("K3", /*mainCycle*/true, new Date(1, Month.JANUARY, 2014))));

        // nextCode
        assertEquals("F4", ASX.nextCode(new Date(1, Month.JANUARY, 2024), /*mainCycle*/false));
        assertEquals("G4", ASX.nextCode(new Date(15, Month.JANUARY, 2024), /*mainCycle*/false));
        assertEquals("H4", ASX.nextCode(new Date(15, Month.JANUARY, 2024), /*mainCycle*/true));

        assertEquals("G4", ASX.nextCode("F4", /*mainCycle*/false, new Date(1, Month.JANUARY, 2020)));
        assertEquals("H5", ASX.nextCode("Z4", /*mainCycle*/true, new Date(1, Month.JANUARY, 2020)));
    }

    @Test
    public void testConsistency() {
        System.out.println("Testing dates...");
        int minDate = Date.minDate().serialNumber() + 1,
                maxDate = Date.maxDate().serialNumber();

        int dyold = new Date(minDate - 1).dayOfYear(),
                dold = new Date(minDate - 1).dayOfMonth(),
                mold = new Date(minDate - 1).month().getValue(),
                yold = new Date(minDate - 1).year(),
                wdold = new Date(minDate - 1).weekday().getValue();

        for (int i = minDate; i <= maxDate; i++) {
            Date t = new Date(i);
            int serial = t.serialNumber();

            // check serial number consistency
            assertFalse(serial != i, "inconsistent serial number:\n"
                    + "    original:      " + i + "\n"
                    + "    date:          " + t + "\n"
                    + "    serial number: " + serial);

            int dy = t.dayOfYear(),
                    d = t.dayOfMonth(),
                    m = t.month().getValue(),
                    y = t.year(),
                    wd = t.weekday().getValue();

            // check if skipping any date
            assertTrue((dy == dyold + 1) ||
                    (dy == 1 && dyold == 365 && !Date.isLeap(yold)) ||
                    (dy == 1 && dyold == 366 && Date.isLeap(yold)), "wrong day of year increment: \n"
                    + "    date: " + t + "\n"
                    + "    day of year: " + dy + "\n"
                    + "    previous:    " + dyold);
            dyold = dy;
            assertTrue(((d == dold + 1 && m == mold && y == yold) ||
                    (d == 1 && m == mold + 1 && y == yold) ||
                    (d == 1 && m == 1 && y == yold + 1)), "wrong day,month,year increment: \n"
                    + "    date: " + t + "\n"
                    + "    day,month,year: "
                    + d + "," + m + "," + y + "\n"
                    + "    previous:       "
                    + dold + "," + mold + "," + yold);
            dold = d;
            mold = m;
            yold = y;

            // check month definition
            assertFalse(m < 1 || m > 12, "invalid month: \n"
                    + "    date:  " + t + "\n"
                    + "    month: " + m);
            // check day definition
            assertFalse(d < 1, "invalid day of month: \n"
                    + "    date:  " + t + "\n"
                    + "    day: " + d);
            assertTrue((m == 1 && d <= 31) ||
                    (m == 2 && d <= 28) ||
                    (m == 2 && d == 29 && Date.isLeap(y)) ||
                    (m == 3 && d <= 31) ||
                    (m == 4 && d <= 30) ||
                    (m == 5 && d <= 31) ||
                    (m == 6 && d <= 30) ||
                    (m == 7 && d <= 31) ||
                    (m == 8 && d <= 31) ||
                    (m == 9 && d <= 30) ||
                    (m == 10 && d <= 31) ||
                    (m == 11 && d <= 30) ||
                    (m == 12 && d <= 31), "invalid day of month: \n"
                    + "    date:  " + t + "\n"
                    + "    day: " + d);

            // check weekday definition
            assertTrue(((wd) == (wdold + 1)) ||
                    ((wd) == 1 && (wdold) == 7), "invalid weekday: \n"
                    + "    date:  " + t + "\n"
                    + "    weekday:  " + (wd) + "\n"
                    + "    previous: " + (wdold));
            wdold = wd;

            // create the same date with a different constructor
            Date s = new Date(d, Month.fromValue(m), y);
            // check serial number consistency
            serial = s.serialNumber();
            assertFalse(serial != i, "inconsistent serial number:\n"
                    + "    date:          " + t + "\n"
                    + "    serial number: " + i + "\n"
                    + "    cloned date:   " + s + "\n"
                    + "    serial number: " + serial);
        }
    }

    @Test
    public void isoDates() {
        System.out.println("Testing ISO dates...");
        String input_date = "2006-01-15";
        Date d = DateParser.parseISO(input_date);
        assertFalse(d.dayOfMonth() != 15 ||
                d.month() != Month.JANUARY ||
                d.year() != 2006, "Iso date failed\n"
                + " input date:    " + input_date + "\n"
                + " day of month:  " + d.dayOfMonth() + "\n"
                + " month:         " + d.month() + "\n"
                + " year:          " + d.year());
    }
}
