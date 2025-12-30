package jquant.termstructures.volatility.equityfx;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.termstructures.VolatilityTermStructure;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Black-volatility term structure
/*! This abstract class defines the interface of concrete
    Black-volatility term structures which will be derived from
    this one.

    Volatilities are assumed to be expressed on an annual basis.
*/
public abstract class BlackVolTermStructure extends VolatilityTermStructure {
    /*! \name Constructors
        See the TermStructure documentation for issues regarding
        constructors.
    */
    //@{
    //! default constructor
    /*! \warning term structures initialized by means of this
                 constructor must manage their own reference date
                 by overriding the referenceDate() method.
    */
    public BlackVolTermStructure(BusinessDayConvention bdc, final DayCounter dc) {
        super(bdc, dc);
    }

    //! initialize with a fixed reference date
    public BlackVolTermStructure(final Date refDate,
                                 final Calendar cal,
                                 BusinessDayConvention bdc,
                                 final DayCounter dc) {
        super(refDate, cal, bdc, dc);
    }

    //! calculate the reference date based on the global evaluation date
    public BlackVolTermStructure(int settlDays,
                                 final Calendar cal,
                                 BusinessDayConvention bdc,
                                 final DayCounter dc) {
        super(settlDays, cal, bdc, dc);
    }

    //@}
    //! \name Black Volatility
    //@{
    //! spot volatility
    public double blackVol(final Date d,
                           double strike,
                           boolean extrapolate) {
        checkRange(d, extrapolate);
        checkStrike(strike, extrapolate);
        double t = timeFromReference(d);
        return blackVolImpl(t, strike);
    }

    //! spot volatility
    public double blackVol(double t,
                           double strike,
                           boolean extrapolate) {
        checkRange(t, extrapolate);
        checkStrike(strike, extrapolate);
        return blackVolImpl(t, strike);
    }

    //! spot variance
    public double blackVariance(final Date d,
                                double strike,
                                boolean extrapolate) {
        checkRange(d, extrapolate);
        checkStrike(strike, extrapolate);
        double t = timeFromReference(d);
        return blackVarianceImpl(t, strike);
    }

    //! spot variance
    public double blackVariance(double t,
                                double strike,
                                boolean extrapolate) {
        checkRange(t, extrapolate);
        checkStrike(strike, extrapolate);
        return blackVarianceImpl(t, strike);
    }

    //! forward (at-the-money) volatility
    public double blackForwardVol(final Date date1,
                                  final Date date2,
                                  double strike,
                                  boolean extrapolate) {
        // (redundant) date-based checks
        QL_REQUIRE(TimeUtils.leq(date1, date2),
                date1 + " later than " + date2);
        checkRange(date2, extrapolate);

        // using the time implementation
        double time1 = timeFromReference(date1);
        double time2 = timeFromReference(date2);
        return blackForwardVol(time1, time2, strike, extrapolate);
    }

    //! forward (at-the-money) volatility
    public double blackForwardVol(double time1,
                                  double time2,
                                  double strike,
                                  boolean extrapolate) {
        QL_REQUIRE(time1 <= time2,
                time1 + " later than " + time2);
        checkRange(time2, extrapolate);
        checkStrike(strike, extrapolate);
        if (time2 == time1) {
            if (time1 == 0.0) {
                double epsilon = 1.0e-5;
                double var = blackVarianceImpl(epsilon, strike);
                return Math.sqrt(var / epsilon);
            } else {
                double epsilon = Math.min(1.0e-5, time1);
                double var1 = blackVarianceImpl(time1 - epsilon, strike);
                double var2 = blackVarianceImpl(time1 + epsilon, strike);
                QL_REQUIRE(var2 >= var1,
                        "variances must be non-decreasing");
                return Math.sqrt((var2 - var1) / (2 * epsilon));
            }
        } else {
            double var1 = blackVarianceImpl(time1, strike);
            double var2 = blackVarianceImpl(time2, strike);
            QL_REQUIRE(var2 >= var1,
                    "variances must be non-decreasing");
            return Math.sqrt((var2 - var1) / (time2 - time1));
        }
    }

    //! forward (at-the-money) variance
    public double blackForwardVariance(final Date date1,
                                       final Date date2,
                                       double strike,
                                       boolean extrapolate) {
        // (redundant) date-based checks
        QL_REQUIRE(TimeUtils.leq(date1, date2),
                date1 + " later than " + date2);
        checkRange(date2, extrapolate);

        // using the time implementation
        double time1 = timeFromReference(date1);
        double time2 = timeFromReference(date2);
        return blackForwardVariance(time1, time2, strike, extrapolate);
    }

    //! forward (at-the-money) variance
    public double blackForwardVariance(double time1,
                                       double time2,
                                       double strike,
                                       boolean extrapolate) {
        QL_REQUIRE(time1 <= time2,
                time1 + " later than " + time2);
        checkRange(time2, extrapolate);
        checkStrike(strike, extrapolate);
        double v1 = blackVarianceImpl(time1, strike);
        double v2 = blackVarianceImpl(time2, strike);
        QL_REQUIRE(v2 >= v1,
                "variances must be non-decreasing");
        return v2 - v1;
    }
    //@}
    //! \name Visitability
    //@{
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<BlackVolTermStructure> vv = (Visitor<BlackVolTermStructure>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    /*! \name Calculations

        These methods must be implemented in derived classes to perform
        the actual volatility calculations. When they are called,
        range check has already been performed; therefore, they must
        assume that extrapolation is required.
    */
    //@{
    //! Black variance calculation
    protected abstract double blackVarianceImpl(double t, double strike);

    //! Black volatility calculation
    protected abstract double blackVolImpl(double t, double strike);
}
