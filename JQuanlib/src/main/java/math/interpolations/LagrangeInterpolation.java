package math.interpolations;

import math.Array;
import math.Interpolation;
import math.interpolations.impl.LagrangeInterpolationImpl;

/*! References: J-P. Berrut and L.N. Trefethen,
     Barycentric Lagrange interpolation,
     SIAM Review, 46(3):501–517, 2004.
    https://people.maths.ox.ac.uk/trefethen/barycentric.pdf
*/
public class LagrangeInterpolation extends Interpolation {
    private double[] xx;
    private double[] yy;
    /*! \ingroup interpolations
        \warning See the Interpolation class for information about the
                 required lifetime of the underlying data.
    */
    public LagrangeInterpolation(double[] x, double[] y) {
        xx = x;
        yy = y;
        impl_ = new LagrangeInterpolationImpl(x, y);
        impl_.update();
    }

    public double value(Array y, double x) {
        return new LagrangeInterpolationImpl(xx, yy).value(y, x);
    }
}
