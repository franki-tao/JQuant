package jquant.termstructures.volatility.optionlet;

import jquant.Handle;
import jquant.Quote;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Constant caplet/floorlet volatility, with no time or strike dependence.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/optionlet/constantoptionletvol.cpp
 */
public class ConstantOptionletVolatility extends OptionletVolatilityStructure {
    private final Handle<Quote> volatility_;
    private final VolatilityType type_;
    private final double displacement_;

    /** Floating reference date, floating market data. */
    public ConstantOptionletVolatility(int settlementDays,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       Handle<Quote> volatility,
                                       DayCounter dc) {
        this(settlementDays, cal, bdc, volatility, dc, ShiftedLognormal, 0.0);
    }

    public ConstantOptionletVolatility(int settlementDays,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       Handle<Quote> volatility,
                                       DayCounter dc,
                                       VolatilityType type,
                                       double displacement) {
        super(settlementDays, cal, bdc, dc);
        volatility_ = volatility;
        type_ = type;
        displacement_ = displacement;
        registerWith(volatility_.get());
    }

    /** Fixed reference date, floating market data. */
    public ConstantOptionletVolatility(Date referenceDate,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       Handle<Quote> volatility,
                                       DayCounter dc) {
        this(referenceDate, cal, bdc, volatility, dc, ShiftedLognormal, 0.0);
    }

    public ConstantOptionletVolatility(Date referenceDate,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       Handle<Quote> volatility,
                                       DayCounter dc,
                                       VolatilityType type,
                                       double displacement) {
        super(referenceDate, cal, bdc, dc);
        volatility_ = volatility;
        type_ = type;
        displacement_ = displacement;
        registerWith(volatility_.get());
    }

    /** Floating reference date, fixed market data. */
    public ConstantOptionletVolatility(int settlementDays,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       double volatility,
                                       DayCounter dc) {
        this(settlementDays, cal, bdc, volatility, dc, ShiftedLognormal, 0.0);
    }

    public ConstantOptionletVolatility(int settlementDays,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       double volatility,
                                       DayCounter dc,
                                       VolatilityType type,
                                       double displacement) {
        super(settlementDays, cal, bdc, dc);
        volatility_ = new Handle<>(new SimpleQuote(volatility), true);
        type_ = type;
        displacement_ = displacement;
    }

    /** Fixed reference date, fixed market data (used by the official tests). */
    public ConstantOptionletVolatility(Date referenceDate,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       double volatility,
                                       DayCounter dc) {
        this(referenceDate, cal, bdc, volatility, dc, ShiftedLognormal, 0.0);
    }

    public ConstantOptionletVolatility(Date referenceDate,
                                       Calendar cal,
                                       BusinessDayConvention bdc,
                                       double volatility,
                                       DayCounter dc,
                                       VolatilityType type,
                                       double displacement) {
        super(referenceDate, cal, bdc, dc);
        volatility_ = new Handle<>(new SimpleQuote(volatility), true);
        type_ = type;
        displacement_ = displacement;
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    @Override
    public VolatilityType volatilityType() {
        return type_;
    }

    @Override
    public double displacement() {
        return displacement_;
    }

    @Override
    protected double volatilityImpl(double optionTime, double strike) {
        return volatility_.currentLink().value();
    }
}
