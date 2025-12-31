package jquant.termstructures.volatility.equityfx;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.termstructures.VolatilityTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.CommonUtil.QL_FAIL;

/*! This abstract class defines the interface of concrete
    local-volatility term structures which will be derived from this one.

    Volatilities are assumed to be expressed on an annual basis.
*/
public abstract class LocalVolTermStructure extends VolatilityTermStructure {
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
    public LocalVolTermStructure(BusinessDayConvention bdc,
                                 final DayCounter dc) {
        super(bdc, dc);
    }

    //! initialize with a fixed reference date
    public LocalVolTermStructure(final Date referenceDate,
                                 final Calendar cal,
                                 BusinessDayConvention bdc,
                                 final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    //! calculate the reference date based on the global evaluation date
    public LocalVolTermStructure(int settlementDays,
                                 final Calendar cal,
                                 BusinessDayConvention bdc,
                                 final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }
    //@}
    //! \name Local Volatility
    //@{
    public double localVol(final Date d,
                           double underlyingLevel,
                           boolean extrapolate) {
        checkRange(d, extrapolate);
        checkStrike(underlyingLevel, extrapolate);
        double t = timeFromReference(d);
        return localVolImpl(t, underlyingLevel);
    }

    public double localVol(double t,
                           double underlyingLevel,
                           boolean extrapolate) {
        checkRange(t, extrapolate);
        checkStrike(underlyingLevel, extrapolate);
        return localVolImpl(t, underlyingLevel);
    }

    //@}
    //! \name Visitability
    //@{
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<LocalVolTermStructure> vv = (Visitor<LocalVolTermStructure>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }
    //@}


    /*! \name Calculations

        These methods must be implemented in derived classes to perform
        the actual volatility calculations. When they are called,
        range check has already been performed; therefore, they must
        assume that extrapolation is required.
    */
    //@{
    //! local vol calculation
    protected abstract double localVolImpl(double t, double strike);
    //@}
}
