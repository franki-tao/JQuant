package jquant.termstructures.yield;

import jquant.Handle;
import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Interpolator;
import jquant.math.interpolations.impl.LogLinear;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

/**
 * Yield curve based on interpolation of discount factors applied as a
 * multiplicative spread to a base {@link YieldTermStructure}.
 *
 * <p>The discount factors spread at any given date is interpolated between the
 * input data. The structure remains linked to the original curve, so changes
 * in the latter are reflected here as well.
 *
 * <p>{@code discountImpl(t) = originalCurve.discount(t) * calcSpread(t)}, where
 * {@code calcSpread} interpolates the spread discount factors and, past the
 * last node, extrapolates them with a flat instantaneous forward.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/spreaddiscountcurve.hpp
 */
public class InterpolatedSpreadDiscountCurve extends RelativeDerivedYieldTermStructure {
    private final Interpolator interpolator_;
    private List<Double> times_;
    private List<Date> dates_;
    private List<Double> data_;
    private Interpolation interpolation_;
    private DayCounter prevDayCount_;

    public InterpolatedSpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                                           List<Date> dates,
                                           double[] dfs) {
        this(baseCurve, dates, dfs, new LogLinear());
    }

    public InterpolatedSpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                                           List<Date> dates,
                                           List<Double> dfs) {
        this(baseCurve, dates, dfs, new LogLinear());
    }

    public InterpolatedSpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                                           List<Date> dates,
                                           double[] dfs,
                                           Interpolator interpolator) {
        this(baseCurve, dates, toList(dfs), interpolator);
    }

    public InterpolatedSpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                                           List<Date> dates,
                                           List<Double> dfs,
                                           Interpolator interpolator) {
        super(baseCurve);
        QL_REQUIRE(interpolator != null, "null interpolator");
        QL_REQUIRE(dates != null && dfs != null, "null dates or discounts");
        interpolator_ = interpolator;
        dates_ = new ArrayList<>(dates);
        data_ = new ArrayList<>(dfs);
        QL_REQUIRE(dates_.size() >= interpolator_.getRequiredPoints(),
                "not enough input dates given");
        QL_REQUIRE(data_.size() == dates_.size(), "dates/data count mismatch");
        QL_REQUIRE(data_.get(0) == 1.0,
                "the first discount must be == 1.0 "
                        + "to flag the corresponding date as reference date");
        for (int i = 1; i < dates_.size(); ++i)
            QL_REQUIRE(data_.get(i) > 0.0, "negative discount");

        times_ = CommonUtil.ArrayInit(dates_.size(), 0.0);
        if (!originalCurve_.empty())
            updateInterpolation();
    }

    /**
     * Bootstrap constructor: the dates, times and values are filled in by the
     * bootstrapper. Mirrors the protected QuantLib constructor.
     */
    protected InterpolatedSpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                                              Interpolator interpolator) {
        super(baseCurve);
        QL_REQUIRE(interpolator != null, "null interpolator");
        interpolator_ = interpolator;
        times_ = new ArrayList<>();
        dates_ = new ArrayList<>();
        data_ = new ArrayList<>();
    }

    @Override
    public Date maxDate() {
        Date curveMax = dates_.isEmpty() ? new Date() : dates_.get(dates_.size() - 1);
        QL_REQUIRE(TimeUtils.neq(curveMax, new Date()), "curve has no dates yet");
        return TimeUtils.min(originalCurve_.currentLink().maxDate(), curveMax);
    }

    public Handle<YieldTermStructure> baseCurve() {
        return originalCurve_;
    }

    public List<Double> times() {
        return List.copyOf(times_);
    }

    public List<Date> dates() {
        return List.copyOf(dates_);
    }

    public List<Double> data() {
        return List.copyOf(data_);
    }

    /** Date/value pairs, as the official {@code InterpolatedNodes::nodes()}. */
    public List<Map.Entry<Date, Double>> nodes() {
        List<Map.Entry<Date, Double>> nodes = new ArrayList<>(dates_.size());
        for (int i = 0; i < dates_.size(); ++i)
            nodes.add(Map.entry(dates_.get(i), data_.get(i)));
        return nodes;
    }

    @Override
    protected double discountImpl(double t) {
        return originalCurve_.currentLink().discount(t, false) * calcSpread(t);
    }

    private double calcSpread(double t) {
        double tMax = times_.get(times_.size() - 1);
        if (t <= tMax)
            return interpolation_.value(t, true);

        // flat forward extrapolation of the spread
        double dMax = data_.get(data_.size() - 1);
        double instFwdMax = -interpolation_.derivative(tMax, true) / dMax;
        return dMax * Math.exp(-instFwdMax * (t - tMax));
    }

    @Override
    public void update() {
        if (!originalCurve_.empty() && !dates_.isEmpty())
            updateInterpolation();
        super.update();
    }

    private void updateInterpolation() {
        QL_REQUIRE(TimeUtils.equals(dates_.get(0), referenceDate()),
                "the first date should be the same as in the original curve");
        // Since dates_ are fixed and dates_[0] must be equal to
        // referenceDate(), the only thing that can change is dayCounter().
        DayCounter dc = dayCounter();
        if (prevDayCount_ == null || !TimeUtils.equals(prevDayCount_, dc)) {
            setupTimes(dc);
            interpolation_ = interpolator_.interpolate(
                    CommonUtil.toArray(times_), CommonUtil.toArray(data_));
            prevDayCount_ = dc;
        }
    }

    private void setupTimes(DayCounter dayCounter) {
        Date referenceDate = dates_.get(0);
        times_ = CommonUtil.ArrayInit(dates_.size(), 0.0);
        times_.set(0, dayCounter.yearFraction(referenceDate, dates_.get(0),
                new Date(), new Date()));
        for (int i = 1; i < dates_.size(); i++) {
            QL_REQUIRE(TimeUtils.greater(dates_.get(i), dates_.get(i - 1)),
                    "dates not sorted: " + dates_.get(i) + " passed after "
                            + dates_.get(i - 1));
            times_.set(i, dayCounter.yearFraction(referenceDate, dates_.get(i),
                    new Date(), new Date()));
            QL_REQUIRE(!close(times_.get(i), times_.get(i - 1)),
                    "two passed dates (" + dates_.get(i - 1) + " and "
                            + dates_.get(i) + ") correspond to the same time "
                            + "under this curve's day count convention ("
                            + dayCounter + ")");
        }
    }

    private static List<Double> toList(double[] values) {
        QL_REQUIRE(values != null, "null discounts");
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values)
            result.add(value);
        return result;
    }
}
