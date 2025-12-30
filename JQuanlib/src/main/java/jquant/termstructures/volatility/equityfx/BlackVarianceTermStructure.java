package jquant.termstructures.volatility.equityfx;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.CommonUtil.QL_FAIL;

//! Black variance term structure
/*! This abstract class acts as an adapter to VolTermStructure allowing
    the programmer to implement only the
    <tt>blackVarianceImpl(Time, Real, bool)</tt> method in derived
    classes.

    Volatility are assumed to be expressed on an annual basis.
*/
public abstract class BlackVarianceTermStructure extends BlackVolTermStructure {
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
    public BlackVarianceTermStructure(
            BusinessDayConvention bdc,
            final DayCounter dc) {
        super(bdc, dc);
    }

    //! initialize with a fixed reference date
    public BlackVarianceTermStructure(
            final Date refDate,
            final Calendar cal,
            BusinessDayConvention bdc,
            final DayCounter dc) {
        super(refDate, cal, bdc, dc);
    }

    //! calculate the reference date based on the global evaluation date
    public BlackVarianceTermStructure(
            int settlementDays,
            final Calendar cal,
            BusinessDayConvention bdc,
            final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
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
                Visitor<BlackVarianceTermStructure> vv = (Visitor<BlackVarianceTermStructure>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    /*! Returns the volatility for the given strike and date calculating it
        from the variance.
    */
    @Override
    protected double blackVolImpl(double t,
                                  double strike) {
        double nonZeroMaturity = (t == 0.0 ? 0.00001 : t);
        double var = blackVarianceImpl(nonZeroMaturity, strike);
        return Math.sqrt(var / nonZeroMaturity);
    }
}
