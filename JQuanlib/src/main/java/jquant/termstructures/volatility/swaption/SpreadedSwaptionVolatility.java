package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.SpreadedSmileSection;
import jquant.time.Calendar;
import jquant.time.DayCounter;
import jquant.time.Date;
import jquant.time.Period;

/**
 * Spreaded swaption volatility.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/spreadedswaptionvol.{hpp,cpp}}.
 *
 * <p>All virtual methods of the base classes are forwarded to the base
 * volatility structure; the spread is added to the base volatility.
 */
public class SpreadedSwaptionVolatility extends SwaptionVolatilityStructure {

    private final Handle<SwaptionVolatilityStructure> baseVol_;
    private final Handle<Quote> spread_;

    public SpreadedSwaptionVolatility(
            Handle<SwaptionVolatilityStructure> baseVol,
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
    public double minStrike() {
        return baseVol_.currentLink().minStrike();
    }

    @Override
    public double maxStrike() {
        return baseVol_.currentLink().maxStrike();
    }

    @Override
    public Period maxSwapTenor() {
        return baseVol_.currentLink().maxSwapTenor();
    }

    @Override
    public Sarb.VolatilityType volatilityType() {
        return baseVol_.currentLink().volatilityType();
    }

    @Override
    protected SmileSection smileSectionImpl(Date optionDate,
                                            Period swapTenor) {
        SmileSection baseSmile =
                baseVol_.currentLink().smileSection(optionDate, swapTenor, true);
        return new SpreadedSmileSection(baseSmile, spread_);
    }

    @Override
    protected SmileSection smileSectionImpl(double optionTime,
                                            double swapLength) {
        SmileSection baseSmile =
                baseVol_.currentLink().smileSection(optionTime, swapLength, true);
        return new SpreadedSmileSection(baseSmile, spread_);
    }

    @Override
    protected double volatilityImpl(Date optionDate,
                                    Period swapTenor,
                                    double strike) {
        return baseVol_.currentLink().volatility(optionDate, swapTenor,
                strike, true) + spread_.currentLink().value();
    }

    @Override
    protected double volatilityImpl(double optionTime,
                                    double swapLength,
                                    double strike) {
        return baseVol_.currentLink().volatility(optionTime, swapLength,
                strike, true) + spread_.currentLink().value();
    }

    @Override
    protected double shiftImpl(double optionTime, double swapLength) {
        return baseVol_.currentLink().shift(optionTime, swapLength, true);
    }
}
