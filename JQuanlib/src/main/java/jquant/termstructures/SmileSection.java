package jquant.termstructures;

import jquant.Option;
import jquant.Settings;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.volatility.Sarb;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;
import static jquant.pricingengines.BlackFormula.*;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

//! interest rate volatility smile section
/*! This abstract class provides volatility smile section interface */
public abstract class SmileSection implements Observer, Observable {
    private boolean isFloating_;
    private Date referenceDate_;
    private Date exerciseDate_;
    private DayCounter dc_;
    private double exerciseTime_;
    private Sarb.VolatilityType volatilityType_;
    private double shift_;
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    /**
     *
     * @param d             date
     * @param dc            daycounter
     * @param referenceDate referenceDate
     * @param type          ShiftedLognormal
     * @param shift         0.0
     */
    public SmileSection(final Date d,
                        DayCounter dc,
                        final Date referenceDate,
                        Sarb.VolatilityType type,
                        double shift) {
        exerciseDate_ = d;
        dc_ = dc;
        volatilityType_ = type;
        shift_ = shift;
        isFloating_ = TimeUtils.equals(referenceDate, new Date());
        if (isFloating_) {
            registerWith(Settings.instance.evaluationDate());
            referenceDate_ = Settings.instance.evaluationDate().Date();
        } else
            referenceDate_ = referenceDate;
        this.initializeExerciseTime();
    }

    /**
     *
     * @param exerciseTime exercise Time
     * @param dc           daycounter
     * @param type         ShiftedLognormal
     * @param shift        0.0
     */
    public SmileSection(double exerciseTime,
                        DayCounter dc,
                        Sarb.VolatilityType type,
                        double shift) {
        isFloating_ = false;
        dc_ = dc;
        exerciseTime_ = exerciseTime;
        volatilityType_ = type;
        shift_ = shift;
        QL_REQUIRE(exerciseTime_ >= 0.0,
                "expiry time must be positive: " +
                        exerciseTime_ + " not allowed");
    }

    public SmileSection() {
    }

    public abstract double minStrike();

    public abstract double maxStrike();

    public double variance(double strike) {
        return varianceImpl(strike);
    }

    public double volatility(double strike) {
        return volatilityImpl(strike);
    }

    public abstract double atmLevel();

    public final Date exerciseDate() {
        return exerciseDate_;
    }

    public Sarb.VolatilityType volatilityType() {
        return volatilityType_;
    }

    public double shift() {
        return shift_;
    }

    public final Date referenceDate() {
        QL_REQUIRE(TimeUtils.neq(referenceDate_, new Date()),
                "referenceDate not available for this instance");
        return referenceDate_;
    }

    public double exerciseTime() {
        return exerciseTime_;
    }

    public final DayCounter dayCounter() {
        return dc_;
    }

    /**
     *
     * @param strike   strike
     * @param type     call
     * @param discount 1.0
     * @return option Price
     */
    public double optionPrice(double strike,
                              Option.Type type,
                              double discount) {
        double atm = atmLevel();
        QL_REQUIRE(!Double.isNaN(atm),
                "smile section must provide atm level to compute option price");
        // if lognormal or shifted lognormal,
        // for strike at -shift, return option price even if outside
        // minstrike, maxstrike interval
        if (volatilityType() == ShiftedLognormal)
            return blackFormula(type, strike, atm, Math.abs(strike + shift()) < QL_EPSILON ?
                    0.2 : (Math.sqrt(variance(strike))), discount, shift());
        else
            return bachelierBlackFormula(type, strike, atm, Math.sqrt(variance(strike)), discount);
    }

    /**
     *
     * @param strike   rate
     * @param type     call
     * @param discount 1.0
     * @param gap      1e-5
     * @return digital Option Price
     */
    public double digitalOptionPrice(double strike,
                                     Option.Type type,
                                     double discount,
                                     double gap) {
        double m = volatilityType() == ShiftedLognormal ? (-shift()) : -QL_MAX_REAL;
        double kl = Math.max(strike - gap / 2.0, m);
        double kr = kl + gap;
        return (type == Option.Type.Call ? 1.0 : -1.0) *
                (optionPrice(kl, type, discount) - optionPrice(kr, type, discount)) / gap;
    }

    /**
     *
     * @param strike   rate
     * @param discount 1.0
     * @return vega
     */
    public double vega(double strike, double discount) {
        double atm = atmLevel();
        QL_REQUIRE(!Double.isNaN(atm),
                "smile section must provide atm level to compute option vega");
        if (volatilityType() == ShiftedLognormal)
            return blackFormulaVolDerivative(strike, atmLevel(),
                    Math.sqrt(variance(strike)),
                    exerciseTime(), discount, shift()) * 0.01;
        else
            QL_FAIL("vega for normal smilesection not yet implemented");
        return 0;
    }

    /**
     *
     * @param strike   rate
     * @param discount 1.0
     * @param gap      1e-4
     * @return
     */
    public double density(double strike,
                          double discount,
                          double gap) {
        double m = volatilityType() == ShiftedLognormal ? (-shift()) : -QL_MAX_REAL;
        double kl = Math.max(strike - gap / 2.0, m);
        double kr = kl + gap;
        return (digitalOptionPrice(kl, Option.Type.Call, discount, gap) -
                digitalOptionPrice(kr, Option.Type.Call, discount, gap)) / gap;
    }

    /**
     *
     * @param strike rate
     * @param volatilityType type
     * @param shift 0.0
     * @return volatility
     */
    public double volatility(double strike, Sarb.VolatilityType volatilityType, double shift) {
        if(volatilityType == volatilityType_ && close(shift,this.shift()))
            return volatility(strike);
        double atm = atmLevel();
        QL_REQUIRE(!Double.isNaN(atm),
                "smile section must provide atm level to compute converted volatilties");
        Option.Type type = strike >= atm ? Option.Type.Call : Option.Type.Put;
        double premium = optionPrice(strike,type,1.0);
        double premiumAtm = optionPrice(atm,type, 1.0);
        if (volatilityType == ShiftedLognormal) {
            try {
                return blackFormulaImpliedStdDev(type, strike, atm, premium,
                        1.0, shift, Double.NaN, 1e-6, 100) /
                        Math.sqrt(exerciseTime());
            } catch(Exception e) {
                return blackFormulaImpliedStdDevChambers(
                        type, strike, atm, premium, premiumAtm, 1.0, shift) /
                        Math.sqrt(exerciseTime());
            }
        } else {
            return bachelierBlackFormulaImpliedVol(type, strike, atm,
                    exerciseTime(), premium, 1.0);
        }
    }

    @Override
    public void update() {
        if (isFloating_) {
            referenceDate_ = Settings.instance.evaluationDate().Date();
            initializeExerciseTime();
        }
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable o) {
        if (o != null) {
            o.unregisterObserver(this);
            registeredObservables.remove(o);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable o : registeredObservables) {
            o.unregisterObserver(this);
        }
        registeredObservables.clear();
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }

    protected void initializeExerciseTime() {
        QL_REQUIRE(TimeUtils.geq(exerciseDate_, referenceDate_),
                "expiry date (" + exerciseDate_ +
                        ") must be greater than reference date (" +
                        referenceDate_ + ")");
        exerciseTime_ = dc_.yearFraction(referenceDate_, exerciseDate_, new Date(), new Date());
    }

    protected double varianceImpl(double strike) {
        double v = volatilityImpl(strike);
        return v * v * exerciseTime();
    }

    protected abstract double volatilityImpl(double strike);
}
