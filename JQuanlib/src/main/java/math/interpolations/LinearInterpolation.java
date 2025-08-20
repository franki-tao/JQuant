package math.interpolations;

import math.Interpolation;

public class LinearInterpolation extends Interpolation {
    public LinearInterpolation(double[] x, double[] y) {
        super.impl_ = new LinearInterpolationImpl(x, y);
        super.impl_.update();
    }
}
