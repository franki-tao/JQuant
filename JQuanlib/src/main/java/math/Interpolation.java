package math;

import math.interpolations.Extrapolator;

import static math.CommonUtil.QL_REQUIRE;


public abstract class Interpolation extends Extrapolator {
    protected Impl impl_;

    public boolean empty() {
        return impl_ == null;
    }

    /**
     * @param x                  x
     * @param allowExtrapolation 默认false
     * @return double
     */
    public double value(double x, boolean allowExtrapolation) {
        checkRange(x, allowExtrapolation);
        return impl_.value(x);
    }

    // allowExtrapolation 默认false
    public double derivative(double x, boolean allowExtrapolation) {
        checkRange(x, allowExtrapolation);
        return impl_.derivative(x);
    }

    // allowExtrapolation 默认false
    public double secondDerivative(double x, boolean allowExtrapolation) {
        checkRange(x, allowExtrapolation);
        return impl_.secondDerivative(x);
    }

    public double xMin() {
        return impl_.xMin();
    }

    public double xMax() {
        return impl_.xMax();
    }

    public boolean isInRange(double x) {
        return impl_.isInRange(x);
    }

    public void update() {
        impl_.update();
    }

    protected void checkRange(double x, boolean extrapolate) {
        QL_REQUIRE(extrapolate || allowsExtrapolation() ||
                        impl_.isInRange(x),
                "interpolation range is [" +
                        impl_.xMin() + ", " + impl_.xMax()
                        + "]: extrapolation at " + x + " not allowed");
    }

}
