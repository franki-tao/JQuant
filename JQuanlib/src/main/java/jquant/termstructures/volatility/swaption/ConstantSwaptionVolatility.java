package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.FlatSmileSection;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.*;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Constant swaption volatility, no time-strike dependence.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/swaptionconstantvol.{hpp,cpp}}.
 * The smile section is flat: its volatility is the current quote value,
 * independent of the strike, with the structure's volatility type and shift.
 */
public class ConstantSwaptionVolatility extends SwaptionVolatilityStructure {
    private final Handle<Quote> volatility_;
    private final Period maxSwapTenor_;
    private final VolatilityType volatilityType_;
    private final double shift_;

    /** floating reference date, floating market data */
    public ConstantSwaptionVolatility(int settlementDays,
                                      Calendar cal,
                                      BusinessDayConvention bdc,
                                      Handle<Quote> volatility,
                                      DayCounter dc,
                                      VolatilityType type,
                                      double shift) {
        super(settlementDays, cal, bdc, dc);
        volatility_ = volatility;
        maxSwapTenor_ = new Period(100, TimeUnit.YEARS);
        volatilityType_ = type;
        shift_ = shift;
        registerWith(volatility_.get());
    }

    /** fixed reference date, floating market data */
    public ConstantSwaptionVolatility(Date referenceDate,
                                      Calendar cal,
                                      BusinessDayConvention bdc,
                                      Handle<Quote> volatility,
                                      DayCounter dc,
                                      VolatilityType type,
                                      double shift) {
        super(referenceDate, cal, bdc, dc);
        volatility_ = volatility;
        maxSwapTenor_ = new Period(100, TimeUnit.YEARS);
        volatilityType_ = type;
        shift_ = shift;
        registerWith(volatility_.get());
    }

    /** floating reference date, fixed market data */
    public ConstantSwaptionVolatility(int settlementDays,
                                      Calendar cal,
                                      BusinessDayConvention bdc,
                                      double volatility,
                                      DayCounter dc,
                                      VolatilityType type,
                                      double shift) {
        this(settlementDays, cal, bdc,
                new Handle<>(new SimpleQuote(volatility), true), dc, type, shift);
    }

    /** fixed reference date, fixed market data */
    public ConstantSwaptionVolatility(Date referenceDate,
                                      Calendar cal,
                                      BusinessDayConvention bdc,
                                      double volatility,
                                      DayCounter dc,
                                      VolatilityType type,
                                      double shift) {
        this(referenceDate, cal, bdc,
                new Handle<>(new SimpleQuote(volatility), true), dc, type, shift);
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
    public Period maxSwapTenor() {
        return maxSwapTenor_;
    }

    @Override
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    @Override
    protected double shiftImpl(double optionTime, double swapLength) {
        // consistency check
        super.shiftImpl(optionTime, swapLength);
        return shift_;
    }

    @Override
    protected double volatilityImpl(double optionTime, double swapLength, double strike) {
        return volatility_.currentLink().value();
    }

    @Override
    protected SmileSection smileSectionImpl(Date d, Period swapTenor) {
        double atmVol = volatility_.currentLink().value();
        return new FlatSmileSection(d, atmVol, dayCounter(), referenceDate(),
                Double.NaN, volatilityType_, shift_);
    }

    @Override
    protected SmileSection smileSectionImpl(double optionTime, double swapLength) {
        double atmVol = volatility_.currentLink().value();
        return new FlatSmileSection(optionTime, atmVol, dayCounter(),
                Double.NaN, volatilityType_, shift_);
    }
}
