package jquant.termstructures.volatility.equityfx;

import jquant.math.Matrix;
import jquant.math.interpolations.Interpolation2D;
import jquant.math.interpolations.Interpolator2D;
import jquant.math.interpolations.impl.Bilinear;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Black volatility surface modelled as a variance surface.
 *
 * <p>Port of
 * {@code ql/termstructures/volatility/equityfx/blackvariancesurface.{hpp,cpp}}.
 * The input is a matrix of Black volatilities observed in the market; the
 * calculation interpolates on the variance surface. Bilinear interpolation is
 * used by default and can be changed with {@link #setInterpolation}.
 */
public class BlackVarianceSurface extends BlackVarianceTermStructure {
    public enum Extrapolation {
        ConstantExtrapolation,
        InterpolatorDefaultExtrapolation
    }

    private final DayCounter dayCounter_;
    private final Date maxDate_;
    private final double[] strikes_;
    private final double[] times_;
    private final double[][] variances_;
    private final Extrapolation lowerExtrapolation_;
    private final Extrapolation upperExtrapolation_;
    private Interpolation2D varianceSurface_;

    public BlackVarianceSurface(final Date referenceDate,
                                final Calendar cal,
                                final List<Date> dates,
                                final List<Double> strikes,
                                final Matrix blackVolMatrix,
                                DayCounter dayCounter) {
        this(referenceDate, cal, dates, strikes, blackVolMatrix, dayCounter,
                Extrapolation.InterpolatorDefaultExtrapolation,
                Extrapolation.InterpolatorDefaultExtrapolation);
    }

    public BlackVarianceSurface(final Date referenceDate,
                                final Calendar cal,
                                final List<Date> dates,
                                final List<Double> strikes,
                                final Matrix blackVolMatrix,
                                DayCounter dayCounter,
                                Extrapolation lowerExtrapolation,
                                Extrapolation upperExtrapolation) {
        super(referenceDate, cal, BusinessDayConvention.FOLLOWING, new DayCounter());

        dayCounter_ = dayCounter;
        maxDate_ = dates.get(dates.size() - 1);
        strikes_ = new double[strikes.size()];
        for (int i = 0; i < strikes.size(); i++) {
            strikes_[i] = strikes.get(i);
        }
        lowerExtrapolation_ = lowerExtrapolation;
        upperExtrapolation_ = upperExtrapolation;

        QL_REQUIRE(dates.size() == blackVolMatrix.cols(),
                "mismatch between date vector and vol matrix colums");
        QL_REQUIRE(strikes_.length == blackVolMatrix.rows(),
                "mismatch between money-strike vector and vol matrix rows");
        QL_REQUIRE(dates.get(0).serialNumber() >= referenceDate.serialNumber(),
                "cannot have dates[0] < referenceDate");

        times_ = new double[dates.size() + 1];
        times_[0] = 0.0;
        variances_ = new double[strikes_.length][dates.size() + 1];
        for (int i = 0; i < blackVolMatrix.rows(); i++) {
            variances_[i][0] = 0.0;
        }
        for (int j = 1; j <= blackVolMatrix.cols(); j++) {
            times_[j] = timeFromReference(dates.get(j - 1));
            QL_REQUIRE(times_[j] > times_[j - 1],
                    "dates must be sorted unique!");
            for (int i = 0; i < blackVolMatrix.rows(); i++) {
                variances_[i][j] = times_[j]
                        * blackVolMatrix.get(i, j - 1) * blackVolMatrix.get(i, j - 1);
            }
        }

        // default: bilinear interpolation
        setInterpolation(new Bilinear());
    }

    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    @Override
    public Date maxDate() {
        return maxDate_;
    }

    @Override
    public double minStrike() {
        return strikes_[0];
    }

    @Override
    public double maxStrike() {
        return strikes_[strikes_.length - 1];
    }

    public void setInterpolation() {
        setInterpolation(new Bilinear());
    }

    public void setInterpolation(final Interpolator2D i) {
        varianceSurface_ = i.interpolate(times_, strikes_, variances_);
        notifyObservers();
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<BlackVarianceSurface> vv = (Visitor<BlackVarianceSurface>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    @Override
    protected double blackVarianceImpl(double t, double strike) {
        if (t == 0.0) {
            return 0.0;
        }

        // enforce constant extrapolation when required
        if (strike < strikes_[0]
                && lowerExtrapolation_ == Extrapolation.ConstantExtrapolation) {
            strike = strikes_[0];
        }
        if (strike > strikes_[strikes_.length - 1]
                && upperExtrapolation_ == Extrapolation.ConstantExtrapolation) {
            strike = strikes_[strikes_.length - 1];
        }

        if (t <= times_[times_.length - 1]) {
            return varianceSurface_.value(t, strike, true);
        } else { // t > times_.back() || extrapolate
            return varianceSurface_.value(times_[times_.length - 1], strike, true)
                    * t / times_[times_.length - 1];
        }
    }
}
