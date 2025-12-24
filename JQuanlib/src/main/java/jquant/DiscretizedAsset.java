package jquant;

import jquant.math.Array;

import java.util.List;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.close_enough;

//! Discretized asset class used by numerical methods
public abstract class DiscretizedAsset {
    protected double time_;
    protected double latestPreAdjustment_, latestPostAdjustment_;
    protected Array values_;

    /*! Indicates if a coupon should be adjusted in preAdjustValues() or postAdjustValues(). */
    protected enum CouponAdjustment {pre, post}

    private Lattice method_;

    public DiscretizedAsset() {
        latestPreAdjustment_ = QL_MAX_REAL;
        latestPostAdjustment_ = QL_MAX_REAL;
    }

    //! \name inspectors
    //@{
    public double time() {
        return time_;
    }

    public void setTime(double time) {
        time_ = time;
    }

    public Array values() {
        return values_;
    }

    public final Lattice method() {
        return method_;
    }
    //@}

    /*! \name High-level interface

        Users of discretized assets should use these methods in
        order to initialize, evolve and take the present value of
        the assets.  They call the corresponding methods in the
        Lattice interface, to which we refer for
        documentation.

        @{
    */
    public void initialize(final Lattice method, double t) {
        method_ = method;
        method_.initialize(this, t);
    }

    public void rollback(double to) {
        method_.rollback(this, to);
    }

    public void partialRollback(double to) {
        method_.partialRollback(this, to);
    }

    public double presentValue() {
        return method_.presentValue(this);
    }
    //@}

    /*! \name Low-level interface

        These methods (that developers should override when
        deriving from DiscretizedAsset) are to be used by
        numerical methods and not directly by users, with the
        exception of adjustValues(), preAdjustValues() and
        postAdjustValues() that can be used together with
        partialRollback().

        @{
    */

    /*! This method should initialize the asset values to an Array
        of the given size and with values depending on the
        particular asset.
    */
    public abstract void reset(int size);

    /*! This method will be invoked after rollback and before any
        other asset (i.e., an option on this one) has any chance to
        look at the values. For instance, payments happening at times
        already spanned by the rollback will be added here.

        This method is not virtual; derived classes must override
        the protected preAdjustValuesImpl() method instead.
    */
    public void preAdjustValues() {
        if (!close_enough(time(), latestPreAdjustment_)) {
            preAdjustValuesImpl();
            latestPreAdjustment_ = time();
        }
    }

    /*! This method will be invoked after rollback and after any
       other asset had their chance to look at the values. For
       instance, payments happening at the present time (and therefore
       not included in an option to be exercised at this time) will be
       added here.

       This method is not virtual; derived classes must override
       the protected postAdjustValuesImpl() method instead.
   */
    public void postAdjustValues() {
        if (!close_enough(time(), latestPostAdjustment_)) {
            postAdjustValuesImpl();
            latestPostAdjustment_ = time();
        }
    }

    /*! This method performs both pre- and post-adjustment */
    void adjustValues() {
        preAdjustValues();
        postAdjustValues();
    }

    /*! This method returns the times at which the numerical
        method should stop while rolling back the asset. Typical
        examples include payment times, exercise times and such.

        \note The returned values are not guaranteed to be sorted.
    */
    public abstract List<Double> mandatoryTimes();

    /*! This method checks whether the asset was rolled at the
        given time. */
    protected boolean isOnTime(double t) {
        final TimeGrid grid = method().timeGrid();
        return close_enough(grid.get(grid.index(t)), time());
    }
    /*! This method performs the actual pre-adjustment */
    protected abstract void preAdjustValuesImpl();
    /*! This method performs the actual post-adjustment */
    protected abstract void postAdjustValuesImpl();

}
