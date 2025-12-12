package jquant.time;

import jquant.math.Point;

import static jquant.math.CommonUtil.QL_FAIL;

public class TimeUtils {
    public static final int[] MonthLength = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };
    public static final int[] MonthLeapLength = {
            31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };
    public static final int[] MonthOffset = {
            0, 31, 59, 90, 120, 151,   // Jan - Jun
            181, 212, 243, 273, 304, 334,   // Jun - Dec
            365     // used in dayOfMonth to bracket day
    };
    public static final int[] MonthLeapOffset = {
            0, 31, 60, 91, 121, 152,   // Jan - Jun
            182, 213, 244, 274, 305, 335,   // Jun - Dec
            366     // used in dayOfMonth to bracket day
    };

    public static final int[] EasterMonday = {
            98,  90, 103,  95, 114, 106,  91, 111, 102,   // 1901-1909
            87, 107,  99,  83, 103,  95, 115,  99,  91, 111,   // 1910-1919
            96,  87, 107,  92, 112, 103,  95, 108, 100,  91,   // 1920-1929
            111,  96,  88, 107,  92, 112, 104,  88, 108, 100,   // 1930-1939
            85, 104,  96, 116, 101,  92, 112,  97,  89, 108,   // 1940-1949
            100,  85, 105,  96, 109, 101,  93, 112,  97,  89,   // 1950-1959
            109,  93, 113, 105,  90, 109, 101,  86, 106,  97,   // 1960-1969
            89, 102,  94, 113, 105,  90, 110, 101,  86, 106,   // 1970-1979
            98, 110, 102,  94, 114,  98,  90, 110,  95,  86,   // 1980-1989
            106,  91, 111, 102,  94, 107,  99,  90, 103,  95,   // 1990-1999
            115, 106,  91, 111, 103,  87, 107,  99,  84, 103,   // 2000-2009
            95, 115, 100,  91, 111,  96,  88, 107,  92, 112,   // 2010-2019
            104,  95, 108, 100,  92, 111,  96,  88, 108,  92,   // 2020-2029
            112, 104,  89, 108, 100,  85, 105,  96, 116, 101,   // 2030-2039
            93, 112,  97,  89, 109, 100,  85, 105,  97, 109,   // 2040-2049
            101,  93, 113,  97,  89, 109,  94, 113, 105,  90,   // 2050-2059
            110, 101,  86, 106,  98,  89, 102,  94, 114, 105,   // 2060-2069
            90, 110, 102,  86, 106,  98, 111, 102,  94, 114,   // 2070-2079
            99,  90, 110,  95,  87, 106,  91, 111, 103,  94,   // 2080-2089
            107,  99,  91, 103,  95, 115, 107,  91, 111, 103,   // 2090-2099
            88, 108, 100,  85, 105,  96, 109, 101,  93, 112,   // 2100-2109
            97,  89, 109,  93, 113, 105,  90, 109, 101,  86,   // 2110-2119
            106,  97,  89, 102,  94, 113, 105,  90, 110, 101,   // 2120-2129
            86, 106,  98, 110, 102,  94, 114,  98,  90, 110,   // 2130-2139
            95,  86, 106,  91, 111, 102,  94, 107,  99,  90,   // 2140-2149
            103,  95, 115, 106,  91, 111, 103,  87, 107,  99,   // 2150-2159
            84, 103,  95, 115, 100,  91, 111,  96,  88, 107,   // 2160-2169
            92, 112, 104,  95, 108, 100,  92, 111,  96,  88,   // 2170-2179
            108,  92, 112, 104,  89, 108, 100,  85, 105,  96,   // 2180-2189
            116, 101,  93, 112,  97,  89, 109, 100,  85, 105    // 2190-2199
    };
    // the list of all December 31st in the preceding year
    // e.g. for 1901 yearOffset[1] is 366, that is, December 31 1900
    public static final int[] YearOffset = {
            // 1900-1909
            0, 366, 731, 1096, 1461, 1827, 2192, 2557, 2922, 3288,
            // 1910-1919
            3653, 4018, 4383, 4749, 5114, 5479, 5844, 6210, 6575, 6940,
            // 1920-1929
            7305, 7671, 8036, 8401, 8766, 9132, 9497, 9862, 10227, 10593,
            // 1930-1939
            10958, 11323, 11688, 12054, 12419, 12784, 13149, 13515, 13880, 14245,
            // 1940-1949
            14610, 14976, 15341, 15706, 16071, 16437, 16802, 17167, 17532, 17898,
            // 1950-1959
            18263, 18628, 18993, 19359, 19724, 20089, 20454, 20820, 21185, 21550,
            // 1960-1969
            21915, 22281, 22646, 23011, 23376, 23742, 24107, 24472, 24837, 25203,
            // 1970-1979
            25568, 25933, 26298, 26664, 27029, 27394, 27759, 28125, 28490, 28855,
            // 1980-1989
            29220, 29586, 29951, 30316, 30681, 31047, 31412, 31777, 32142, 32508,
            // 1990-1999
            32873, 33238, 33603, 33969, 34334, 34699, 35064, 35430, 35795, 36160,
            // 2000-2009
            36525, 36891, 37256, 37621, 37986, 38352, 38717, 39082, 39447, 39813,
            // 2010-2019
            40178, 40543, 40908, 41274, 41639, 42004, 42369, 42735, 43100, 43465,
            // 2020-2029
            43830, 44196, 44561, 44926, 45291, 45657, 46022, 46387, 46752, 47118,
            // 2030-2039
            47483, 47848, 48213, 48579, 48944, 49309, 49674, 50040, 50405, 50770,
            // 2040-2049
            51135, 51501, 51866, 52231, 52596, 52962, 53327, 53692, 54057, 54423,
            // 2050-2059
            54788, 55153, 55518, 55884, 56249, 56614, 56979, 57345, 57710, 58075,
            // 2060-2069
            58440, 58806, 59171, 59536, 59901, 60267, 60632, 60997, 61362, 61728,
            // 2070-2079
            62093, 62458, 62823, 63189, 63554, 63919, 64284, 64650, 65015, 65380,
            // 2080-2089
            65745, 66111, 66476, 66841, 67206, 67572, 67937, 68302, 68667, 69033,
            // 2090-2099
            69398, 69763, 70128, 70494, 70859, 71224, 71589, 71955, 72320, 72685,
            // 2100-2109
            73050, 73415, 73780, 74145, 74510, 74876, 75241, 75606, 75971, 76337,
            // 2110-2119
            76702, 77067, 77432, 77798, 78163, 78528, 78893, 79259, 79624, 79989,
            // 2120-2129
            80354, 80720, 81085, 81450, 81815, 82181, 82546, 82911, 83276, 83642,
            // 2130-2139
            84007, 84372, 84737, 85103, 85468, 85833, 86198, 86564, 86929, 87294,
            // 2140-2149
            87659, 88025, 88390, 88755, 89120, 89486, 89851, 90216, 90581, 90947,
            // 2150-2159
            91312, 91677, 92042, 92408, 92773, 93138, 93503, 93869, 94234, 94599,
            // 2160-2169
            94964, 95330, 95695, 96060, 96425, 96791, 97156, 97521, 97886, 98252,
            // 2170-2179
            98617, 98982, 99347, 99713, 100078, 100443, 100808, 101174, 101539, 101904,
            // 2180-2189
            102269, 102635, 103000, 103365, 103730, 104096, 104461, 104826, 105191, 105557,
            // 2190-2199
            105922, 106287, 106652, 107018, 107383, 107748, 108113, 108479, 108844, 109209,
            // 2200
            109574
    };
    public static final boolean[] YearIsLeap = {
            // 1900 is leap in agreement with Excel's bug
            // 1900 is out of valid date range anyway
            // 1900-1909
            true, false, false, false, true, false, false, false, true, false,
            // 1910-1919
            false, false, true, false, false, false, true, false, false, false,
            // 1920-1929
            true, false, false, false, true, false, false, false, true, false,
            // 1930-1939
            false, false, true, false, false, false, true, false, false, false,
            // 1940-1949
            true, false, false, false, true, false, false, false, true, false,
            // 1950-1959
            false, false, true, false, false, false, true, false, false, false,
            // 1960-1969
            true, false, false, false, true, false, false, false, true, false,
            // 1970-1979
            false, false, true, false, false, false, true, false, false, false,
            // 1980-1989
            true, false, false, false, true, false, false, false, true, false,
            // 1990-1999
            false, false, true, false, false, false, true, false, false, false,
            // 2000-2009
            true, false, false, false, true, false, false, false, true, false,
            // 2010-2019
            false, false, true, false, false, false, true, false, false, false,
            // 2020-2029
            true, false, false, false, true, false, false, false, true, false,
            // 2030-2039
            false, false, true, false, false, false, true, false, false, false,
            // 2040-2049
            true, false, false, false, true, false, false, false, true, false,
            // 2050-2059
            false, false, true, false, false, false, true, false, false, false,
            // 2060-2069
            true, false, false, false, true, false, false, false, true, false,
            // 2070-2079
            false, false, true, false, false, false, true, false, false, false,
            // 2080-2089
            true, false, false, false, true, false, false, false, true, false,
            // 2090-2099
            false, false, true, false, false, false, true, false, false, false,
            // 2100-2109
            false, false, false, false, true, false, false, false, true, false,
            // 2110-2119
            false, false, true, false, false, false, true, false, false, false,
            // 2120-2129
            true, false, false, false, true, false, false, false, true, false,
            // 2130-2139
            false, false, true, false, false, false, true, false, false, false,
            // 2140-2149
            true, false, false, false, true, false, false, false, true, false,
            // 2150-2159
            false, false, true, false, false, false, true, false, false, false,
            // 2160-2169
            true, false, false, false, true, false, false, false, true, false,
            // 2170-2179
            false, false, true, false, false, false, true, false, false, false,
            // 2180-2189
            true, false, false, false, true, false, false, false, true, false,
            // 2190-2199
            false, false, true, false, false, false, true, false, false, false,
            // 2200
            false
    };

    public static Point<Integer, Integer> daysMinMax(final Period p) {
        switch (p.units()) {
            case DAYS:
                return new Point<>(p.length(), p.length());
            case WEEKS:
                return new Point<>(7 * p.length(), 7 * p.length());
            case MONTHS:
                return new Point<>(28 * p.length(), 31 * p.length());
            case YEARS:
                return new Point<>(365 * p.length(), 366 * p.length());
            default:
                QL_FAIL("unknown time unit (" + p.units() + ")");
        }
        return new Point<>();
    }

    public static double years(Period p) {
        if (p.length() == 0) return 0.0;

        switch (p.units()) {
            case DAYS:
                QL_FAIL("cannot convert Days into Years");
            case WEEKS:
                QL_FAIL("cannot convert Weeks into Years");
            case MONTHS:
                return p.length() / 12.0;
            case YEARS:
                return p.length();
            default:
                QL_FAIL("unknown time unit (" + p.units() + ")");
        }
        return 0.0;
    }

    public static double months(Period p) {
        if (p.length() == 0) return 0.0;

        switch (p.units()) {
            case DAYS:
                QL_FAIL("cannot convert Days into Months");
            case WEEKS:
                QL_FAIL("cannot convert Weeks into Months");
            case MONTHS:
                return p.length();
            case YEARS:
                return p.length() * 12.0;
            default:
                QL_FAIL("unknown time unit (" + p.units() + ")");
        }
        return 0.0;
    }

    public static double weeks(Period p) {
        if (p.length() == 0) return 0.0;

        switch (p.units()) {
            case DAYS:
                return p.length() / 7.0;
            case WEEKS:
                return p.length();
            case MONTHS:
                QL_FAIL("cannot convert Months into Weeks");
            case YEARS:
                QL_FAIL("cannot convert Years into Weeks");
            default:
                QL_FAIL("unknown time unit (" + p.units() + ")");
        }
        return 0.0;
    }

    public static double days(Period p) {
        if (p.length() == 0) return 0.0;

        switch (p.units()) {
            case DAYS:
                return p.length();
            case WEEKS:
                return p.length() * 7.0;
            case MONTHS:
                QL_FAIL("cannot convert Months into Days");
            case YEARS:
                QL_FAIL("cannot convert Years into Days");
            default:
                QL_FAIL("unknown time unit (" + p.units() + ")");
        }
        return 0.0;
    }

    public static Period multiply(int n, TimeUnit utils) {
        return new Period(n, utils);
    }

    public static Period multiply(TimeUnit utils, int n) {
        return new Period(n, utils);
    }

    public static Period negetive(Period p) {
        return new Period(-p.length(), p.units());
    }

    public static Period multiply(int n, Period p) {
        return new Period(n * p.length(), p.units());
    }

    public static Period multiply(Period p, int n) {
        return new Period(n * p.length(), p.units());
    }

    public static Period divide(Period p, int n) {
        Period result = p.clone();
        result.divideEquals(n);
        return result;
    }

    public static Period add(Period p1, Period p2) {
        Period result = p1.clone();
        result.addEquals(p2);
        return result;
    }

    public static Period subtract(Period p1, Period p2) {
        Period result = p1.clone();
        result.subtractEquals(p2);
        return result;
    }

    public static boolean less(Period p1, Period p2) {
        // special cases
        if (p1.length() == 0)
            return p2.length() > 0;
        if (p2.length() == 0)
            return p1.length() < 0;

        // exact comparisons
        if (p1.units() == p2.units())
            return p1.length() < p2.length();
        if (p1.units() == TimeUnit.MONTHS && p2.units() == TimeUnit.YEARS)
            return p1.length() < 12 * p2.length();
        if (p1.units() == TimeUnit.YEARS && p2.units() == TimeUnit.MONTHS)
            return 12 * p1.length() < p2.length();
        if (p1.units() == TimeUnit.DAYS && p2.units() == TimeUnit.WEEKS)
            return p1.length() < 7 * p2.length();
        if (p1.units() == TimeUnit.WEEKS && p2.units() == TimeUnit.DAYS)
            return 7 * p1.length() < p2.length();

        // inexact comparisons (handled by converting to days and using limits)
        Point<Integer, Integer> p1lim = daysMinMax(p1);
        Point<Integer, Integer> p2lim = daysMinMax(p2);

        if (p1lim.getSecond() < p2lim.getFirst())
            return true;
        else if (p1lim.getFirst() > p2lim.getSecond())
            return false;
        else
            QL_FAIL("undecidable comparison between " + p1 + " and " + p2);
        return false;
    }

    public static boolean greater(Period p1, Period p2) {
        return less(p2, p1);
    }

    public static boolean equals(Period p1, Period p2) {
        return !(less(p1, p2) || less(p2, p1));
    }

    public static boolean neq(Period p1, Period p2) {
        return !equals(p1, p2);
    }

    public static boolean leq(Period p1, Period p2) {
        return !less(p2, p1);
    }

    public static boolean geq(Period p1, Period p2) {
        return !greater(p2, p1);
    }

    public static int substract(final Date d1, final Date d2) {
        return d1.serialNumber() - d2.serialNumber();
    }

    public static boolean equals(final Date d1, final Date d2) {
        return d1.serialNumber() == d2.serialNumber();
    }

    public static boolean neq(final Date d1, final Date d2) {
        return d1.serialNumber() != d2.serialNumber();
    }

    public static boolean less(final Date d1, final Date d2) {
        return d1.serialNumber() < d2.serialNumber();
    }

    public static boolean greater(final Date d1, final Date d2) {
        return d1.serialNumber() > d2.serialNumber();
    }

    public static boolean leq(final Date d1, final Date d2) {
        return d1.serialNumber() <= d2.serialNumber();
    }

    public static boolean geq(final Date d1, final Date d2) {
        return d1.serialNumber() >= d2.serialNumber();
    }

    /*! \relates Date
        \brief Difference in days (including fraction of days) between dates
    */
    public static double daysBetween(Date d1, Date d2) {
        return (double) (substract(d2, d1));
    }
}
