package jquant.math.interpolations;

import jquant.math.interpolations.impl.BicubicSplineImpl;

//! bicubic-spline interpolation between discrete points
/*! \ingroup interpolations
    \todo revise end conditions
    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class BicubicSpline extends Interpolation2D {
    private BicubicSplineImpl temp;
    public BicubicSpline(double[] x, double[] y, double[][] z) {
        temp = new BicubicSplineImpl(x, y, z);
        impl_ = temp;
    }

    public double derivativeX(double x, double y) {
        return temp.derivativeX(x, y);
    }
    public double derivativeY(double x, double y) {
        return temp.derivativeY(x, y);
    }
    public double secondDerivativeX(double x, double y) {
        return temp.secondDerivativeX(x, y);
    }
    public double secondDerivativeY(double x, double y) {
        return temp.secondDerivativeY(x, y);
    }

    public double derivativeXY(double x, double y) {
        return temp.derivativeXY(x, y);
    }
}
