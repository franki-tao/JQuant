package jquant.termstructures;

import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Interpolator;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

public abstract class InterpolatedCurve {
    protected List<Double> times_;
    protected List<Double> data_;
    protected Interpolation interpolation_;
    protected Interpolator interpolator_;
    // Usually, the maximum date is the one corresponding to the
    // last node. However, it might happen that a bit of
    // extrapolation is used by construction; for instance, when a
    // curve is bootstrapped and the last relevant date for an
    // instrument is after the corresponding pillar.
    // We provide here a slot to store this information, so that
    // it's available to all derived classes (we should have
    // probably done the same with the dates_ vector, but moving
    // it here might not be entirely backwards-compatible).
    protected Date maxDate_;
    protected InterpolatedCurve(List<Double> times,
                                List<Double> data,
                                final Interpolator i) {
        times_ = times;
        data_ = data;
        interpolator_ = i;
    }
    protected InterpolatedCurve(List<Double> times,
                                final Interpolator i){
        times_ = times;
        data_ = CommonUtil.ArrayInit(times.size());
        interpolator_ = i;
    }
    protected InterpolatedCurve(int n, final Interpolator i){
        times_ = CommonUtil.ArrayInit(n);
        data_ = CommonUtil.ArrayInit(n);
        interpolator_ = i;
    }
    protected InterpolatedCurve(final Interpolator i){
        times_ = new ArrayList<>();
        data_ = new ArrayList<>();
        interpolator_ = i;
    }
    protected InterpolatedCurve(final InterpolatedCurve c){
        times_ = c.times_;
        data_ = c.data_;
        interpolator_ = c.interpolator_;
        setupInterpolation();
    }
    protected void setupInterpolation() {
        interpolation_ = interpolator_.interpolate(CommonUtil.toArray(times_),
                CommonUtil.toArray(data_));
    }
    protected void setupTimes(final List<Date> dates,
                              Date referenceDate,
                              final DayCounter dayCounter) {
        times_ = CommonUtil.ArrayInit(dates.size());
        times_.set(0, dayCounter.yearFraction(referenceDate, dates.get(0), new Date(), new Date()));
        for (int i = 1; i < dates.size(); i++) {
            QL_REQUIRE(TimeUtils.greater(dates.get(i), dates.get(i-1)),
                    "dates not sorted: " + dates.get(i) + " passed after " + dates.get(i-1));

            times_.set(i, dayCounter.yearFraction(referenceDate, dates.get(i), new Date(), new Date()));
            QL_REQUIRE(!close(this.times_.get(i), this.times_.get(i-1)),
            "two passed dates (" + dates.get(i-1) + " and " + dates.get(i)
                    + ") correspond to the same time "
                    + "under this curve's day count convention (" + dayCounter + ")");
        }
    }
}
