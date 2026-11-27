package jquant.termstructures.volatility.equityfx;

import jquant.math.Array;
import jquant.math.Interpolation;
import jquant.math.Matrix;
import jquant.math.interpolations.LinearInterpolation;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.Interpolator;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

/**
 * Local volatility surface based on fixed values plus interpolation.
 *
 * <p>Port of
 * {@code ql/termstructures/volatility/equityfx/fixedlocalvolsurface.{hpp,cpp}}.
 */
public class FixedLocalVolSurface extends LocalVolTermStructure {

    public enum Extrapolation {
        ConstantExtrapolation,
        InterpolatorDefaultExtrapolation
    }

    private final Date maxDate_;
    private List<Double> times_;
    private final Matrix localVolMatrix_;
    private final List<List<Double>> strikes_;

    private List<Interpolation> localVolInterpol_;
    private final Extrapolation lowerExtrapolation_;
    private final Extrapolation upperExtrapolation_;

    public FixedLocalVolSurface(final Date referenceDate,
                                final List<Date> dates,
                                final List<Double> strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter) {
        this(referenceDate, dates, strikes, localVolMatrix, dayCounter,
                Extrapolation.ConstantExtrapolation,
                Extrapolation.ConstantExtrapolation);
    }

    public FixedLocalVolSurface(final Date referenceDate,
                                final List<Date> dates,
                                final List<Double> strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                Extrapolation lowerExtrapolation,
                                Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, dayCounter);
        maxDate_ = dates.get(dates.size() - 1);
        localVolMatrix_ = localVolMatrix;
        strikes_ = new ArrayList<>(dates.size());
        for (int i = 0; i < dates.size(); ++i)
            strikes_.add(new ArrayList<>(strikes));
        localVolInterpol_ = new ArrayList<>(dates.size());
        lowerExtrapolation_ = lowerExtrapolation;
        upperExtrapolation_ = upperExtrapolation;

        QL_REQUIRE(!TimeUtils.less(dates.get(0), referenceDate),
                "cannot have dates[0] < referenceDate");

        times_ = new ArrayList<>(dates.size());
        for (int j = 0; j < dates.size(); ++j)
            times_.add(timeFromReference(dates.get(j)));

        checkSurface();
        setInterpolation(new Linear());
    }

    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final List<Double> strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter) {
        this(referenceDate, times, strikes, localVolMatrix, dayCounter,
                Extrapolation.ConstantExtrapolation,
                Extrapolation.ConstantExtrapolation);
    }

    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final List<Double> strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                Extrapolation lowerExtrapolation,
                                Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, dayCounter);
        maxDate_ = TimeUtils.yearFractionToDate(dayCounter, referenceDate,
                times[times.length - 1]);
        times_ = new ArrayList<>(times.length);
        for (double time : times)
            times_.add(time);
        localVolMatrix_ = localVolMatrix;
        strikes_ = new ArrayList<>(times.length);
        for (int i = 0; i < times.length; ++i)
            strikes_.add(new ArrayList<>(strikes));
        localVolInterpol_ = new ArrayList<>(times.length);
        lowerExtrapolation_ = lowerExtrapolation;
        upperExtrapolation_ = upperExtrapolation;

        QL_REQUIRE(times_.get(0) >= 0, "cannot have times[0] < 0");

        checkSurface();
        setInterpolation(new Linear());
    }

    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final double[][] strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter) {
        this(referenceDate, times, strikes, localVolMatrix, dayCounter,
                Extrapolation.ConstantExtrapolation,
                Extrapolation.ConstantExtrapolation);
    }

    public FixedLocalVolSurface(final Date referenceDate,
                                final double[] times,
                                final double[][] strikes,
                                final Matrix localVolMatrix,
                                final DayCounter dayCounter,
                                Extrapolation lowerExtrapolation,
                                Extrapolation upperExtrapolation) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, dayCounter);
        maxDate_ = TimeUtils.yearFractionToDate(dayCounter, referenceDate,
                times[times.length - 1]);
        times_ = new ArrayList<>(times.length);
        for (double time : times)
            times_.add(time);
        localVolMatrix_ = localVolMatrix;
        strikes_ = new ArrayList<>(strikes.length);
        for (double[] s : strikes) {
            final List<Double> strike = new ArrayList<>(s.length);
            for (double v : s)
                strike.add(v);
            strikes_.add(strike);
        }
        localVolInterpol_ = new ArrayList<>(times.length);
        lowerExtrapolation_ = lowerExtrapolation;
        upperExtrapolation_ = upperExtrapolation;

        QL_REQUIRE(times_.get(0) >= 0, "cannot have times[0] < 0");
        QL_REQUIRE(times.length == strikes.length,
                "need strikes for every time step");
        checkSurface();
        setInterpolation(new Linear());
    }

    /**
     * Java adaptation of the official shared {@code vector<Real>} strike
     * slices: the official {@code HestonSLVMCModel} fills the strike slices
     * in place while calibrating. Java copies the arrays on construction, so
     * the model has to push the updated slice before rebuilding the
     * interpolation.
     */
    public void setStrikes(int timeIndex, List<Double> strikes) {
        QL_REQUIRE(strikes.size() == localVolMatrix_.rows(),
                "mismatch between money-strike vector and vol matrix rows");
        strikes_.set(timeIndex, new ArrayList<>(strikes));
    }

    public final void setInterpolation() {
        setInterpolation(new Linear());
    }

    public void setInterpolation(final Interpolator interpolator) {
        for (int j = 0; j < times_.size(); ++j) {
            final List<Double> strike = strikes_.get(j);
            final double[] x = new double[strike.size()];
            for (int i = 0; i < strike.size(); ++i)
                x[i] = strike.get(i);
            final Array column = localVolMatrix_.getColArray(j);
            while (localVolInterpol_.size() <= j)
                localVolInterpol_.add(null);
            localVolInterpol_.set(j, interpolator.interpolate(x, column.toArray()));
        }
        notifyObservers();
    }

    private void checkSurface() {
        QL_REQUIRE(times_.size() == localVolMatrix_.cols(),
                "mismatch between date vector and vol matrix colums");
        for (List<Double> strike : strikes_) {
            QL_REQUIRE(strike.size() == localVolMatrix_.rows(),
                    "mismatch between money-strike vector and "
                            + "vol matrix rows");
        }

        for (int j = 1; j < times_.size(); ++j) {
            QL_REQUIRE(times_.get(j) > times_.get(j - 1),
                    "dates must be sorted unique!");
        }

        for (List<Double> strike : strikes_)
            for (int j = 1; j < strike.size(); ++j) {
                QL_REQUIRE(strike.get(j) >= strike.get(j - 1),
                        "strikes must be sorted");
            }
    }

    @Override
    public Date maxDate() {
        return maxDate_;
    }

    @Override
    public double maxTime() {
        return times_.get(times_.size() - 1);
    }

    @Override
    public double minStrike() {
        return strikes_.get(strikes_.size() - 1).get(0);
    }

    @Override
    public double maxStrike() {
        final List<Double> last = strikes_.get(strikes_.size() - 1);
        return last.get(last.size() - 1);
    }

    @Override
    protected double localVolImpl(double t, double strike) {
        t = Math.min(times_.get(times_.size() - 1),
                Math.max(t, times_.get(0)));

        final int idx = lowerBound(times_, t);

        if (close_enough(t, times_.get(idx))) {
            final List<Double> s = strikes_.get(idx);
            if (s.get(0) < s.get(s.size() - 1))
                return localVolInterpol_.get(idx).value(strike, true);
            else
                return localVolMatrix_.get(localVolMatrix_.rows() / 2, idx);
        } else {
            double earlierStrike = strike, laterStrike = strike;
            final List<Double> sPrev = strikes_.get(idx - 1);
            final List<Double> sNext = strikes_.get(idx);

            if (lowerExtrapolation_ == Extrapolation.ConstantExtrapolation) {
                if (strike < sPrev.get(0))
                    earlierStrike = sPrev.get(0);
                if (strike < sNext.get(0))
                    laterStrike = sNext.get(0);
            }

            if (upperExtrapolation_ == Extrapolation.ConstantExtrapolation) {
                if (strike > sPrev.get(sPrev.size() - 1))
                    earlierStrike = sPrev.get(sPrev.size() - 1);
                if (strike > sNext.get(sNext.size() - 1))
                    laterStrike = sNext.get(sNext.size() - 1);
            }

            final double earlyVol =
                    (sPrev.get(0) < sPrev.get(sPrev.size() - 1))
                            ? localVolInterpol_.get(idx - 1).value(earlierStrike, true)
                            : localVolMatrix_.get(localVolMatrix_.rows() / 2, idx - 1);
            final double laterVol = localVolInterpol_.get(idx).value(laterStrike, true);

            return earlyVol
                    + (laterVol - earlyVol) / (times_.get(idx) - times_.get(idx - 1))
                    * (t - times_.get(idx - 1));
        }
    }

    private static int lowerBound(List<Double> sorted, double t) {
        int lo = 0, hi = sorted.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (sorted.get(mid) < t)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }
}
