package jquant.utilities;

import jquant.time.Date;
import jquant.time.Month;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class DateParser {
    //! Parses a string in a used-defined format.
    /*! This method uses the parsing functions from
        Boost.Date_Time and supports the same formats.
    */
    public static Date parseISO(String str) {
        QL_REQUIRE(str.length() == 10 && str.charAt(4) == '-' && str.charAt(7) == '-',
                "invalid format");
        int year = Integer.parseInt(str.substring(0, 4));
        Month month = Month.fromValue(Integer.parseInt(str.substring(5,7)));
        int day = Integer.parseInt(str.substring(8));

        return new Date(day, month, year);
    }
}
