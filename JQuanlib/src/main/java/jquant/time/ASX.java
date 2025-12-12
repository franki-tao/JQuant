package jquant.time;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_ASSERT;
import static jquant.math.CommonUtil.QL_REQUIRE;


//! Main cycle of the Australian Securities Exchange (a.k.a. ASX) months
public class ASX {
    public enum Month {
        F, G, H,
        J, K, M,
        N, Q, U,
        V, X, Z
    }

    // 用于高效查找的 Set 结构
    public static final String All_MONTH_CODES = "FGHJKMNQUVXZ";
    private static final Set<Character> ALL_MONTH_CODES_SET = new HashSet<>();

    static {
        // 初始化 Set，以便快速查找 All_MONTH_CODES 中的字符
        for (char c : All_MONTH_CODES.toCharArray()) {
            ALL_MONTH_CODES_SET.add(c);
        }
    }

    //! returns whether or not the given date is an ASX date
    public static boolean isASXdate(final Date date, boolean mainCycle) {
        if (date.weekday() != Weekday.FRIDAY)
            return false;

        int d = date.dayOfMonth();
        if (d < 8 || d > 14)
            return false;

        if (!mainCycle) return true;

        return switch (date.month()) {
            case MARCH, JUNE, SEPTEMBER, DECEMBER -> true;
            default -> false;
        };
    }

    //! returns whether or not the given string is an ASX code
    public static boolean isASXcode(final String in, boolean mainCycle) {
        if (in.length() != 2)
            return false;

        // 2nd character of code needs to be digit
        if (!Character.isDigit(in.charAt(1)))
            return false;

        final char firstCharUpper = Character.toUpperCase(in.charAt(0));
        final Set<Character> validMonthCodes;
        if (mainCycle) {
            // 主周期代码集：H, M, U, Z (三月、六月、九月、十二月)
            validMonthCodes = new HashSet<>(Arrays.asList('H', 'M', 'U', 'Z'));
        } else {
            // 所有有效代码集
            validMonthCodes = ALL_MONTH_CODES_SET;
        }
        return validMonthCodes.contains(firstCharUpper);
    }

    /*! returns the ASX code for the given date
        (e.g. M5 for June 12th, 2015).

        \warning It raises an exception if the input
                 date is not an ASX date
    */
    public static String code(final Date date) {
        QL_REQUIRE(isASXdate(date, false),
                date + " is not an ASX date");

        // month() is 1-based!
        final char monthCode = All_MONTH_CODES.charAt(date.month().getValue() - 1);
        final char yearDigit = (char) ('0' + (date.year() % 10));
        String code = new String(new char[]{monthCode, yearDigit});

        QL_REQUIRE(isASXcode(code, false),
                "the result " + code +
                        " is an invalid ASX code");
        return code;
    }

    /*! returns the ASX date for the given ASX code
        (e.g. June 12th, 2015 for M5).

        \warning It raises an exception if the input
                 string is not an ASX code
    */
    public static Date date(final String asxCode, final Date refDate) {
        QL_REQUIRE(isASXcode(asxCode, false),
                asxCode + " is not a valid ASX code");

        final Date referenceDate = (refDate != null ?
                refDate :
                Date.todaysDate());

        final char ms = Character.toUpperCase(asxCode.charAt(0));
        int idxZeroBased = All_MONTH_CODES.indexOf(ms);
        QL_ASSERT(idxZeroBased != -1, "invalid ASX month letter. code: " + asxCode);

        // QuantLib::Month is 1-based!
        jquant.time.Month m = jquant.time.Month.fromValue(idxZeroBased + 1);

        // convert 2nd char to year digit
        int y = asxCode.charAt(1)-'0'; //static_cast<int>(asxCode[1]) - static_cast<int>('0');
        QL_ASSERT((y>=0) && (y <= 9), "invalid ASX year digit. code: " + asxCode);

        /* year<1900 are not valid QuantLib years: to avoid a run-time
           exception few lines below we need to add 10 years right away */
        if (y==0 && referenceDate.year()<=1909) y+=10;
        int referenceYear = (referenceDate.year() % 10);
        y += referenceDate.year() - referenceYear;
        Date result = nextDate(new Date(1, m, y), false);
        return (TimeUtils.geq(result, referenceDate)) ? result : nextDate(new Date(1, m, y+10), false);
    }

    //! next ASX date following the given ASX code
    /*! returns the 1st delivery date for next contract listed in the
        Australian Securities Exchange
    */
    public static Date nextDate(Date date, boolean mainCycle) {
        Date refDate = (date == null ?
                Date.todaysDate() :
        date);
        int y = refDate.year();
        jquant.time.Month m = refDate.month();

        int offset = mainCycle ? 3 : 1;
        int skipMonths = offset-(m.getValue()%offset);
        if (skipMonths != offset || refDate.dayOfMonth() > 14) {
            skipMonths += (m.getValue());
            if (skipMonths<=12) {
                m = jquant.time.Month.fromValue(skipMonths);
            } else {
                m = jquant.time.Month.fromValue(skipMonths-12);
                y += 1;
            }
        }

        Date result = Date.nthWeekday(2, Weekday.FRIDAY, m, y);
        if (TimeUtils.leq(result,refDate))
            result = nextDate(new Date(15, m, y), mainCycle);
        return result;
    }

    //! next ASX date following the given ASX code
    /*! returns the 1st delivery date for next contract listed in the
        Australian Securities Exchange
    */
    public static Date nextDate(String asxCode, boolean mainCycle, Date refDate) {
        Date asxDate = date(asxCode, refDate);
        return nextDate(asxDate.add(1),  mainCycle);
    }

    //! next ASX code following the given date
    /*! returns the ASX code for next contract listed in the
        Australian Securities Exchange
    */
    public static String nextCode(Date d,  boolean mainCycle) {
        Date date = nextDate(d, mainCycle);
        return code(date);
    }

    //! next ASX code following the given code
    /*! returns the ASX code for next contract listed in the
        Australian Securities Exchange
    */
    public static String nextCode(String asxCode, boolean mainCycle, Date refDate) {
        Date date = nextDate(asxCode, mainCycle, refDate);
        return code(date);
    }

    public static void main(String[] args) {
        String code = ASX.code(Date.todaysDate());
        System.out.println(ASX.code(Date.todaysDate()));
        System.out.println(ASX.date(code, null));
    }
}
