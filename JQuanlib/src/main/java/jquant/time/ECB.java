package jquant.time;

import jquant.Settings;

import java.util.*;

import static jquant.math.CommonUtil.QL_REQUIRE;

/// /! European Central Bank reserve maintenance dates
public class ECB {
    // 1. 替代 C++ 的 boost::bimap: 使用一个标准的 Map
    //    由于我们只需要通过 String 查找 Month，单向 Map 足够了。
    private static final Map<String, Month> MONTH_MAP;

    // 静态初始化块，用于在类加载时填充 MONTH_MAP
    static {
        Map<String, Month> map = new HashMap<>();
        map.put("JAN", Month.JANUARY);
        map.put("FEB", Month.FEBRUARY);
        map.put("MAR", Month.MARCH);
        map.put("APR", Month.APRIL);
        map.put("MAY", Month.MAY);
        map.put("JUN", Month.JUNE);
        map.put("JUL", Month.JULY);
        map.put("AUG", Month.AUGUST);
        map.put("SEP", Month.SEPTEMBER);
        map.put("OCT", Month.OCTOBER);
        map.put("NOV", Month.NOVEMBER);
        map.put("DEC", Month.DECEMBER);

        // 保证线程安全和不可变性
        MONTH_MAP = Collections.unmodifiableMap(map);
    }

    public static Set<Date> knownDates() {
        return TimeUtils.ecbKnownDateSet;
    }

    public static void addDate(final Date d) {
        TimeUtils.ecbKnownDateSet.add(d);
    }

    public static void removeDate(final Date d) {
        TimeUtils.ecbKnownDateSet.remove(d);
    }

    /// /! maintenance period start date in the given month/year
    public static Date date(Month m, int y) {
        return nextDate(new Date(1, m, y).substract(1));
    }

    /**
     * ! returns the ECB date for the given ECB code
     * (e.g. March xxth, 2013 for MAR10).
     * <p>
     * \warning It raises an exception if the input
     * string is not an ECB code
     */
    public static Date date(String ecbCode, Date refDate) {
        QL_REQUIRE(isECBcode(ecbCode),
                ecbCode + " is not a valid ECB code");

        // convert first 3 characters to `Month m`
        String monthCode = ecbCode.substring(0, 3).toUpperCase();
        Month m = MONTH_MAP.get(monthCode);

        // convert 4th, 5th characters to `Year y`
        char char4 = ecbCode.charAt(3);
        char char5 = ecbCode.charAt(4);
        int y = Character.getNumericValue(char4) * 10 + Character.getNumericValue(char5);
        Date referenceDate = (TimeUtils.neq(refDate, new Date()) ?
                refDate :
                Settings.instance.evaluationDate().Date());
        int referenceYear = (referenceDate.year() % 100);
        y += referenceDate.year() - referenceYear;
        if (y < Date.minDate().year())
            return ECB.nextDate(Date.minDate());

        return ECB.nextDate(new Date(1, m, y).substract(1));
    }

    /**
     * ! returns the ECB code for the given date
     * (e.g. MAR10 for March xxth, 2010).
     * <p>
     * \warning It raises an exception if the input
     * date is not an ECB date
     */
    public static String code(final Date ecbDate) {
        QL_REQUIRE(isECBdate(ecbDate),
                ecbDate + " is not a valid ECB date");

        // 3 characters for the month
        Month m = ecbDate.month();
        String month = m.getShortName().toUpperCase();
        // last two digits of the year
        int y = ecbDate.year() % 100;

        // c-style string. length: 6 == (3 for month + 2 for year + 1 for terminating null)
        String ecbCode = String.format(
                Locale.ROOT,
                "%3s%02d",
                month,
                y
        );

        QL_REQUIRE(isECBcode(ecbCode),
                "the result " + ecbCode +
                        " is an invalid ECB code");
        return ecbCode;
    }

    /// /! next maintenance period start date following the given date
    public static Date nextDate(final Date date) {
        Date d = TimeUtils.equals(date, new Date()) ?
                Settings.instance.evaluationDate().Date() : date;

        int i = upper_bound(d);

        QL_REQUIRE(i != knownDates().size(),
                "ECB dates after " + get(knownDates().size() - 1) + " are unknown");
        return get(i);
    }

    /// /! next maintenance period start date following the given ECB code
    public static Date nextDate(String ecbCode,
                                Date referenceDate) {
        return nextDate(date(ecbCode, referenceDate));
    }

    /// /! next maintenance period start dates following the given date
    public static List<Date> nextDates(Date date) {
        Date d = (TimeUtils.equals(date, new Date()) ?
                Settings.instance.evaluationDate().Date() :
                date);

        int i = upper_bound(d);

        QL_REQUIRE(i != knownDates().size(),
                "ECB dates after " + get(knownDates().size() - 1) + " are unknown");
        return gets(i);
    }

    ////! next maintenance period start dates following the given code
    public static List<Date> nextDates(String ecbCode,
                                           Date referenceDate) {
        return nextDates(date(ecbCode, referenceDate));
    }

    /**
     * ! returns whether or not the given date is
     * a maintenance period start date
     */
    public static boolean isECBdate(final Date d) {
        Date date = nextDate(d.substract(1));
        return TimeUtils.equals(date, d);
    }

    /// /! returns whether or not the given string is an ECB code
    public static boolean isECBcode(final String ecbCode) {
        if (ecbCode == null || ecbCode.length() != 5) {
            return false;
        }
        try {
            String monthSubstring = ecbCode.substring(0, 3).toUpperCase();
            if (!MONTH_MAP.containsKey(monthSubstring)) {
                return false;
            }
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
        char char4 = ecbCode.charAt(3);
        char char5 = ecbCode.charAt(4);

        return Character.isDigit(char4) && Character.isDigit(char5);
    }

    ////! next ECB code following the given date
    public static String nextCode(Date d) {
        return code(nextDate(d));
    }

    ////! next ECB code following the given code
    public static String nextCode(String ecbCode) {
        QL_REQUIRE(isECBcode(ecbCode),
                ecbCode + " is not a valid ECB code");

        String month = ecbCode.substring(0, 3).toUpperCase();
        Month monthEnum = MONTH_MAP.get(month);

        StringBuilder nextCodeStr = new StringBuilder();
        if (monthEnum != Month.DECEMBER) {
            // use next month
            Month nextMonthEnum = Month.fromValue(monthEnum.getValue() + 1);
            String nextMonth = nextMonthEnum.getShortName().toUpperCase();
            nextCodeStr.append(nextMonth);

            // copy year
            nextCodeStr.append(ecbCode.charAt(3)).append(ecbCode.charAt(4));
        } else {
            // previous month was DEC
            nextCodeStr.append("JAN");
            String yearSubstring = ecbCode.substring(3, 5);
            int yearLastTwoDigits = Integer.parseInt(yearSubstring);
            // C++: 复杂的 incrementAndCheckForOverlow 字符操作逻辑
            // Java 替换：直接对年份整数进行递增和取模操作
            int nextYearLastTwoDigits = yearLastTwoDigits + 1;

            // 处理溢出：如果年份递增到 100（即 XX99 递增到 100），则取后两位 00。
            // 例如 '99' + 1 = 100 -> '00'
            // 实际上，我们只关心年份后两位，所以可以直接递增并对 100 取模。
            if (nextYearLastTwoDigits >= 100) {
                nextYearLastTwoDigits %= 100;
                nextCodeStr.append(0);
            }
            nextCodeStr.append(nextYearLastTwoDigits);
        }

        QL_REQUIRE(isECBcode(nextCodeStr.toString()),
                "the result " + nextCodeStr.toString() +
                        " is an invalid ECB code");
        return nextCodeStr.toString();
    }

    private static int upper_bound(Date d) {
        int index = 0;
        for (Date dd : knownDates()) {
            if (TimeUtils.less(d, dd)) {
                return index;
            }
            index++;
        }
        return index;
    }

    public static Date get(int index) {
        int i = 0;
        for (Date dd : knownDates()) {
            if (index == i) {
                return dd;
            }
            i++;
        }
        return new Date();
    }

    private static List<Date> gets(int index) {
        int i = 0;
        List<Date> dates = new ArrayList<>();
        for (Date dd : knownDates()) {
            if (i >= index) {
                dates.add(dd);
            }
            i++;
        }
        return dates;
    }
}
