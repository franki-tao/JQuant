package jquant.math.interpolations;

import jquant.math.Matrix;
import jquant.math.interpolations.impl.Interpolation2DImpl;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;


//! base class for 2-D interpolations.
/*! Classes derived from this class will provide interpolated
    values from two sequences of length \f$ N \f$ and \f$ M \f$,
    representing the discretized values of the \f$ x \f$ and \f$ y
    \f$ variables, and a \f$ N \times M \f$ matrix representing
    the tabulated function values.

    \warning See the Interpolation class for information about the
             required lifetime of the underlying data.
*/
public class Interpolation2D extends Extrapolator {
    protected Interpolation2DImpl impl_;

    //allowExtrapolation = false
    public double value(double x, double y, boolean allowExtrapolation) {
        checkRange(x, y, allowExtrapolation);
        return impl_.value(x, y);
    }

    public double xMin() {
        return impl_.xMin();
    }

    public double xMax() {
        return impl_.xMax();
    }

    public List<Double> xValues() {
        return impl_.xValues();
    }

    public int locateX(double x) {
        return impl_.locateX(x);
    }

    public double yMin() {
        return impl_.yMin();
    }

    public double yMax() {
        return impl_.yMax();
    }

    public List<Double> yValues() {
        return impl_.yValues();
    }

    public int locateY(double y) {
        return impl_.locateY(y);
    }

    public final Matrix zData() {
        return impl_.zData();
    }

    public boolean isInRange(double x, double y) {
        return impl_.isInRange(x, y);
    }

    public void update() {
        impl_.calculate();
    }

    protected void checkRange(double x, double y, boolean extrapolate) {
        QL_REQUIRE(extrapolate || allowsExtrapolation() || impl_.isInRange(x, y),
                "interpolation range is ["
                        + impl_.xMin() + ", " + impl_.xMax()
                        + "] x ["
                        + impl_.yMin() + ", " + impl_.yMax()
                        + "]: extrapolation at ("
                        + x + ", " + y + ") not allowed");
    }
}
