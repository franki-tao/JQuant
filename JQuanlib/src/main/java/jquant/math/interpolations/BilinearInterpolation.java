package jquant.math.interpolations;

import jquant.math.interpolations.impl.BilinearInterpolationImpl;

//! %bilinear interpolation between discrete points
/*! \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class BilinearInterpolation extends Interpolation2D {
    /*! \pre the \f$ x \f$ and \f$ y \f$ values must be sorted. */
    public BilinearInterpolation(double[] x, double[] y, double[][] z) {
        impl_ = new BilinearInterpolationImpl(x, y, z);
    }
}
