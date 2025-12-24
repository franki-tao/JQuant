package jquant;

import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Bermudan exercise
/*! A Bermudan option can only be exercised at a set of fixed dates.
 */
public class BermudanExercise extends EarlyExercise {
    public BermudanExercise(final List<Date> dates, boolean payoffAtExpiry) {
        super(Type.Bermudan, payoffAtExpiry);
        QL_REQUIRE(!dates.isEmpty(), "no exercise date given");
        dates_ = dates;
        dates_.sort((o1, o2) -> {
            if (TimeUtils.equals(o1, o2))
                return 0;
            return TimeUtils.greater(o1, o2) ? 1 : -1;
        });
    }
}
