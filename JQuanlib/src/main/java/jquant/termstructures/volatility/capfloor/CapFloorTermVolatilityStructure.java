package jquant.termstructures.volatility.capfloor;

import jquant.termstructures.VolatilityTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;

/**
 * Cap/floor term-volatility structure.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/capfloor/capfloortermvolatilitystructure.{hpp,cpp}}.
 */
public abstract class CapFloorTermVolatilityStructure extends VolatilityTermStructure {

    /**
     * Warning: term structures initialized by means of this constructor must
     * manage their own reference date by overriding {@link #referenceDate()}.
     */
    public CapFloorTermVolatilityStructure(BusinessDayConvention bdc,
                                           DayCounter dc) {
        super(bdc, dc);
    }

    /** Initialize with a fixed reference date. */
    public CapFloorTermVolatilityStructure(Date referenceDate,
                                           Calendar cal,
                                           BusinessDayConvention bdc,
                                           DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    /** Calculate the reference date based on the global evaluation date. */
    public CapFloorTermVolatilityStructure(int settlementDays,
                                           Calendar cal,
                                           BusinessDayConvention bdc,
                                           DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    /** Returns the volatility for a given cap/floor length and strike rate. */
    public double volatility(Period length, double strike, boolean extrapolate) {
        Date d = optionDateFromTenor(length);
        return volatility(d, strike, extrapolate);
    }

    public double volatility(Date end, double strike, boolean extrapolate) {
        checkRange(end, extrapolate);
        double t = timeFromReference(end);
        return volatility(t, strike, extrapolate);
    }

    /** Returns the volatility for a given end time and strike rate. */
    public double volatility(double t, double strike, boolean extrapolate) {
        checkRange(t, extrapolate);
        checkStrike(strike, extrapolate);
        return volatilityImpl(t, strike);
    }

    /** Implements the actual volatility calculation in derived classes. */
    protected abstract double volatilityImpl(double length, double strike);
}
