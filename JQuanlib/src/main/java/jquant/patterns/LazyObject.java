package jquant.patterns;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;

//! Framework for calculation on demand and result caching.
/*! \ingroup patterns */
public abstract class LazyObject implements Observable, Observer {
    protected boolean calculated_, frozen_, alwaysForward_;
    private boolean updating_;
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    public LazyObject() {
        calculated_ = false;
        frozen_ = false;
        updating_ = false;
    }
    @Override
    public void update() {
        if (updating_) {
            QL_FAIL("recursive notification loop detected; you probably created an object cycle");
            return;
        }

        // This sets updating to true (so the above check breaks the
        // infinite loop if we enter this method recursively) and will
        // set it back to false when we exit this scope, either
        // successfully or because of an exception.
        updating_ = true;

        // forwards notifications only the first time
        if (calculated_ || alwaysForward_) {
            // set to false early
            // 1) to prevent infinite recursion
            // 2) otherways non-lazy observers would be served obsolete
            //    data because of calculated_ being still true
            calculated_ = false;
            // observers don't expect notifications from frozen objects
            if (!frozen_)
                notifyObservers();
            // exiting notifyObservers() calculated_ could be
            // already true because of non-lazy observers
        }
        updating_ = false;
    }

    public boolean isCalculated() {
        return calculated_;
    }
    /*! \name Calculations
        These methods do not modify the structure of the object
        and are therefore declared as <tt>const</tt>. Data members
        which will be calculated on demand need to be declared as
        mutable.
    */
    //@{
    /*! This method force the recalculation of any results which
        would otherwise be cached. It is not declared as
        <tt>const</tt> since it needs to call the
        non-<tt>const</tt> <i><b>notifyObservers</b></i> method.

        \note Explicit invocation of this method is <b>not</b>
              necessary if the object registered itself as
              observer with the structures on which such results
              depend.  It is strongly advised to follow this
              policy when possible.
    */
    public void recalculate() {
        boolean wasFrozen = frozen_;
        calculated_ = frozen_ = false;
        try {
            calculate();
        } catch (Exception e) {
            frozen_ = wasFrozen;
            notifyObservers();
            throw e;
        }
        frozen_ = wasFrozen;
        notifyObservers();
    }
    /*! This method constrains the object to return the presently
        cached results on successive invocations, even if
        arguments upon which they depend should change.
    */
    public void freeze() {
        frozen_ = true;
    }
    /*! This method reverts the effect of the <i><b>freeze</b></i>
        method, thus re-enabling recalculations.
    */
    public void unfreeze() {
        frozen_ = false;
    }

    //! \name Notification settings
    //@{
    /*! This method causes the object to forward the first notification received,
        and discard the others until recalculated; the rationale is that observers
        were already notified, and don't need further notifications until they
        recalculate, at which point this object would be recalculated too.
        After recalculation, this object would again forward the first notification
        received.

        Although not always correct, this behavior is a lot faster
        and thus is the current default.  The default can be
        changed at compile time, or at at run time by calling
        `LazyObject::Defaults::instance().alwaysForwardNotifications()`;
        the run-time change won't affect lazy objects already created.
    */
    public void forwardFirstNotificationOnly() {
        alwaysForward_ = false;
    }
    /*! This method causes the object to forward all notifications received.

        Although safer, this behavior is a lot slower and thus
        usually not the default.  The default can be changed at
        compile time, or at run-time by calling
        `LazyObject::Defaults::instance().alwaysForwardNotifications()`;
        the run-time change won't affect lazy objects already
        created.
    */
    public void alwaysForwardNotifications() {
        alwaysForward_ = true;
    }

    /*! This method performs all needed calculations by calling
        the <i><b>performCalculations</b></i> method.

        \warning Objects cache the results of the previous
                 calculation. Such results will be returned upon
                 later invocations of
                 <i><b>calculate</b></i>. When the results depend
                 on arguments which could change between
                 invocations, the lazy object must register itself
                 as observer of such objects for the calculations
                 to be performed again when they change.

        \warning Should this method be redefined in derived
                 classes, LazyObject::calculate() should be called
                 in the overriding method.
    */
    public void calculate() {
        if (!calculated_ && !frozen_) {
            calculated_ = true;   // prevent infinite recursion in
            // case of bootstrapping
            try {
                performCalculations();
            } catch (Exception e) {
                calculated_ = false;
                throw e;
            }
        }
    }
    /*! This method must implement any calculations which must be
        (re)done in order to calculate the desired results.
    */
    protected abstract void performCalculations();

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
