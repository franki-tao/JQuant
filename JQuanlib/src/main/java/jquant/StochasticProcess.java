package jquant;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.time.Date;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
/// ! multi-dimensional stochastic process class.

/**! This class describes a stochastic process governed by
 \f[
 d\mathrm{x}_t = \mu(t, x_t)\mathrm{d}t
 + \sigma(t, \mathrm{x}_t) \cdot d\mathrm{W}_t.
 \f]
 */
public abstract class StochasticProcess implements Observer, Observable {
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();
    protected discretization discretization_;

    public StochasticProcess() {
    }

    public StochasticProcess(discretization discretization) {
        discretization_ = discretization;
    }

    //! \name Stochastic process interface
    //@{
    //! returns the number of dimensions of the stochastic process
    public abstract int size();

    //! returns the number of independent factors of the process
    public int factors() {
        return size();
    }

    //! returns the initial values of the state variables
    public abstract Array initialValues();

    /*! \brief returns the drift part of the equation, i.e.,
               \f$ \mu(t, \mathrm{x}_t) \f$
    */
    public abstract Array drift(double t, final Array x);

    /*! \brief returns the diffusion part of the equation, i.e.
               \f$ \sigma(t, \mathrm{x}_t) \f$
    */
    public abstract Matrix diffusion(double t, final Array x);

    /*! returns the expectation
        \f$ E(\mathrm{x}_{t_0 + \Delta t}
            | \mathrm{x}_{t_0} = \mathrm{x}_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public Array expectation(double t0, final Array x0, double dt) {
        return apply(x0, discretization_.drift(this,  t0, x0, dt));
    }

    /*! returns the standard deviation
        \f$ S(\mathrm{x}_{t_0 + \Delta t}
            | \mathrm{x}_{t_0} = \mathrm{x}_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public Matrix stdDeviation(double t0, final Array x0, double dt) {
        return discretization_.diffusion(this,  t0, x0, dt);
    }

    /*! returns the covariance
        \f$ V(\mathrm{x}_{t_0 + \Delta t}
            | \mathrm{x}_{t_0} = \mathrm{x}_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public Matrix covariance(double t0, final Array x0, double dt) {
        return discretization_.covariance(this, t0, x0, dt);
    }

    /*! returns the asset value after a time interval \f$ \Delta t
        \f$ according to the given discretization. By default, it
        returns
        \f[
        E(\mathrm{x}_0,t_0,\Delta t) +
        S(\mathrm{x}_0,t_0,\Delta t) \cdot \Delta \mathrm{w}
        \f]
        where \f$ E \f$ is the expectation and \f$ S \f$ the
        standard deviation.
    */
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        return apply(expectation(t0, x0, dt), stdDeviation(t0, x0, dt).mutiply(dw));
    }

    /*! applies a change to the asset value. By default, it
        returns \f$ \mathrm{x} + \Delta \mathrm{x} \f$.
    */
    public Array apply(final Array x0, final Array dx) {
        return x0.add(dx);
    }
    //@}

    //! \name utilities
    //@{
    /*! returns the time value corresponding to the given date
        in the reference system of the stochastic process.

        \note As a number of processes might not need this
              functionality, a default implementation is given
              which raises an exception.
    */
    public double time(final Date d) {
        QL_FAIL("date/time conversion not supported");
        return 0;
    }
    //@}

    //! \name Observer interface
    //@{
    @Override
    public void update() {
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
}
