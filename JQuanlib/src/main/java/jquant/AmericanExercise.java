package jquant;

import jquant.math.CommonUtil;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;
//! American exercise
/*! An American option can be exercised at any time between two
    predefined dates; the first date might be omitted, in which
    case the option can be exercised at any time before the expiry.

    \todo check that everywhere the American condition is applied
          from earliestDate and not earlier
*/
public class AmericanExercise extends EarlyExercise {
    public AmericanExercise(final Date earliest,
                            final Date latest,
                            boolean payoffAtExpiry) {
        super(Type.American, payoffAtExpiry);
        QL_REQUIRE(TimeUtils.leq(earliest, latest),
                "earliest > latest exercise date");
        dates_ = CommonUtil.ArrayInit(2);
        dates_.set(0, earliest);
        dates_.set(1, latest);
    }

    public AmericanExercise(final Date latest, boolean payoffAtExpiry) {
        super(Type.American,  payoffAtExpiry);
        dates_ = CommonUtil.ArrayInit(2);
        dates_.set(0, Date.minDate());
        dates_.set(1, latest);
    }
}
