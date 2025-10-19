package jquant.math.interpolations;


import jquant.math.Function;
import jquant.math.interpolations.impl.KernelInterpolation2DImpl;

/*! Implementation of the 2D kernel interpolation approach, which
    can be found in "Foreign Exchange Risk" by Hakala, Wystup page
    256.

    The kernel in the implementation is kept general, although a
    Gaussian is considered in the cited text.

    \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class KernelInterpolation2D extends Interpolation2D {
    /*! \pre the \f$ x \f$ values must be sorted.
        \pre kernel needs a Real operator()(Real x) implementation
    */
    public KernelInterpolation2D(double[] x, double[] y, double[][] z, Function kernel) {
        impl_ = new KernelInterpolation2DImpl(x, y, z, kernel);
        update();
    }
}
