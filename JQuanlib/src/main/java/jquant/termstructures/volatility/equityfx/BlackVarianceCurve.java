package jquant.termstructures.volatility.equityfx;

import jquant.math.CommonUtil;
import jquant.math.Interpolation;
import jquant.math.interpolations.LinearInterpolation;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Black volatility curve modelled as variance curve
/*! This class calculates time-dependent Black volatilities using
    as input a vector of (ATM) Black volatilities observed in the
    market.

    The calculation is performed interpolating on the variance curve.
    Linear interpolation is used as default; this can be changed
    by the setInterpolation() method.

    For strike dependence, see BlackVarianceSurface.

    \todo check time extrapolation

*/
public class BlackVarianceCurve extends BlackVarianceTermStructure {
    private DayCounter dayCounter_;
    private Date maxDate_;
    private List<Double> times_;
    private List<Double> variances_;
    private Interpolation varianceCurve_;

    public BlackVarianceCurve(final Date referenceDate,
                              final List<Date> dates,
                              final List<Double> blackVolCurve,
                              DayCounter dayCounter,
                              boolean forceMonotoneVariance) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, new DayCounter());
        dayCounter_ = dayCounter;
        maxDate_ = dates.get(dates.size() - 1);
        QL_REQUIRE(dates.size() == blackVolCurve.size(),
                "mismatch between date vector and black vol vector");

        // cannot have dates[0]==referenceDate, since the
        // value of the vol at dates[0] would be lost
        // (variance at referenceDate must be zero)
        QL_REQUIRE(TimeUtils.greater(dates.get(0), referenceDate),
                "cannot have dates[0] <= referenceDate");

        variances_ = CommonUtil.ArrayInit(dates.size() + 1);
        times_ = CommonUtil.ArrayInit(dates.size() + 1);
        variances_.set(0, 0.0);
        times_.set(0, 0.0);
        int j;
        for (j = 1; j <= blackVolCurve.size(); j++) {
            times_.set(j, timeFromReference(dates.get(j - 1)));
            QL_REQUIRE(times_.get(j) > times_.get(j - 1),
                    "dates must be sorted unique!");
            variances_.set(j, times_.get(j) * blackVolCurve.get(j - 1) * blackVolCurve.get(j - 1));
            QL_REQUIRE(variances_.get(j) >= variances_.get(j - 1) || !forceMonotoneVariance,
                    "variance must be non-decreasing");
        }

        // default: linear interpolation
        Interpolation i = new LinearInterpolation(CommonUtil.toArray(times_), CommonUtil.toArray(variances_));
        setInterpolation(i);
    }

    //! \name TermStructure interface
    //@{
    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }
    @Override
    public Date maxDate() {
        return maxDate_;
    }

    //@}
    //! \name VolatilityTermStructure interface
    //@{
    @Override
    public double minStrike() {
        return Double.MIN_VALUE;
    }
    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    //@}
    //! \name Modifiers
    //@{
    public void setInterpolation(final Interpolation i) {
        varianceCurve_ = i;
        varianceCurve_.update();
        notifyObservers();
    }

    //@}
    //! \name Visitability
    //@{
    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<BlackVarianceCurve> vv = (Visitor<BlackVarianceCurve>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    //@}
    @Override
    protected double blackVarianceImpl(double t, double x) {
        if (t <= times_.get(times_.size() - 1)) {
            return varianceCurve_.value(t, true);
        } else {
            // extrapolate with flat vol
            return varianceCurve_.value(times_.get(times_.size() - 1), true) *
                    t / times_.get(times_.size() - 1);
        }
    }
}
