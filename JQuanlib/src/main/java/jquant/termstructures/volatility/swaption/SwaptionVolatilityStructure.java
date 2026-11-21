package jquant.termstructures.volatility.swaption;

import jquant.termstructures.VolatilityTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.time.*;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Swaption-volatility structure.
 *
 * <p>Minimal port of QuantLib
 * {@code ql/termstructures/volatility/swaption/swaptionvolstructure.hpp}:
 * only the volatility, variance, shift, swap-length and tenor-limit methods
 * used by the swaption engines and by {@code Swaption::impliedVolatility}.
 * Smile sections are deliberately not ported (the official swaption suite
 * does not use them).
 */
public abstract class SwaptionVolatilityStructure extends VolatilityTermStructure {
    /*! \warning term structures initialized by means of this
                 constructor must manage their own reference date
                 by overriding the referenceDate() method. */
    public SwaptionVolatilityStructure(BusinessDayConvention bdc, DayCounter dc) {
        super(bdc, dc);
    }

    /** initialize with a fixed reference date */
    public SwaptionVolatilityStructure(Date referenceDate,
                                       Calendar calendar,
                                       BusinessDayConvention bdc,
                                       DayCounter dc) {
        super(referenceDate, calendar, bdc, dc);
    }

    /** calculate the reference date based on the global evaluation date */
    public SwaptionVolatilityStructure(int settlementDays,
                                       Calendar calendar,
                                       BusinessDayConvention bdc,
                                       DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);
    }

    //! returns the volatility for a given option date and swap length
    public double volatility(Date optionDate,
                             double swapLength,
                             double strike,
                             boolean extrapolate) {
        checkSwapTenor(swapLength, extrapolate);
        checkRange(optionDate, extrapolate);
        checkStrike(strike, extrapolate);
        double optionTime = timeFromReference(optionDate);
        return volatilityImpl(optionTime, swapLength, strike);
    }

    public double volatility(Date optionDate, double swapLength, double strike) {
        return volatility(optionDate, swapLength, strike, false);
    }

    /** returns the Black variance for a given option date and swap length */
    public double blackVariance(Date optionDate,
                                double swapLength,
                                double strike,
                                boolean extrapolate) {
        double v = volatility(optionDate, swapLength, strike, extrapolate);
        double optionTime = timeFromReference(optionDate);
        return v * v * optionTime;
    }

    public double blackVariance(Date optionDate, double swapLength, double strike) {
        return blackVariance(optionDate, swapLength, strike, false);
    }

    /** returns the shift for a given option date and swap length */
    public double shift(Date optionDate, double swapLength, boolean extrapolate) {
        checkSwapTenor(swapLength, extrapolate);
        checkRange(optionDate, extrapolate);
        double optionTime = timeFromReference(optionDate);
        return shiftImpl(optionTime, swapLength);
    }

    public double shift(Date optionDate, double swapLength) {
        return shift(optionDate, swapLength, false);
    }

    /** the largest length for which the term structure can return vols */
    public Period maxSwapTenor() {
        return new Period(100, TimeUnit.YEARS);
    }

    /** the largest swapLength for which the term structure can return vols */
    public double maxSwapLength() {
        return swapLength(maxSwapTenor());
    }

    /** volatility type */
    public Sarb.VolatilityType volatilityType() {
        return ShiftedLognormal;
    }

    /** implements the conversion between swap tenor and swap (time) length */
    public double swapLength(Period p) {
        QL_REQUIRE(p.length() > 0,
                "non-positive swap tenor (" + p + ") given");
        switch (p.units()) {
            case MONTHS:
                return p.length() / 12.0;
            case YEARS:
                return p.length();
            default:
                QL_FAIL("invalid Time Unit (" + p.units() + ") for swap length");
                return 0.0;
        }
    }

    /** implements the conversion between swap dates and swap (time) length */
    public double swapLength(Date start, Date end) {
        QL_REQUIRE(TimeUtils.greater(end, start), "swap end date (" + end
                + ") must be greater than start (" + start + ")");
        double result = TimeUtils.substract(end, start) / 365.25 * 12.0; // month unit
        result = Math.round(result); // ClosestRounding(0)
        result /= 12.0; // year unit
        return result;
    }

    protected void checkSwapTenor(Period swapTenor, boolean extrapolate) {
        QL_REQUIRE(swapTenor.length() > 0,
                "non-positive swap tenor (" + swapTenor + ") given");
        QL_REQUIRE(extrapolate || allowsExtrapolation()
                        || TimeUtils.leq(swapTenor, maxSwapTenor()),
                "swap tenor (" + swapTenor + ") is past max tenor ("
                        + maxSwapTenor() + ")");
    }

    protected void checkSwapTenor(double swapLength, boolean extrapolate) {
        QL_REQUIRE(swapLength > 0.0,
                "non-positive swap length (" + swapLength + ") given");
        QL_REQUIRE(extrapolate || allowsExtrapolation()
                        || swapLength <= maxSwapLength(),
                "swap tenor (" + swapLength + ") is past max tenor ("
                        + maxSwapLength() + ")");
    }

    //! implements the actual volatility calculation in derived classes
    protected double volatilityImpl(double optionTime, double swapLength, double strike) {
        return 0.0;
    }

    protected double shiftImpl(double optionTime, double swapLength) {
        QL_REQUIRE(volatilityType() == ShiftedLognormal,
                "shift parameter only makes sense for lognormal volatilities");
        return 0.0;
    }
}
