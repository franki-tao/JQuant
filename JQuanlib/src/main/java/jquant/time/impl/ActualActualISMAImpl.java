package jquant.time.impl;

import jquant.time.Date;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.TimeUtils.*;

public class ActualActualISMAImpl extends DayCounterImpl {
    private Schedule schedule_;

    public ActualActualISMAImpl(Schedule schedule) {
        schedule_ = schedule;
    }

    @Override
    public String name() {
        return "Actual/Actual (ISMA)";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date d3, Date d4) {
        if (TimeUtils.equals(d1, d2)) {
            return 0.0;
        } else if (TimeUtils.less(d2, d1)) {
            return -yearFraction(d2, d1, d3, d4);
        }

        List<Date> couponDates =
                getListOfPeriodDatesIncludingQuasiPayments(schedule_);

        Date firstDate = min_date(couponDates);
        Date lastDate = max_date(couponDates);

        QL_REQUIRE(TimeUtils.geq(d1, firstDate) && TimeUtils.leq(d2, lastDate), "Dates out of range of schedule: "
                + "date 1: " + d1 + ", date 2: " + d2 + ", first date: "
                + firstDate + ", last date: " + lastDate);

        double yearFractionSum = 0.0;
        for (int i = 0; i < couponDates.size() - 1; i++) {
            Date startReferencePeriod = couponDates.get(i);
            Date endReferencePeriod = couponDates.get(i + 1);
            if (TimeUtils.less(d1, endReferencePeriod) && TimeUtils.greater(d2, startReferencePeriod)) {
                yearFractionSum +=
                        yearFractionWithReferenceDates(this,
                                TimeUtils.max(d1, startReferencePeriod),
                                TimeUtils.min(d2, endReferencePeriod),
                                startReferencePeriod,
                                endReferencePeriod);
            }
        }
        return yearFractionSum;
    }
}
