package jquant.termstructures.volatility;

import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Flat smile section.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/flatsmilesection.hpp} and
 * {@code flatsmilesection.cpp}: a smile section whose volatility does not
 * depend on the strike. The only state is the flat volatility and the at-the-
 * money level; the date-based constructor forwards to the
 * {@link SmileSection} date constructor and the time-based one to its
 * exercise-time constructor.
 *
 * <p>Java {@link Date} instances are mutable, so the dates passed to the
 * date-based constructor are copied before being stored.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/flatsmilesection.hpp
 */
public class FlatSmileSection extends SmileSection {

    private final double vol_;
    private final double atmLevel_;

    /**
     * @param d             exercise date
     * @param vol           flat volatility
     * @param dc            day counter
     * @param referenceDate reference date; an empty {@link Date} means the
     *                      global evaluation date, as official
     * @param atmLevel      at-the-money level; {@code Double.NaN} is the
     *                      official {@code Null<Rate>()}
     * @param type          volatility type
     * @param shift         shift
     */
    public FlatSmileSection(Date d,
                            double vol,
                            DayCounter dc,
                            Date referenceDate,
                            double atmLevel,
                            VolatilityType type,
                            double shift) {
        super(d == null ? new Date() : d.copy(), dc,
                referenceDate == null ? new Date() : referenceDate.copy(),
                type, shift);
        vol_ = vol;
        atmLevel_ = atmLevel;
    }

    /** Official defaults: empty reference date, null ATM level, unshifted lognormal. */
    public FlatSmileSection(Date d, double vol, DayCounter dc) {
        this(d, vol, dc, new Date(), Double.NaN, ShiftedLognormal, 0.0);
    }

    public FlatSmileSection(Date d, double vol, DayCounter dc, Date referenceDate) {
        this(d, vol, dc, referenceDate, Double.NaN, ShiftedLognormal, 0.0);
    }

    public FlatSmileSection(Date d, double vol, DayCounter dc,
                            Date referenceDate, double atmLevel) {
        this(d, vol, dc, referenceDate, atmLevel, ShiftedLognormal, 0.0);
    }

    public FlatSmileSection(Date d, double vol, DayCounter dc,
                            Date referenceDate, double atmLevel,
                            VolatilityType type) {
        this(d, vol, dc, referenceDate, atmLevel, type, 0.0);
    }

    /**
     * @param exerciseTime exercise time
     * @param vol          flat volatility
     * @param dc           day counter
     * @param atmLevel     at-the-money level; {@code Double.NaN} is the
     *                     official {@code Null<Rate>()}
     * @param type         volatility type
     * @param shift        shift
     */
    public FlatSmileSection(double exerciseTime,
                            double vol,
                            DayCounter dc,
                            double atmLevel,
                            VolatilityType type,
                            double shift) {
        super(exerciseTime, dc, type, shift);
        vol_ = vol;
        atmLevel_ = atmLevel;
    }

    /** Official defaults: null ATM level, unshifted lognormal. */
    public FlatSmileSection(double exerciseTime, double vol, DayCounter dc) {
        this(exerciseTime, vol, dc, Double.NaN, ShiftedLognormal, 0.0);
    }

    public FlatSmileSection(double exerciseTime, double vol, DayCounter dc,
                            double atmLevel) {
        this(exerciseTime, vol, dc, atmLevel, ShiftedLognormal, 0.0);
    }

    public FlatSmileSection(double exerciseTime, double vol, DayCounter dc,
                            double atmLevel, VolatilityType type) {
        this(exerciseTime, vol, dc, atmLevel, type, 0.0);
    }

    @Override
    public double minStrike() {
        return QL_MIN_REAL - shift();
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    @Override
    public double atmLevel() {
        return atmLevel_;
    }

    @Override
    protected double volatilityImpl(double strike) {
        return vol_;
    }
}
