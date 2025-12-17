package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import static jquant.time.TimeUtils.daysBetween;

public class ActualActualAFBImpl extends DayCounterImpl {
    @Override
    public String name() {
        return "Actual/Actual (AFB)";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        if (TimeUtils.equals(d1, d2))
            return 0.0;

        if (TimeUtils.greater(d1, d2))
            return -yearFraction(d2, d1, new Date(), new Date());

        Date newD2 = d2.copy(), temp = d2.copy();
        double sum = 0.0;
        while (TimeUtils.greater(temp, d1)) {
            temp = newD2.substract(TimeUtils.multiply(1, TimeUnit.YEARS));
            if (temp.dayOfMonth() == 28 && temp.month().getValue() == 2
                    && Date.isLeap(temp.year())) {
                temp.addEquals(1);
            }
            if (TimeUtils.geq(temp, d1)) {
                sum += 1.0;
                newD2 = temp.copy();
            }
        }

        double den = 365.0;

        if (Date.isLeap(newD2.year())) {
            temp = new Date(29, Month.FEBRUARY, newD2.year());
            if (TimeUtils.greater(newD2, temp) && TimeUtils.leq(d1, temp))
                den += 1.0;
        } else if (Date.isLeap(d1.year())) {
            temp = new Date(29, Month.FEBRUARY, d1.year());
            if (TimeUtils.greater(newD2, temp) && TimeUtils.leq(d1, temp))
                den += 1.0;
        }

        return sum + daysBetween(d1, newD2) / den;
    }
}
