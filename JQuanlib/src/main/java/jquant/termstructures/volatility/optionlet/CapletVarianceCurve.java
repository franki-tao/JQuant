package jquant.termstructures.volatility.optionlet;

import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.FlatSmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.equityfx.BlackVarianceCurve;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.calendars.NullCalendar;

import java.util.List;

import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Official QuantLib
 * {@code ql/termstructures/volatility/optionlet/capletvariancecurve.hpp}
 * port (header-only in official).
 */
public class CapletVarianceCurve extends OptionletVolatilityStructure {

    private final BlackVarianceCurve blackCurve_;
    private final VolatilityType type_;
    private final double displacement_;

    public CapletVarianceCurve(final Date referenceDate,
                               final List<Date> dates,
                               final List<Double> capletVolCurve,
                               final DayCounter dayCounter) {
        this(referenceDate, dates, capletVolCurve, dayCounter,
                ShiftedLognormal, 0.0);
    }

    public CapletVarianceCurve(final Date referenceDate,
                               final List<Date> dates,
                               final List<Double> capletVolCurve,
                               final DayCounter dayCounter,
                               VolatilityType type,
                               double displacement) {
        super(referenceDate, new NullCalendar(),
                BusinessDayConvention.FOLLOWING, new DayCounter());
        blackCurve_ = new BlackVarianceCurve(referenceDate, dates,
                capletVolCurve, dayCounter, false);
        type_ = type;
        displacement_ = displacement;
    }

    @Override
    public DayCounter dayCounter() {
        return blackCurve_.dayCounter();
    }

    @Override
    public Date maxDate() {
        return blackCurve_.maxDate();
    }

    @Override
    public double minStrike() {
        return blackCurve_.minStrike();
    }

    @Override
    public double maxStrike() {
        return blackCurve_.maxStrike();
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
    protected SmileSection smileSectionImpl(double t) {
        // dummy strike
        double atmVol = blackCurve_.blackVol(t, 0.05, true);
        return new FlatSmileSection(t, atmVol, dayCounter());
    }

    @Override
    protected double volatilityImpl(double t, double r) {
        return blackCurve_.blackVol(t, r, true);
    }
}
