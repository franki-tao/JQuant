package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.LinearInterpolationImpl;

public class LinearInterpolation extends Interpolation {
    public LinearInterpolation(double[] x, double[] y) {
        super.impl_ = new LinearInterpolationImpl(x, y);
        super.impl_.update();
    }
}
