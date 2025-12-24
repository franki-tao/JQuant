package jquant;

import jquant.math.Array;

//! %Lattice (tree, finite-differences) base class
public abstract class Lattice {
    protected TimeGrid t_;
    public Lattice(TimeGrid t){
        t_ = t;
    }
    //!\name Inspectors
    //{
    public final TimeGrid timeGrid(){
        return t_;
    }
    //@}

    /*! \name Numerical method interface

        These methods are to be used by discretized assets and
        must be overridden by developers implementing numerical
        methods. Users are advised to use the corresponding
        methods of DiscretizedAsset instead.

        @{
    */

    //! initialize an asset at the given time.
    public abstract void initialize(DiscretizedAsset d, double time);

    /*! Roll back an asset until the given time, performing any
        needed adjustment.
    */
    public abstract void rollback(DiscretizedAsset d, double to);

    /*! Roll back an asset until the given time, but do not perform
        the final adjustment.

        \warning In version 0.3.7 and earlier, this method was
                 called rollAlmostBack method and performed
                 pre-adjustment. This is no longer true; when
                 migrating your code, you'll have to replace calls
                 such as:
                 \code
                 method->rollAlmostBack(asset,t);
                 \endcode
                 with the two statements:
                 \code
                 method->partialRollback(asset,t);
                 asset->preAdjustValues();
                 \endcode
    */
    public abstract void partialRollback(DiscretizedAsset d, double to);

    //! computes the present value of an asset.
    public abstract double presentValue(DiscretizedAsset d);

    //@}

    // this is a smell, but we need it. We'll rethink it later.
    public abstract Array grid(double t);
}
