package jquant.time;

import jquant.Settings;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Main cycle of the International %Money Market (a.k.a. %IMM) months
public class IMM {
    public enum Month {
        F, G, H,
        J, K, M,
        N, Q, U,
        V, X, Z
    }

    //! returns whether or not the given date is an IMM date
    public static boolean isIMMdate(Date date,
                                    boolean mainCycle) {
        if (date.weekday() != Weekday.WEDNESDAY)
            return false;

        int d = date.dayOfMonth();
        if (d < 15 || d > 21)
            return false;

        if (!mainCycle) return true;

        return switch (date.month()) {
            case MARCH, JUNE, SEPTEMBER, DECEMBER -> true;
            default -> false;
        };
    }

    //! returns whether or not the given string is an IMM code
    public static boolean isIMMCode(final String in, boolean mainCycle) {
        if (in == null || in.length() != 2) {
            return false;
        }
        char secondChar = in.charAt(1);
        if (!Character.isDigit(secondChar)) {
            return false;
        }
        char firstChar = in.charAt(0);
        String validFirstChars;

        if (mainCycle) {
            validFirstChars = "hmzuHMZU";
        } else {
            validFirstChars = "fghjkmnquvxzFGHJKMNQUVXZ";
        }
        return validFirstChars.indexOf(firstChar) != -1;
    }

    /*! returns the IMM code for the given date
        (e.g. H3 for March 20th, 2013).

        \warning It raises an exception if the input
                 date is not an IMM date
    */
    public static String code(final Date date) {
        QL_REQUIRE(isIMMdate(date, false),
                date + " is not an IMM date");

        StringBuilder IMMcode = new StringBuilder();
        int y = date.year() % 10;
        switch (date.month()) {
            case JANUARY:
                IMMcode.append('F').append(y);
                break;
            case FEBRUARY:
                IMMcode.append('G').append(y);
                break;
            case MARCH:
                IMMcode.append('H').append(y);
                break;
            case APRIL:
                IMMcode.append('J').append(y);
                break;
            case MAY:
                IMMcode.append('K').append(y);
                break;
            case JUNE:
                IMMcode.append('M').append(y);
                break;
            case JULY:
                IMMcode.append('N').append(y);
                break;
            case AUGUST:
                IMMcode.append('Q').append(y);
                break;
            case SEPTEMBER:
                IMMcode.append('U').append(y);
                break;
            case OCTOBER:
                IMMcode.append('V').append(y);
                break;
            case NOVEMBER:
                IMMcode.append('X').append(y);
                break;
            case DECEMBER:
                IMMcode.append('Z').append(y);
                break;
            default:
                QL_FAIL("not an IMM month (and it should have been)");
        }

        QL_REQUIRE(isIMMCode(IMMcode.toString(), false),
                "the result " + IMMcode.toString() +
                        " is an invalid IMM code");
        return IMMcode.toString();
    }

    /*! returns the IMM date for the given IMM code
        (e.g. March 20th, 2013 for H3).

        \warning It raises an exception if the input
                 string is not an IMM code
    */
    public static Date date(final String immCode,
                            final Date refDate) {
        QL_REQUIRE(isIMMCode(immCode, false),
                immCode + " is not a valid IMM code");

        Date referenceDate = (TimeUtils.neq(refDate, new Date()) ?
                refDate :
                Settings.instance.evaluationDate().Date());

        String code = immCode.toUpperCase();
        char ms = code.charAt(0);
        jquant.time.Month m = jquant.time.Month.NOVEMBER;
        if (ms == 'F') m = jquant.time.Month.JANUARY;
        else if (ms == 'G') m = jquant.time.Month.FEBRUARY;
        else if (ms == 'H') m = jquant.time.Month.MARCH;
        else if (ms == 'J') m = jquant.time.Month.APRIL;
        else if (ms == 'K') m = jquant.time.Month.MAY;
        else if (ms == 'M') m = jquant.time.Month.JUNE;
        else if (ms == 'N') m = jquant.time.Month.JULY;
        else if (ms == 'Q') m = jquant.time.Month.AUGUST;
        else if (ms == 'U') m = jquant.time.Month.SEPTEMBER;
        else if (ms == 'V') m = jquant.time.Month.OCTOBER;
        else if (ms == 'X') m = jquant.time.Month.NOVEMBER;
        else if (ms == 'Z') m = jquant.time.Month.DECEMBER;
        else QL_FAIL("invalid IMM month letter");

        int y = Character.getNumericValue(code.charAt(1));
        /* year<1900 are not valid QuantLib years: to avoid a run-time
           exception few lines below we need to add 10 years right away */
        if (y == 0 && referenceDate.year() <= 1909) y += 10;
        int referenceYear = (referenceDate.year() % 10);
        y += referenceDate.year() - referenceYear;
        Date result = IMM.nextDate(new Date(1, m, y), false);
        if (TimeUtils.less(result, referenceDate))
            return IMM.nextDate(new Date(1, m, y + 10), false);
        return result;
    }

    //! next IMM date following the given date
    /*! returns the 1st delivery date for next contract listed in the
        International Money Market section of the Chicago Mercantile
        Exchange.
    */
    public static Date nextDate(Date date, boolean mainCycle) {
        Date refDate = (TimeUtils.equals(date, new Date()) ?
                Settings.instance.evaluationDate().Date() :
                date);
        int y = refDate.year();
        jquant.time.Month m = refDate.month();

        int offset = mainCycle ? 3 : 1;
        int skipMonths = offset - (m.getValue() % offset);
        if (skipMonths != offset || refDate.dayOfMonth() > 21) {
            skipMonths += (m.getValue());
            if (skipMonths <= 12) {
                m = jquant.time.Month.fromValue(skipMonths);
            } else {
                m = jquant.time.Month.fromValue(skipMonths - 12);
                y += 1;
            }
        }

        Date result = Date.nthWeekday(3, Weekday.WEDNESDAY, m, y);
        if (TimeUtils.leq(result, refDate))
            result = nextDate(new Date(22, m, y), mainCycle);
        return result;
    }

    //! next IMM date following the given IMM code
    /*! returns the 1st delivery date for next contract listed in the
        International Money Market section of the Chicago Mercantile
        Exchange.
    */
    public static Date nextDate(String IMMcode,
                                boolean mainCycle,
                                Date referenceDate) {
        Date immDate = date(IMMcode, referenceDate);
        return nextDate(immDate.add(1), mainCycle);
    }

    //! next IMM code following the given date
    /*! returns the IMM code for next contract listed in the
        International Money Market section of the Chicago Mercantile
        Exchange.
    */
    public static String nextCode(Date d,
                                  boolean mainCycle) {
        Date date = nextDate(d, mainCycle);
        return code(date);
    }

    //! next IMM code following the given code
    /*! returns the IMM code for next contract listed in the
        International Money Market section of the Chicago Mercantile
        Exchange.
    */
    public static String nextCode(String immCode,
                                  boolean mainCycle,
                                  Date referenceDate) {
        Date date = nextDate(immCode, mainCycle, referenceDate);
        return code(date);
    }

    public static void main(String[] args) {
        System.out.println(IMM.code(Date.todaysDate().add(2)));
    }
}
