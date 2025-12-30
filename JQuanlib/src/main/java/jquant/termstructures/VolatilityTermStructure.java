package jquant.termstructures;

import jquant.TermStructure;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Volatility term structure
/*! This abstract class defines the interface of concrete
    volatility structures which will be derived from this one.

*/
public abstract class VolatilityTermStructure extends TermStructure {
    private BusinessDayConvention bdc_;

    /*! \name Constructors
        See the TermStructure documentation for issues regarding
        constructors.
    */
    //@{
    /*! \warning term structures initialized by means of this
                 constructor must manage their own reference date
                 by overriding the referenceDate() method.
    */
    public VolatilityTermStructure(BusinessDayConvention bdc, final DayCounter dc) {
        super(dc);
        bdc_ = bdc;
    }

    //! initialize with a fixed reference date
    public VolatilityTermStructure(final Date referenceDate,
                                   final Calendar cal,
                                   BusinessDayConvention bdc,
                                   final DayCounter dc) {
        super(referenceDate, cal, dc);
        bdc_ = bdc;
    }

    //! calculate the reference date based on the global evaluation date
    public VolatilityTermStructure(int settlementDays,
                                   final Calendar cal,
                                   BusinessDayConvention bdc,
                                   final DayCounter dc) {
        super(settlementDays, cal, dc);
        bdc_ = bdc;
    }

    //@}
    //! the business day convention used in tenor to date conversion
    public BusinessDayConvention businessDayConvention() {
        return bdc_;
    }

    //! period/date conversion
    public Date optionDateFromTenor(final Period p) {
        // swaption style
        return calendar().advance(referenceDate(),
                p,
                businessDayConvention(),
                false);
    }

    //! the minimum strike for which the term structure can return vols
    public abstract double minStrike();

    //! the maximum strike for which the term structure can return vols
    public abstract double maxStrike();

    //! strike-range check
    protected void checkStrike(double k, boolean extrapolate) {
        QL_REQUIRE(extrapolate || allowsExtrapolation() ||
                        (k >= minStrike() && k <= maxStrike()),
                "strike (" + k + ") is outside the curve domain ["
                        + minStrike() + "," + maxStrike() + "]");
    }
}
