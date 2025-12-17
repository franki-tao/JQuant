package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.TimeUtils.daysBetween;

public class ActualActualOldISMAImpl extends DayCounterImpl {
    @Override
    public String name() {
        return "Actual/Actual (ISMA)";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date d3, Date d4) {
        if (TimeUtils.equals(d1, d2))
            return 0.0;

        if (TimeUtils.greater(d1, d2))
            return -yearFraction(d2, d1, d3, d4);

        // when the reference period is not specified, try taking
        // it equal to (d1,d2)
        Date refPeriodStart = (TimeUtils.neq(d3, new Date()) ? d3 : d1);
        Date refPeriodEnd = (TimeUtils.neq(d4, new Date()) ? d4 : d2);

        QL_REQUIRE(TimeUtils.greater(refPeriodEnd, refPeriodStart) && TimeUtils.greater(refPeriodEnd, d1),
                "invalid reference period: "
                        + "date 1: " + d1
                        + ", date 2: " + d2
                        + ", reference period start: " + refPeriodStart
                        + ", reference period end: " + refPeriodEnd);

        // estimate roughly the length in months of a period
        int months = (int) Math.round(12 * (double) TimeUtils.substract(refPeriodEnd, refPeriodStart) / 365);

        // for short periods...
        if (months == 0) {
            // ...take the reference period as 1 year from d1
            refPeriodStart = d1;
            refPeriodEnd = d1.add(TimeUtils.multiply(1, TimeUnit.YEARS));
            months = 12;
        }

        double period = (months) / 12.0;

        if (TimeUtils.leq(d2, refPeriodEnd)) {
            // here refPeriodEnd is a future (notional?) payment date
            if (TimeUtils.geq(d1, refPeriodStart)) {
                // here refPeriodStart is the last (maybe notional)
                // payment date.
                // refPeriodStart <= d1 <= d2 <= refPeriodEnd
                // [maybe the equality should be enforced, since
                // refPeriodStart < d1 <= d2 < refPeriodEnd
                // could give wrong results] ???
                return period * (double) (daysBetween(d1, d2)) /
                        daysBetween(refPeriodStart, refPeriodEnd);
            } else {
                // here refPeriodStart is the next (maybe notional)
                // payment date and refPeriodEnd is the second next
                // (maybe notional) payment date.
                // d1 < refPeriodStart < refPeriodEnd
                // AND d2 <= refPeriodEnd
                // this case is long first coupon

                // the last notional payment date
                Date previousRef = refPeriodStart.substract(TimeUtils.multiply(months, TimeUnit.MONTHS));

                if (TimeUtils.greater(d2, refPeriodStart))
                    return yearFraction(d1, refPeriodStart, previousRef,
                            refPeriodStart) +
                            yearFraction(refPeriodStart, d2, refPeriodStart,
                                    refPeriodEnd);
                else
                    return yearFraction(d1, d2, previousRef, refPeriodStart);
            }
        } else {
            // here refPeriodEnd is the last (notional?) payment date
            // d1 < refPeriodEnd < d2 AND refPeriodStart < refPeriodEnd
            QL_REQUIRE(TimeUtils.leq(refPeriodStart, d1),
                    "invalid dates: " +
                            "d1 < refPeriodStart < refPeriodEnd < d2");
            // now it is: refPeriodStart <= d1 < refPeriodEnd < d2

            // the part from d1 to refPeriodEnd
            double sum = yearFraction(d1, refPeriodEnd,
                    refPeriodStart, refPeriodEnd);

            // the part from refPeriodEnd to d2
            // count how many regular periods are in [refPeriodEnd, d2],
            // then add the remaining time
            int i = 0;
            Date newRefStart, newRefEnd;
            for (; ; ) {
                newRefStart = refPeriodEnd.add(TimeUtils.multiply((months * i), TimeUnit.MONTHS));
                newRefEnd = refPeriodEnd.add(TimeUtils.multiply((months * (i + 1)), TimeUnit.MONTHS));
                if (TimeUtils.less(d2, newRefEnd)) {
                    break;
                } else {
                    sum += period;
                    i++;
                }
            }
            sum += yearFraction(newRefStart, d2, newRefStart, newRefEnd);
            return sum;
        }
    }
}
