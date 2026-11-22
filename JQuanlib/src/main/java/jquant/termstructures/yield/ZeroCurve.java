package jquant.termstructures.yield;

import jquant.math.interpolations.LinearInterpolation;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * Yield term structure based on linear interpolation of zero rates.
 *
 * <p>Port of {@code InterpolatedZeroCurve<Linear>} and its {@code ZeroCurve}
 * typedef from {@code ql/termstructures/yield/zerocurve.hpp}. The reference
 * date is the first input date, as in QuantLib. Past the last knot the
 * zero yield is extrapolated with a flat instantaneous forward rate.
 */
public class ZeroCurve extends ZeroYieldStructure {
    private final List<Date> dates_;
    private final double[] times_;
    private final double[] data_;
    private final LinearInterpolation interpolation_;

    public ZeroCurve(List<Date> dates,
                     List<Double> yields,
                     DayCounter dayCounter) {
        this(dates, toArray(yields), dayCounter);
    }

    public ZeroCurve(List<Date> dates,
                     double[] yields,
                     DayCounter dayCounter) {
        this(dates, yields, dayCounter, new NullCalendar());
    }

    /** Fixed reference date with an explicit calendar, as in the official
     *  {@code ZeroCurve(dates, rates, dayCounter, calendar)} overload. */
    public ZeroCurve(List<Date> dates,
                     double[] yields,
                     DayCounter dayCounter,
                     Calendar calendar) {
        super(dates.get(0), calendar, dayCounter,
                new ArrayList<>(), new ArrayList<>());

        // initialize(): compounding defaults to Continuous, so the input
        // rates are used as they are.
        QL_REQUIRE(dates.size() >= 2, "not enough input dates given");
        QL_REQUIRE(yields.length == dates.size(), "dates/data count mismatch");

        dates_ = new ArrayList<>(dates);
        times_ = new double[dates_.size()];
        data_ = yields.clone();

        times_[0] = dayCounter.yearFraction(dates_.get(0), dates_.get(0),
                new Date(), new Date());
        for (int i = 1; i < dates_.size(); i++) {
            QL_REQUIRE(TimeUtils.greater(dates_.get(i), dates_.get(i - 1)),
                    "dates not sorted: " + dates_.get(i) + " passed after "
                            + dates_.get(i - 1));
            times_[i] = dayCounter.yearFraction(dates_.get(0), dates_.get(i),
                    new Date(), new Date());
            QL_REQUIRE(!close(times_[i], times_[i - 1]),
                    "two passed dates (" + dates_.get(i - 1) + " and "
                            + dates_.get(i) + ") correspond to the same time "
                            + "under this curve's day count convention");
        }

        interpolation_ = new LinearInterpolation(times_, data_);
    }

    @Override
    public Date maxDate() {
        return dates_.get(dates_.size() - 1);
    }

    public List<Date> dates() {
        return dates_;
    }

    public double[] times() {
        return times_.clone();
    }

    public double[] data() {
        return data_.clone();
    }

    public double[] zeroRates() {
        return data_.clone();
    }

    @Override
    protected double zeroYieldImpl(double t) {
        if (t <= times_[times_.length - 1]) {
            return interpolation_.value(t, true);
        }

        // flat fwd extrapolation
        final double tMax = times_[times_.length - 1];
        final double zMax = data_[data_.length - 1];
        final double instFwdMax = zMax + tMax * interpolation_.derivative(tMax, false);
        return (zMax * tMax + instFwdMax * (t - tMax)) / t;
    }

    private static double[] toArray(List<Double> values) {
        final double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            result[i] = values.get(i);
        }
        return result;
    }
}
