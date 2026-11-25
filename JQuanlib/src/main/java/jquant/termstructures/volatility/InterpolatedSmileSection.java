package jquant.termstructures.volatility;

import jquant.Handle;
import jquant.Quote;
import jquant.math.interpolations.LinearInterpolation;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Smile section interpolating standard deviations linearly in strike.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/interpolatedsmilesection.hpp}, for the
 * {@code Linear} interpolator used by the official interpolated swaption
 * volatility cube. The section is lazy, as official: the standard deviations
 * (or the handles holding them) are only turned into volatilities and the
 * interpolation is only updated when a value is requested.
 *
 * <p>The official class also offers date-based constructors. They are not
 * needed by the cube and are omitted here; only the time-based paths, which
 * the cube uses, are ported.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/interpolatedsmilesection.hpp
 */
public class InterpolatedSmileSection extends SmileSection {

    private final double exerciseTimeSquareRoot_;
    private final double[] strikes_;
    private final List<Handle<Quote>> stdDevHandles_;
    private final Handle<Quote> atmLevel_;
    private final double[] vols_;
    private final LinearInterpolation interpolation_;
    private final boolean flatStrikeExtrapolation_;

    private boolean calculated_ = false;

    /**
     * Time-based section from plain standard deviations and an ATM level.
     * As official, dummy handles are created but no observers are registered.
     */
    public InterpolatedSmileSection(double expiryTime,
                                    double[] strikes,
                                    double[] stdDevs,
                                    double atmLevel,
                                    DayCounter dc,
                                    VolatilityType type,
                                    double shift,
                                    boolean flatStrikeExtrapolation) {
        super(expiryTime, dc, type, shift);
        exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        strikes_ = strikes.clone();
        stdDevHandles_ = new ArrayList<>(stdDevs.length);
        vols_ = new double[stdDevs.length];
        for (double stdDev : stdDevs)
            stdDevHandles_.add(new Handle<>(
                    new SimpleQuote(stdDev), true));
        atmLevel_ = new Handle<>(new SimpleQuote(atmLevel), true);
        flatStrikeExtrapolation_ = flatStrikeExtrapolation;

        checkStrikes();
        interpolation_ = new LinearInterpolation(strikes_, vols_);
    }

    /** Official defaults: {@code Actual365Fixed}, lognormal, no shift, no flat extrapolation. */
    public InterpolatedSmileSection(double expiryTime,
                                    double[] strikes,
                                    double[] stdDevs,
                                    double atmLevel,
                                    DayCounter dc,
                                    VolatilityType type,
                                    double shift) {
        this(expiryTime, strikes, stdDevs, atmLevel, dc, type, shift, false);
    }

    /** Official defaults: {@code Actual365Fixed}, lognormal, no shift. */
    public InterpolatedSmileSection(double expiryTime,
                                    double[] strikes,
                                    double[] stdDevs,
                                    double atmLevel) {
        this(expiryTime, strikes, stdDevs, atmLevel,
                new Actual365Fixed(), ShiftedLognormal, 0.0, false);
    }

    /**
     * Time-based section from standard-deviation handles and an ATM-level
     * handle. Registers with both sets of handles, as official.
     */
    public InterpolatedSmileSection(double expiryTime,
                                    double[] strikes,
                                    List<Handle<Quote>> stdDevHandles,
                                    Handle<Quote> atmLevel,
                                    DayCounter dc,
                                    VolatilityType type,
                                    double shift,
                                    boolean flatStrikeExtrapolation) {
        super(expiryTime, dc, type, shift);
        exerciseTimeSquareRoot_ = Math.sqrt(exerciseTime());
        strikes_ = strikes.clone();
        stdDevHandles_ = new ArrayList<>(stdDevHandles);
        vols_ = new double[stdDevHandles_.size()];
        atmLevel_ = atmLevel;
        flatStrikeExtrapolation_ = flatStrikeExtrapolation;

        for (Handle<Quote> stdDevHandle : stdDevHandles_)
            registerWith(stdDevHandle.get());
        registerWith(atmLevel_.get());

        checkStrikes();
        interpolation_ = new LinearInterpolation(strikes_, vols_);
    }

    /** Official defaults: {@code Actual365Fixed}, lognormal, no shift, no flat extrapolation. */
    public InterpolatedSmileSection(double expiryTime,
                                    double[] strikes,
                                    List<Handle<Quote>> stdDevHandles,
                                    Handle<Quote> atmLevel,
                                    DayCounter dc,
                                    VolatilityType type,
                                    double shift) {
        this(expiryTime, strikes, stdDevHandles, atmLevel, dc, type, shift,
                false);
    }

    @Override
    public double minStrike() {
        return strikes_[0];
    }

    @Override
    public double maxStrike() {
        return strikes_[strikes_.length - 1];
    }

    @Override
    public double atmLevel() {
        return atmLevel_.currentLink().value();
    }

    @Override
    public double varianceImpl(double strike) {
        double v = volatilityImpl(strike);
        return v * v * exerciseTime();
    }

    @Override
    protected double volatilityImpl(double strike) {
        calculate();

        if (flatStrikeExtrapolation_) {
            if (strike < minStrike())
                return interpolation_.value(minStrike(), true);
            else if (strike > maxStrike())
                return interpolation_.value(maxStrike(), true);
        }

        return Math.max(interpolation_.value(strike, true), 0.0);
    }

    /** Mirrors the official {@code LazyObject::calculate()}. */
    private void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    private void performCalculations() {
        for (int i = 0; i < stdDevHandles_.size(); ++i)
            vols_[i] = stdDevHandles_.get(i).currentLink().value()
                    / exerciseTimeSquareRoot_;
        interpolation_.update();
    }

    @Override
    public void update() {
        calculated_ = false;
        super.update();
    }

    private void checkStrikes() {
        for (int i = 1; i < strikes_.length; ++i)
            QL_REQUIRE(strikes_[i - 1] <= strikes_[i],
                    "Strikes have to be sorted in ascending order");
    }
}
