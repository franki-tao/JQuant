package time;

import jquant.time.Date;
import jquant.time.ECB;
import jquant.time.Month;

public class ECBTest {
    public static void main(String[] args) {
        Date d = ECB.date(Month.JULY, 2023);
        System.out.println(d);
        String c = ECB.code(d);
        System.out.println(c);
        System.out.println(ECB.nextCode(d));
        System.out.println(ECB.nextCode(c));
    }
}
