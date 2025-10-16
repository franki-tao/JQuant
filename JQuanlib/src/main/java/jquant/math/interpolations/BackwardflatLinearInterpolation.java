package jquant.math.interpolations;

import jquant.math.interpolations.impl.BackwardflatLinearInterpolationImpl;

/*! \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class BackwardflatLinearInterpolation extends Interpolation2D {
    public BackwardflatLinearInterpolation(double[] x, double[] y, double[][] z) {
        impl_ = new BackwardflatLinearInterpolationImpl(x,y,z);
    }
}
