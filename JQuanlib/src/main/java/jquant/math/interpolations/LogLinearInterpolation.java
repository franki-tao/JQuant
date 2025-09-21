package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.LogInterpolationImpl;

//! %log-linear interpolation between discrete points
/*! \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class LogLinearInterpolation extends Interpolation {
    /*! \pre the \f$ x \f$ values must be sorted. */
    public LogLinearInterpolation(double[] x, double[] y) {
        impl_ = new LogInterpolationImpl(x, y, new Linear());
        impl_.update();
    }
}
