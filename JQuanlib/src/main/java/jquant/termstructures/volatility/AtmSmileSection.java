package jquant.termstructures.volatility;

import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Date;
import jquant.time.DayCounter;

/**
 * Smile section that allows for an explicit / alternate specification of the
 * at-the-money level.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/atmsmilesection.hpp} and
 * {@code atmsmilesection.cpp}. The official class copy-constructs its base
 * {@link SmileSection} from the source section and overrides every accessor.
 * The Java base class has final date accessors, so this port constructs the
 * base with the source's exercise date/time (including a copy of the dates,
 * because Java {@link Date} is mutable) and delegates the remaining
 * overridable accessors to the source. A source section built on an exercise
 * time carries no exercise date; because Java cannot choose between the two
 * {@code SmileSection} constructors at run time, {@link #of(SmileSection,double)}
 * selects the matching one.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/atmsmilesection.hpp
 */
public class AtmSmileSection extends SmileSection {

    private final SmileSection source_;
    private final double atmLevel_;

    /**
     * Creates the section matching the source's date/time basis.
     *
     * @param source source smile section
     * @param atm    at-the-money level; {@code Double.NaN} is the official
     *               {@code Null<Real>()} and means "use the source's ATM level"
     */
    public static AtmSmileSection of(SmileSection source, double atm) {
        if (source.exerciseDate() == null)
            return new AtmSmileSection(source, atm, source.exerciseTime());
        return new AtmSmileSection(source, atm, source.exerciseDate());
    }

    /** Time-based source section. */
    public AtmSmileSection(SmileSection source, double atm, double sourceExerciseTime) {
        super(sourceExerciseTime, source.dayCounter(),
                source.volatilityType(), source.shift());
        source_ = source;
        atmLevel_ = Double.isNaN(atm) ? source.atmLevel() : atm;
    }

    /** Date-based source section. */
    public AtmSmileSection(SmileSection source, double atm, Date sourceExerciseDate) {
        super(sourceExerciseDate.copy(), source.dayCounter(),
                source.referenceDate().copy(),
                source.volatilityType(), source.shift());
        source_ = source;
        atmLevel_ = Double.isNaN(atm) ? source.atmLevel() : atm;
    }

    @Override
    public double minStrike() {
        return source_.minStrike();
    }

    @Override
    public double maxStrike() {
        return source_.maxStrike();
    }

    @Override
    public double atmLevel() {
        return atmLevel_;
    }

    @Override
    public double exerciseTime() {
        return source_.exerciseTime();
    }

    @Override
    public VolatilityType volatilityType() {
        return source_.volatilityType();
    }

    @Override
    public double shift() {
        return source_.shift();
    }

    @Override
    protected double volatilityImpl(double strike) {
        return source_.volatility(strike);
    }

    @Override
    protected double varianceImpl(double strike) {
        return source_.variance(strike);
    }
}
