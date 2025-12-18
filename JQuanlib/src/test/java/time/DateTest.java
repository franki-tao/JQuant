package time;

import jquant.time.Date;
import jquant.time.Month;

public class DateTest {
    public static void main(String[] args) {
        Date date = new Date(1, Month.JANUARY, 2025);
        Date d = new Date(367);
        System.out.println(d);
    }
}
