package jquant.termstructures.yield;

import jquant.math.Interpolation;
import jquant.math.interpolations.LogLinearInterpolation;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Yield term structure based on log-linear interpolation of discount
 * factors.
 *
 * <p>Port of QuantLib's {@code InterpolatedDiscountCurve<LogLinear>} and its
 * {@code DiscountCurve} typedef in
 * {@code ql/termstructures/yield/discountcurve.hpp}. Only the
 * dates/discount-factors/day-counter constructor used by the overnight-coupon
 * tests is provided. Past the last knot the discount is extrapolated with a
 * flat instantaneous forward rate when extrapolation is allowed.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/discountcurve.hpp
 */
public class DiscountCurve extends YieldTermStructure {
    private final List<Date> dates_;
    private final double[] times_;
    private final double[] data_;
    private final Interpolation interpolation_;

    public DiscountCurve(List<Date> dates,
                         List<Double> discounts,
                         DayCounter dayCounter) {
        this(dates, toArray(discounts), dayCounter);
    }

    public DiscountCurve(List<Date> dates,
                         double[] discounts,
                         DayCounter dayCounter) {
        super(dates.get(0), new NullCalendar(), dayCounter,
                new ArrayList<>(), new ArrayList<>());

        QL_REQUIRE(dates.size() >= 2, "not enough input dates given");
        QL_REQUIRE(discounts.length == dates.size(),
                "dates/data count mismatch");
        QL_REQUIRE(discounts[0] == 1.0,
                "the first discount must be == 1.0 "
                        + "to flag the corresponding date as reference date");
        for (int i = 1; i < dates.size(); ++i)
            QL_REQUIRE(discounts[i] > 0.0, "negative discount");

        dates_ = new ArrayList<>(dates);
        data_ = discounts.clone();
        times_ = new double[dates_.size()];
        times_[0] = dayCounter.yearFraction(dates_.get(0), dates_.get(0),
                new Date(), new Date());
        for (int i = 1; i < dates_.size(); ++i) {
            QL_REQUIRE(TimeUtils.greater(dates_.get(i), dates_.get(i - 1)),
                    "dates not sorted: " + dates_.get(i) + " passed after "
                            + dates_.get(i - 1));
            times_[i] = dayCounter.yearFraction(dates_.get(0),
                    dates_.get(i), new Date(), new Date());
        }

        interpolation_ = new LogLinearInterpolation(times_, data_);
    }

    @Override
    public Date maxDate() {
        return dates_.get(dates_.size() - 1);
    }

    @Override
    protected double discountImpl(double t) {
        final double tMax = times_[times_.length - 1];
        if (t <= tMax)
            return interpolation_.value(t, true);

        // Flat forward extrapolation past the last knot.
        final double dMax = data_[data_.length - 1];
        final double instFwdMax = -interpolation_.derivative(tMax, true) / dMax;
        return dMax * Math.exp(-instFwdMax * (t - tMax));
    }

    private static double[] toArray(List<Double> values) {
        final double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i)
            result[i] = values.get(i);
        return result;
    }
}
