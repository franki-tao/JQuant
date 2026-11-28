package jquant.termstructures.volatility;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.SmileSection;
import jquant.time.Date;
import jquant.time.DayCounter;

/**
 * Spreaded smile section.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/spreadedsmilesection.hpp}.
 *
 * <p>Forwards the smile-section interface to the underlying section and adds
 * the spread quote to the volatility.
 */
public class SpreadedSmileSection extends SmileSection {

    private final SmileSection underlyingSection_;
    private final Handle<Quote> spread_;

    public SpreadedSmileSection(SmileSection underlyingSection,
                                Handle<Quote> spread) {
        super();
        underlyingSection_ = underlyingSection;
        spread_ = spread;
        registerWith(underlyingSection_);
        registerWith(spread_.get());
    }

    @Override
    public double minStrike() {
        return underlyingSection_.minStrike();
    }

    @Override
    public double maxStrike() {
        return underlyingSection_.maxStrike();
    }

    @Override
    public double atmLevel() {
        return underlyingSection_.atmLevel();
    }

    @Override
    public Date exerciseDate() {
        return underlyingSection_.exerciseDate();
    }

    @Override
    public double exerciseTime() {
        return underlyingSection_.exerciseTime();
    }

    @Override
    public DayCounter dayCounter() {
        return underlyingSection_.dayCounter();
    }

    @Override
    public Date referenceDate() {
        return underlyingSection_.referenceDate();
    }

    @Override
    public Sarb.VolatilityType volatilityType() {
        return underlyingSection_.volatilityType();
    }

    @Override
    public double shift() {
        return underlyingSection_.shift();
    }

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    protected double volatilityImpl(double strike) {
        return underlyingSection_.volatility(strike)
                + spread_.currentLink().value();
    }
}
