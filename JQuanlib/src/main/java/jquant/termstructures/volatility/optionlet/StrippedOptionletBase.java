package jquant.termstructures.volatility.optionlet;

import jquant.patterns.LazyObject;
import jquant.termstructures.volatility.Sarb;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.List;

/**
 * Abstract base class interface for a (time indexed) vector of (strike
 * indexed) optionlet (i.e. caplet/floorlet) volatilities.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/strippedoptionletbase.hpp}.
 */
public abstract class StrippedOptionletBase extends LazyObject {

    public abstract List<Double> optionletStrikes(int i);

    public abstract List<Double> optionletVolatilities(int i);

    public abstract List<Date> optionletFixingDates();

    public abstract double[] optionletFixingTimes();

    public abstract int optionletMaturities();

    public abstract double[] atmOptionletRates();

    public abstract DayCounter dayCounter();

    public abstract Calendar calendar();

    public abstract int settlementDays();

    public abstract BusinessDayConvention businessDayConvention();

    public abstract Sarb.VolatilityType volatilityType();

    public abstract double displacement();
}
