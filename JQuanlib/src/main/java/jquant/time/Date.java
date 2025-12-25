package jquant.time;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Concrete date class
/*! This class provides methods to inspect dates as well as methods and
    operators which implement a limited date algebra (increasing and
    decreasing dates, and calculating their difference).

    \ingroup datetime

    \test self-consistency of dates, serial numbers, days of
          month, months, and weekdays is checked over the whole
          date range.
*/
public class Date {
    private int serialNumber_;

    //! Default constructor returning a null date.
    public Date() {
        serialNumber_ = 0;
    }

    //! Constructor taking a serial number as given by Applix or Excel.
    public Date(int serialNumber) {
        serialNumber_ = serialNumber;
        checkSerialNumber(serialNumber);
    }

    //! More traditional constructor.
    public Date(int d, Month m, int y) {
        QL_REQUIRE(y > 1900 && y < 2200,
                "year " + y + " out of bound. It must be in [1901,2199]");
        QL_REQUIRE(m.getValue() > 0 && m.getValue() < 13,
                "month " + m.getValue()
                        + " outside January-December range [1,12]");

        boolean leap = isLeap(y);
        int len = monthLength(m, leap), offset = monthOffset(m, leap);
        QL_REQUIRE(d <= len && d > 0,
                "day outside month (" + m.getValue() + ") day-range "
                        + "[1," + len + "]");

        serialNumber_ = d + offset + yearOffset(y);
    }

    public Date copy() {
        return new Date(serialNumber_);
    }

    //! \name inspectors
    public Weekday weekday() {
        int w = serialNumber_ % 7;
        return Weekday.valueOf(w == 0 ? 7 : w);
    }

    public int dayOfMonth() {
        return dayOfYear() - monthOffset(month(), isLeap(year()));
    }

    //! One-based (Jan 1st = 1)
    public int dayOfYear() {
        return serialNumber_ - yearOffset(year());
    }

    public Month month() {
        int d = dayOfYear(); // dayOfYear is 1 based
        int m = d / 30 + 1;
        boolean leap = isLeap(year());
        while (d <= monthOffset(m, leap))
            --m;
        while (d > monthOffset(m + 1, leap)) // NOLINT(misc-misplaced-widening-cast)
            ++m;
        return Month.fromValue(m);
    }

    public int year() {
        int y = (serialNumber_ / 365) + 1900;
        // yearOffset(y) is December 31st of the preceding year
        if (serialNumber_ <= yearOffset(y))
            --y;
        return y;
    }

    public int serialNumber() {
        return serialNumber_;
    }

    //! \name date algebra
    //! increments date by the given number of days
    public Date addEquals(int days) {
        int serial = serialNumber_ + days;
        checkSerialNumber(serial);
        serialNumber_ = serial;
        return this;
    }

    //! increments date by the given period
    public Date addEquals(final Period p) {
        serialNumber_ = advance(this, p.length(), p.units()).serialNumber();
        return this;
    }

    //! decrement date by the given number of days
    public Date substractEquals(int days) {
        int serial = serialNumber_ - days;
        checkSerialNumber(serial);
        serialNumber_ = serial;
        return this;
    }

    //! decrements date by the given period
    public Date substractEquals(final Period p) {
        serialNumber_ = advance(this, -p.length(), p.units()).serialNumber();
        return this;
    }

    //! returns a new date incremented by the given number of days
    public Date add(int days) {
        return new Date(serialNumber_ + days);
    }

    //! returns a new date incremented by the given period
    public Date add(final Period p) {
        return advance(this, p.length(), p.units());
    }

    //! returns a new date decremented by the given number of days
    public Date substract(int days) {
        return new Date(serialNumber_ - days);
    }

    //! returns a new date decremented by the given period
    public Date substract(final Period p) {
        return advance(this, -p.length(), p.units());
    }

    //! today's date.
    public static Date todaysDate() {
        // 1. 获取系统默认时区：对应 C++ 的 localtime()
        ZoneId localZone = ZoneId.systemDefault();

        // 2. 获取当前日期时间：从系统时间戳开始
        LocalDate today = LocalDate.now(localZone);

        // 3. 提取日、月、年 (Java 的值是标准的，无需 +1 或 +1900)
        int day = today.getDayOfMonth();
        int month = today.getMonthValue(); // 月份是 1-12
        int year = today.getYear();
        return new Date(day, Month.fromValue(month), year);
    }

    //! earliest allowed date
    public static Date minDate() {
        return new Date(minimumSerialNumber());
    }

    //! latest allowed date
    public static Date maxDate() {
        return new Date(maximumSerialNumber());
    }

    //! whether the given year is a leap one
    public static boolean isLeap(int y) {
        QL_REQUIRE(y >= 1900 && y <= 2200, "year outside valid range");
        return TimeUtils.YearIsLeap[y - 1900];
    }

    //! first day of the month to which the given date belongs
    public static Date startOfMonth(final Date d) {
        Month m = d.month();
        int y = d.year();
        return new Date(1, m, y);
    }

    //! whether a date is the first day of its month
    public static boolean isStartOfMonth(final Date d) {
        return d.dayOfMonth() == 1;
    }

    //! last day of the month to which the given date belongs
    public static Date endOfMonth(final Date d) {
        Month m = d.month();
        int y = d.year();
        return new Date(monthLength(m, isLeap(y)), m, y);
    }

    //! whether a date is the last day of its month
    public static boolean isEndOfMonth(final Date d) {
        return (d.dayOfMonth() == monthLength(d.month(), isLeap(d.year())));
    }

    //! next given weekday following or equal to the given date
    /*! E.g., the Friday following Tuesday, January 15th, 2002
        was January 18th, 2002.

        see http://www.cpearson.com/excel/DateTimeWS.htm
    */
    public static Date nextWeekday(final Date d, Weekday dayOfWeek) {
        Weekday wd = d.weekday();
        return d.add((wd.getValue() > dayOfWeek.getValue() ? 7 : 0) - wd.getValue() + dayOfWeek.getValue());
    }

    //! n-th given weekday in the given month and year
    /*! E.g., the 4th Thursday of March, 1998 was March 26th,
        1998.

        see http://www.cpearson.com/excel/DateTimeWS.htm
    */
    public static Date nthWeekday(int nth,
                                  Weekday dayOfWeek,
                                  Month m,
                                  int y) {
        QL_REQUIRE(nth > 0,
                "zeroth day of week in a given (month, year) is undefined");
        QL_REQUIRE(nth < 6,
                "no more than 5 weekday in a given (month, year)");
        Weekday first = new Date(1, m, y).weekday();
        int skip = nth - (dayOfWeek.getValue() >= first.getValue() ? 1 : 0);
        return new Date((1 + dayOfWeek.getValue() + skip * 7 - first.getValue()), m, y);
    }

    private static int minimumSerialNumber() {
        return 367;       // Jan 1st, 1901
    }

    private static int maximumSerialNumber() {
        return 109574;    // Dec 31st, 2199
    }

    private static void checkSerialNumber(int serialNumber) {
        QL_REQUIRE(serialNumber >= minimumSerialNumber() &&
                        serialNumber <= maximumSerialNumber(),
                "Date's serial number (" + serialNumber + ") outside " +
                        "allowed range [" + minimumSerialNumber() +
                        "-" + maximumSerialNumber() + "]");
    }

    private static Date advance(final Date date, int n, TimeUnit units) {
        switch (units) {
            case DAYS:
                return date.add(n);
            case WEEKS:
                return date.add(7 * n);
            case MONTHS: {
                int d = date.dayOfMonth();
                int m = date.month().getValue() + n;
                int y = date.year();
                while (m > 12) {
                    m -= 12;
                    y += 1;
                }
                while (m < 1) {
                    m += 12;
                    y -= 1;
                }

                QL_REQUIRE(y >= 1900 && y <= 2199,
                        "year " + y + " out of bounds. "
                                + "It must be in [1901,2199]");

                int length = monthLength(Month.fromValue(m), isLeap(y));
                if (d > length)
                    d = length;

                return new Date(d, Month.fromValue(m), y);
            }
            case YEARS: {
                int d = date.dayOfMonth();
                Month m = date.month();
                int y = date.year() + n;

                QL_REQUIRE(y >= 1900 && y <= 2199,
                        "year " + y + " out of bounds. "
                                + "It must be in [1901,2199]");

                if (d == 29 && m == Month.FEBRUARY && !isLeap(y))
                    d = 28;

                return new Date(d, m, y);
            }
            default:
                QL_FAIL("undefined time units");
        }
        return new Date();
    }

    private static int monthLength(Month m, boolean leapYear) {
        return (leapYear ? TimeUtils.MonthLeapLength[m.getValue() - 1] : TimeUtils.MonthLength[m.getValue() - 1]);
    }

    private static int monthOffset(Month m, boolean leapYear) {
        return (leapYear ? TimeUtils.MonthLeapOffset[m.getValue() - 1] : TimeUtils.MonthOffset[m.getValue() - 1]);
    }

    private static int monthOffset(int m, boolean leapYear) {
        return (leapYear ? TimeUtils.MonthLeapOffset[m - 1] : TimeUtils.MonthOffset[m - 1]);
    }

    private static int yearOffset(int y) {
        return TimeUtils.YearOffset[y - 1900];
    }

    @Override
    public String toString() {
        if (serialNumber_ < minimumSerialNumber() || serialNumber_ > maximumSerialNumber()) {
            return "null";
        }
        int dd = dayOfMonth();
        int mm = month().getValue();
        int yy = year();
        LocalDate date = LocalDate.of(yy, mm, dd);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        return date.format(formatter);
    }

    public static void main(String[] args) {
        Date d = Date.todaysDate();
        System.out.println(d);
        Date d50 = d.add(50);
        System.out.println(d50);
        System.out.println(d.substract(230));
        System.out.println(d.weekday());
        System.out.println(d.dayOfMonth());
        System.out.println(d.month());
        System.out.println(d.dayOfYear());
    }
}
