package jquant;

import jquant.math.interpolations.Extrapolator;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.time.*;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;
import static jquant.time.TimeUnit.DAYS;

//! Basic term-structure functionality
public abstract class TermStructure extends Extrapolator implements Observer, Observable {
    protected boolean moving_ = false;
    protected boolean updated_ = true;
    protected Calendar calendar_;
    private Date referenceDate_;
    private int settlementDays_;
    private DayCounter dayCounter_;
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    /*! \name Constructors

        There are three ways in which a term structure can keep
        track of its reference date.  The first is that such date
        is fixed; the second is that it is determined by advancing
        the current date of a given number of business days; and
        the third is that it is based on the reference date of
        some other structure.

        In the first case, the constructor taking a date is to be
        used; the default implementation of referenceDate() will
        then return such date. In the second case, the constructor
        taking a number of days and a calendar is to be used;
        referenceDate() will return a date calculated based on the
        current evaluation date, and the term structure and its
        observers will be notified when the evaluation date
        changes. In the last case, the referenceDate() method must
        be overridden in derived classes so that it fetches and
        return the appropriate date.
    */
    //@{
    //! default constructor
    /*! \warning term structures initialized by means of this
                 constructor must manage their own reference date
                 by overriding the referenceDate() method.
    */
    public TermStructure(DayCounter dc) {
        settlementDays_ = -1;
        dayCounter_ = dc;
    }

    //! initialize with a fixed reference date
    public TermStructure(final Date referenceDate, Calendar calendar, DayCounter dc) {
        calendar_ = calendar;
        referenceDate_ = referenceDate;
        settlementDays_ = -1;
        dayCounter_ = dc;
    }

    //! calculate the reference date based on the global evaluation date
    public TermStructure(int settlementDays, Calendar cal, DayCounter dc) {
        moving_ = true;
        updated_ = false;
        calendar_ = cal;
        settlementDays_ = settlementDays;
        dayCounter_ = dc;
        registerWith(Settings.instance.evaluationDate());
    }

    //! \name Dates and Time
    //@{
    //! the day counter used for date/time conversion
    public DayCounter dayCounter() {
        return dayCounter_;
    }

    //! date/time conversion
    public double timeFromReference(final Date d) {
        return dayCounter().yearFraction(referenceDate(), d, new Date(), new Date());
    }

    //! the latest date for which the curve can return values
    public abstract Date maxDate();

    //! the latest time for which the curve can return values
    public double maxTime() {
        return timeFromReference(maxDate());
    }

    //! the date at which discount = 1.0 and/or variance = 0.0
    public final Date referenceDate() {
        if (!updated_) {
            Date today = Settings.instance.evaluationDate().Date();
            referenceDate_ = calendar().advance(today, settlementDays(), DAYS, BusinessDayConvention.FOLLOWING, false);
            updated_ = true;
        }
        return referenceDate_;
    }

    //! the calendar used for reference and/or option date calculation
    public Calendar calendar() {
        return calendar_;
    }

    //! the settlementDays used for reference date calculation
    public int settlementDays() {
        QL_REQUIRE(settlementDays_ != -1,
                "settlement days not provided for this instance");
        return settlementDays_;
    }

    //@}
    //! \name Observer interface
    //@{
    @Override
    public void update() {
        if (moving_)
            updated_ = false;
        notifyObservers();
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

    //! date-range check
    protected void checkRange(final Date d, boolean extrapolate) {
        QL_REQUIRE(TimeUtils.geq(d, referenceDate()),
                "date (" + d + ") before reference date (" +
                        referenceDate() + ")");
        QL_REQUIRE(extrapolate || allowsExtrapolation() || TimeUtils.leq(d, maxDate()),
                "date (" + d + ") is past max curve date (" + maxDate() + ")");
    }

    //! time-range check
    protected void checkRange(double t, boolean extrapolate) {
        QL_REQUIRE(t >= 0.0,
                "negative time (" + t + ") given");
        QL_REQUIRE(extrapolate || allowsExtrapolation()
                        || t <= maxTime() || close_enough(t, maxTime()),
                "time (" + t + ") is past max curve time (" + maxTime() + ")");
    }
}
