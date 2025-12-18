package time;

import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Month;
import jquant.time.Schedule;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.ActualActual;

public class DayCounterTest {
    public static void testactualactual() {
        Schedule s = new Schedule();
        DayCounter dc = new ActualActual(ActualActual.Convention.ISMA);
        Date d1 = Date.todaysDate();
        Date d2 = new Date(1, Month.JANUARY, 2025);
        System.out.println(dc.dayCount(d2, d1));
        System.out.println(dc.name());
    }

    public static void testactual360() {
        DayCounter dc = new Actual360(true);
        Date d1 = Date.todaysDate();
        Date d2 = new Date(1, Month.JANUARY, 2025);
        System.out.println(dc.dayCount(d2, d1));
        System.out.println(dc.name());
        System.out.println(dc.yearFraction(d2, d1, new Date(), new Date()));
    }
    public static void main(String[] args) {
        testactualactual();
        testactual360();
    }
}
