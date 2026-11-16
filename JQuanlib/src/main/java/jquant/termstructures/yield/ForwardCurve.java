package jquant.termstructures.yield;

import jquant.math.interpolations.BackwardFlatInterpolation;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Yield term structure based on a backward-flat interpolation of
 * instantaneous forward rates.
 *
 * <p>Port of the {@code InterpolatedForwardCurve<BackwardFlat>} typedef
 * {@code ForwardCurve} from
 * {@code ql/termstructures/yield/forwardcurve.hpp}. The reference date is the
 * first date, as in QuantLib.
 */
public class ForwardCurve extends ZeroYieldStructure {
    private final List<Date> dates_;
    private final double[] times_;
    private final double[] forwards_;
    private final BackwardFlatInterpolation interpolation_;

    public ForwardCurve(List<Date> dates,
                        List<Double> forwards,
                        DayCounter dayCounter) {
        this(dates, toArray(forwards), dayCounter);
    }

    public ForwardCurve(List<Date> dates,
                        double[] forwards,
                        DayCounter dayCounter) {
        super(dates.get(0), new NullCalendar(), dayCounter,
                new ArrayList<>(), new ArrayList<>());

        QL_REQUIRE(dates.size() >= 1, "not enough input dates given");
        QL_REQUIRE(forwards.length == dates.size(), "dates/data count mismatch");

        dates_ = new ArrayList<>(dates);
        times_ = new double[dates_.size()];
        forwards_ = forwards.clone();

        for (int i = 0; i < dates_.size(); ++i) {
            times_[i] = dayCounter.yearFraction(
                    dates_.get(0), dates_.get(i), new Date(), new Date());
            if (i > 0) {
                QL_REQUIRE(TimeUtils.greater(
                                dates_.get(i), dates_.get(i - 1)),
                        "dates not sorted: " + dates_.get(i)
                                + " passed after " + dates_.get(i - 1));
            }
        }

        interpolation_ = new BackwardFlatInterpolation(times_, forwards_);
    }

    @Override
    public Date maxDate() {
        return dates_.get(dates_.size() - 1);
    }

    public List<Date> dates() {
        return dates_;
    }

    public List<Double> forwards() {
        final List<Double> result = new ArrayList<>(forwards_.length);
        for (double forward : forwards_) {
            result.add(forward);
        }
        return result;
    }

    @Override
    protected double zeroYieldImpl(double t) {
        if (t == 0.0) {
            return interpolation_.value(t, true);
        }

        final double maxTime = times_[times_.length - 1];
        final double integral;
        if (t <= maxTime) {
            integral = interpolation_.primitive(t, true);
        } else {
            // flat forward extrapolation
            integral = interpolation_.primitive(maxTime, true)
                    + forwards_[forwards_.length - 1] * (t - maxTime);
        }
        return integral / t;
    }

    private static double[] toArray(List<Double> values) {
        final double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }
}
