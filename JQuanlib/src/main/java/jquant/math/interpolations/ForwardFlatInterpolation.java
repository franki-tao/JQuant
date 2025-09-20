package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.ForwardFlatInterpolationImpl;


//! Forward-flat interpolation between discrete points
/*! \ingroup interpolations
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class ForwardFlatInterpolation extends Interpolation {
    public ForwardFlatInterpolation(double[] x, double[] y) {
        impl_ = new ForwardFlatInterpolationImpl(x, y);
        impl_.update();
    }
}
