package jquant.termstructures.volatility.optionlet;

import jquant.termstructures.SmileSection;
import jquant.termstructures.VolatilityTermStructure;
import jquant.termstructures.volatility.Sarb;
import jquant.time.*;

import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

public abstract class OptionletVolatilityStructure extends VolatilityTermStructure {
    /*! \name Constructors
        See the TermStructure documentation for issues regarding
        constructors.
    */
    //@{
    //! default constructor
    /*! \warning term structures initialized by means of this
                 constructor must manage their own reference date
                 by overriding the referenceDate() method.
    */
    // default bdc = Following, dc = DayCounter()
    public OptionletVolatilityStructure(BusinessDayConvention bdc, final DayCounter dc) {
        super(bdc, dc);
    }

    //! initialize with a fixed reference date
    public OptionletVolatilityStructure(final Date referenceDate,
                                        final Calendar cal,
                                        BusinessDayConvention bdc,
                                        final DayCounter dc) {
        super(referenceDate, cal, bdc, dc);
    }

    //! calculate the reference date based on the global evaluation date
    public OptionletVolatilityStructure(int settlementDays,
                                        final Calendar cal,
                                        BusinessDayConvention bdc,
                                        final DayCounter dc) {
        super(settlementDays, cal, bdc, dc);
    }

    //! returns the volatility for a given option tenor and strike rate
    // default extrapolate = false
    public double volatility(final Period optionTenor,
                             double strike,
                             boolean extrapolate) {
        Date optionDate = optionDateFromTenor(optionTenor);
        return volatility(optionDate, strike, extrapolate);
    }

    //! returns the volatility for a given option date and strike rate
    // default extrapolate = false
    public double volatility(final Date optionDate,
                             double strike,
                             boolean extrapolate) {
        checkRange(optionDate, extrapolate);
        checkStrike(strike, extrapolate);
        return volatilityImpl(optionDate, strike);
    }

    //! returns the volatility for a given option time and strike rate
    // default extrapolate = false
    public double volatility(double optionTime,
                             double strike,
                             boolean extrapolate) {
        checkRange(optionTime, extrapolate);
        checkStrike(strike, extrapolate);
        return volatilityImpl(optionTime, strike);
    }

    //! returns the Black variance for a given option tenor and strike rate
    // default extrapolate = false
    public double blackVariance(final Period optionTenor,
                                double strike,
                                boolean extrapolate) {
        Date optionDate = optionDateFromTenor(optionTenor);
        return blackVariance(optionDate, strike, extrapolate);
    }

    //! returns the Black variance for a given option date and strike rate
    // default extrapolate = false
    public double blackVariance(final Date optionDate,
                                double strike,
                                boolean extrapolate) {
        double v = volatility(optionDate, strike, extrapolate);
        double t = timeFromReference(optionDate);
        return v * v * t;
    }

    //! returns the Black variance for a given option time and strike rate
    // default extrapolate = false
    public double blackVariance(double optionTime,
                                double strike,
                                boolean extrapolate) {
        double v = volatility(optionTime, strike, extrapolate);
        return v * v * optionTime;
    }

    //! returns the smile for a given option tenor
    public SmileSection smileSection(final Period optionTenor,
                                     boolean extr //false
    ) {
        Date optionDate = optionDateFromTenor(optionTenor);
        return smileSection(optionDate, extr);
    }

    //! returns the smile for a given option date
    public SmileSection smileSection(final Date optionDate,
                                     boolean extr //false
    ) {
        checkRange(optionDate, extr);
        return smileSectionImpl(optionDate);
    }

    //! returns the smile for a given option time
    public SmileSection smileSection(double optionTime,
                                     boolean extr //false
    ) {
        checkRange(optionTime, extr);
        return smileSectionImpl(optionTime);
    }

    public Sarb.VolatilityType volatilityType() {
        return ShiftedLognormal;
    }

    public double displacement() {
        return 0d;
    }

    //! implements the actual volatility calculation in derived classes
    protected abstract double volatilityImpl(double optionTime, double strike);

    protected double volatilityImpl(final Date optionDate, double strike) {
        return volatilityImpl(timeFromReference(optionDate), strike);
    }

    //! implements the actual smile calculation in derived classes
    protected abstract SmileSection smileSectionImpl(double optionTime);

    protected SmileSection smileSectionImpl(final Date optionDate) {
        return smileSectionImpl(timeFromReference(optionDate));
    }

}
