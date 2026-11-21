package jquant.termstructures.volatility.optionlet;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.volatility.Sarb;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.DayCounter;
import jquant.time.Date;

/**
 * Spreaded caplet/floorlet volatility.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/spreadedoptionletvol.{hpp,cpp}}.
 *
 * <p>All virtual methods of the base classes are forwarded to the base
 * volatility structure; the spread is added to the base volatility.
 */
public class SpreadedOptionletVolatility extends OptionletVolatilityStructure {

    private final Handle<OptionletVolatilityStructure> baseVol_;
    private final Handle<Quote> spread_;

    public SpreadedOptionletVolatility(
            Handle<OptionletVolatilityStructure> baseVol,
            Handle<Quote> spread) {
        super(baseVol.currentLink().businessDayConvention(),
                baseVol.currentLink().dayCounter());
        baseVol_ = baseVol;
        spread_ = spread;
        if (baseVol.currentLink().allowsExtrapolation())
            enableExtrapolation();
        registerWith(baseVol_.get());
        registerWith(spread_.get());
    }

    @Override
    public BusinessDayConvention businessDayConvention() {
        return baseVol_.currentLink().businessDayConvention();
    }

    @Override
    public double minStrike() {
        return baseVol_.currentLink().minStrike();
    }

    @Override
    public double maxStrike() {
        return baseVol_.currentLink().maxStrike();
    }

    @Override
    public DayCounter dayCounter() {
        return baseVol_.currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return baseVol_.currentLink().maxDate();
    }

    @Override
    public double maxTime() {
        return baseVol_.currentLink().maxTime();
    }

    @Override
    public Date referenceDate() {
        return baseVol_.currentLink().referenceDate();
    }

    @Override
    public Calendar calendar() {
        return baseVol_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return baseVol_.currentLink().settlementDays();
    }

    @Override
    public Sarb.VolatilityType volatilityType() {
        return baseVol_.currentLink().volatilityType();
    }

    @Override
    public double displacement() {
        return baseVol_.currentLink().displacement();
    }

    @Override
    protected double volatilityImpl(double optionTime, double strike) {
        return baseVol_.currentLink().volatility(optionTime, strike, true)
                + spread_.currentLink().value();
    }
}
